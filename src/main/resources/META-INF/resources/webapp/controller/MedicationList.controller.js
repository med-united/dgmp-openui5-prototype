sap.ui.define([
    "epa/controller/BaseController",
    "sap/m/MessageToast",
    "sap/m/MessageBox",
    "epa/model/formatter",
    "sap/ui/core/Fragment"
], function (BaseController, MessageToast, MessageBox, formatter, Fragment) {
    "use strict";

    return BaseController.extend("epa.controller.MedicationList", {
        formatter: formatter,

        onInit: function () {
            var oEventBus = sap.ui.getCore().getEventBus();
            oEventBus.subscribe("epa", "refreshData", this._onRefreshData, this);

            this._attachKvnrListener(function (sKvnr) {
                this._loadMedicationList(sKvnr);
            });
        },

        _onRefreshData: function () {
            var oUIModel = this.getView().getModel("ui");
            if (oUIModel) {
                var sKvnr = oUIModel.getProperty("/currentKVNR");
                if (sKvnr) {
                    this._loadMedicationList(sKvnr);
                }
            }
        },

        /**
         * Fetch MedicationDispense bundle from FHIR with _include and _revinclude.
         * - MedicationDispense (mode=match) → primary display entries
         * - MedicationRequest (mode=include via authorizingPrescription) → linked eMP entry details
         * - Medication (mode=include) → medication name/PZN/ATC
         * - MedicationStatement (mode=include via derivedFrom, from _revinclude) → for link/unlink operations
         *
         * @param {string} sKvnr
         * @returns {Promise}
         */
        _loadMedicationList: function (sKvnr) {
            var that = this;
            var oUIModel = this.getView().getModel("ui");
            this.getView().setBusy(true);

            return fetch("/fhir/MedicationDispense/_search", {
                method: "POST",
                headers: { "Content-Type": "application/x-www-form-urlencoded" },
                body: "patient=http%3A%2F%2Ffhir.de%2Fsid%2Fgkv%2Fkvid-10%7C" + encodeURIComponent(sKvnr)
                    + "&_include=MedicationDispense%3Amedication-request"
                    + "&_revinclude=MedicationStatement%3Aderived-from"
            })
                .then(function (response) {
                    if (!response.ok) throw new Error("List not found");
                    return response.json();
                })
                .then(function (oBundle) {
                    var aEntries = (oBundle && oBundle.entry) ? oBundle.entry : [];

                    // Build lookup maps for included resources
                    var oMedMap = {};       // Medication.id → resource
                    var oReqMap = {};       // MedicationRequest.id → resource
                    var oStmtByDispenseId = {}; // MedicationDispense.id → MedicationStatement.id

                    aEntries.forEach(function (oE) {
                        var oRes = oE.resource;
                        if (!oRes) return;
                        if (oRes.resourceType === "Medication") {
                            oMedMap[oRes.id] = oRes;
                        } else if (oRes.resourceType === "MedicationRequest") {
                            oReqMap[oRes.id] = oRes;
                        } else if (oRes.resourceType === "MedicationStatement") {
                            // Map statement to its dispense via derivedFrom reference
                            if (oRes.derivedFrom && oRes.derivedFrom.length > 0) {
                                var sDispRef = oRes.derivedFrom[0].reference || "";
                                var sDispId = sDispRef.replace("MedicationDispense/", "");
                                if (sDispId) oStmtByDispenseId[sDispId] = oRes.id;
                            }
                        }
                    });

                    // Store the MedicationStatement map in ui model for link/unlink handlers
                    oUIModel.setProperty("/medicationStatementMap", oStmtByDispenseId);

                    // Derive flat display array from MedicationDispense match entries
                    var aDerived = [];
                    aEntries.forEach(function (oE) {
                        if (!oE.search || oE.search.mode !== "match") return;
                        var oDisp = oE.resource;
                        if (!oDisp || oDisp.resourceType !== "MedicationDispense") return;

                        // Resolve dispense Medication
                        var oDispMed = null;
                        if (oDisp.medicationReference && oDisp.medicationReference.reference) {
                            var sMedId = oDisp.medicationReference.reference.replace("Medication/", "");
                            oDispMed = oMedMap[sMedId] || null;
                        }

                        // Resolve linked MedicationRequest (via authorizingPrescription)
                        var oLinkedReq = null;
                        var sReqRef = "";
                        if (oDisp.authorizingPrescription && oDisp.authorizingPrescription.length > 0) {
                            sReqRef = oDisp.authorizingPrescription[0].reference || "";
                            var sReqId = sReqRef.replace("MedicationRequest/", "");
                            oLinkedReq = oReqMap[sReqId] || null;
                        }

                        // Resolve prescription Medication (for fallback display)
                        var oReqMed = null;
                        if (oLinkedReq && oLinkedReq.medicationReference && oLinkedReq.medicationReference.reference) {
                            var sReqMedId = oLinkedReq.medicationReference.reference.replace("Medication/", "");
                            oReqMed = oMedMap[sReqMedId] || null;
                        }

                        // Decision 7: dispense Medication takes precedence
                        var oEffectiveMed = oDispMed || oReqMed;
                        var sMedName = oEffectiveMed ? (oEffectiveMed.code && oEffectiveMed.code.text) || "" : "";
                        var sPzn = that._getCoding(oEffectiveMed, "http://fhir.de/CodeSystem/ifa/pzn");
                        var sAtc = that._getCoding(oEffectiveMed, "http://www.whocc.no/atc");

                        // Decision 7: dosage from dispense first, fall back to request
                        var sDosage = "";
                        if (oDisp.dosageInstruction && oDisp.dosageInstruction[0] && oDisp.dosageInstruction[0].text) {
                            sDosage = oDisp.dosageInstruction[0].text;
                        } else if (oLinkedReq && oLinkedReq.dosageInstruction && oLinkedReq.dosageInstruction[0]) {
                            sDosage = oLinkedReq.dosageInstruction[0].text || "";
                        }

                        // medicationPlanIdentifier from linked MedicationRequest
                        var sEmpId = oLinkedReq
                            ? that._getIdentifierValue(oLinkedReq, "https://gematik.de/fhir/sid/emp-identifier")
                            : "";

                        // MedicationStatement id for link/unlink (stored in map)
                        var sStatementId = oStmtByDispenseId[oDisp.id] || null;

                        aDerived.push({
                            id: oDisp.id,
                            entryType: "dispense",
                            medicationName: sMedName,
                            pzn: sPzn,
                            atcCode: sAtc,
                            dosageText: sDosage,
                            dispensedDate: oDisp.whenHandedOver || "",
                            authoredDate: oLinkedReq ? (oLinkedReq.authoredOn || "") : "",
                            medicationPlanIdentifier: sEmpId,
                            wasSubstituted: !!(oDisp.substitution && oDisp.substitution.wasSubstituted),
                            linkedToPlanId: sReqRef ? sReqRef.replace("MedicationRequest/", "") : null,
                            medicationStatementId: sStatementId,
                            // Retain references for reconciliation
                            authorizingPrescriptionRef: sReqRef
                        });
                    });

                    oUIModel.setProperty("/medicationList", { entries: aDerived });
                    oUIModel.setProperty("/eMLVisible", true);
                    return aDerived;
                })
                .catch(function (error) {
                    console.error("Failed to load medication list:", error);
                    oUIModel.setProperty("/medicationList", { entries: [] });
                    oUIModel.setProperty("/eMLVisible", true);
                    return [];
                })
                .finally(function () {
                    that.getView().setBusy(false);
                });
        },

        /**
         * Extracts a code value from a Medication resource by coding system URL.
         */
        _getCoding: function (oMed, sSystem) {
            if (!oMed || !oMed.code || !oMed.code.coding) return "";
            var oC = oMed.code.coding.find(function (c) { return c.system === sSystem; });
            return oC ? (oC.code || "") : "";
        },

        /**
         * Extracts an identifier value by system URL from a FHIR resource.
         */
        _getIdentifierValue: function (oResource, sSystem) {
            if (!oResource || !oResource.identifier) return "";
            var oId = oResource.identifier.find(function (i) { return i.system === sSystem; });
            return oId ? (oId.value || "") : "";
        },

        onMedicationRowSelect: function (oEvent) {
            var oContext;

            var iRowIndex = oEvent.getParameter("rowIndex");
            if (iRowIndex !== undefined && iRowIndex > -1) {
                oContext = oEvent.getSource().getContextByIndex(iRowIndex);
            }

            if (!oContext) return;

            var oMed = oContext.getObject();
            if (!oMed) return;

            MessageBox.information(
                "Medication Details:\n\n" +
                "Type: " + (oMed.entryType || "n/a") + "\n" +
                "Medication: " + oMed.medicationName + "\n" +
                "PZN: " + (oMed.pzn || "n/a") + "\n" +
                "ATC: " + (oMed.atcCode || "n/a") + "\n" +
                "Dosage: " + (oMed.dosageText || "n/a") + "\n" +
                "Dispensed: " + this._formatDateTime(oMed.dispensedDate) + "\n" +
                (oMed.wasSubstituted ? "Substituted: Yes\n" : "")
            );
        },

        _formatDateTime: function (sDate) {
            if (!sDate) return "N/A";
            return new Date(sDate).toLocaleString();
        },

        onLinkPress: function (oEvent) {
            var oContext = oEvent.getSource().getBindingContext("ui");
            var oEntry = oContext.getObject();
            var that = this;
            var oView = this.getView();

            // If already linked (has a MedicationStatement), offer to unlink
            if (oEntry.medicationStatementId) {
                sap.ui.require(["sap/m/ActionSheet", "sap/m/Button"], function (ActionSheet, Button) {
                    var oActionSheet = new ActionSheet({
                        title: "Medication Link",
                        showCancelButton: true,
                        buttons: [
                            new Button({
                                text: "Unlink from Plan",
                                icon: "sap-icon://broken-link",
                                type: "Reject",
                                press: function () { that.onUnlinkEntry(oEntry); }
                            })
                        ]
                    });
                    oView.addDependent(oActionSheet);
                    oActionSheet.openBy(oEvent.getSource());
                });
                return;
            }

            // If not linked, show eMP candidates
            var oUIModel = oView.getModel("ui");
            var aPlanEntries = (oUIModel.getProperty("/medicationPlan") || { entries: [] }).entries || [];

            if (aPlanEntries.length === 0) {
                MessageToast.show("No active medication plan entries to link to.");
                return;
            }

            sap.ui.require(["sap/m/ActionSheet", "sap/m/Button"], function (ActionSheet, Button) {
                var aButtons = [];
                aPlanEntries.forEach(function (oEmpEntry) {
                    if (oEmpEntry.status !== "active") return;
                    aButtons.push(new Button({
                        text: "Plan: " + oEmpEntry.medicationName + " (" + (oEmpEntry.dosageText || "No dosage") + ")",
                        icon: "sap-icon://chain-link",
                        press: function () { that.onLinkToPlanEntry(oEntry, oEmpEntry); }
                    }));
                });

                if (aButtons.length === 0) {
                    MessageToast.show("No eligible active plan entries found.");
                    return;
                }

                var oActionSheet = new ActionSheet({
                    title: "Link to Plan Entry",
                    showCancelButton: true,
                    buttons: aButtons
                });
                oView.addDependent(oActionSheet);
                oActionSheet.openBy(oEvent.getSource());
            });
        },

        /**
         * Link a MedicationStatement to a MedicationRequest (eMP entry) via $link-emp.
         * Task 5.9: invoke POST /fhir/MedicationStatement/:id/$link-emp
         */
        onLinkToPlanEntry: function (oEmlEntry, oEmpEntry) {
            var that = this;
            var oUIModel = this.getView().getModel("ui");
            var sKvnr = oUIModel.getProperty("/currentKVNR");
            var sStatementId = oEmlEntry.medicationStatementId;

            if (!sStatementId) {
                MessageToast.show("No MedicationStatement found for this dispense entry.");
                return;
            }

            this.getView().setBusy(true);
            fetch("/fhir/MedicationStatement/" + sStatementId + "/$link-emp", {
                method: "POST",
                headers: {
                    "Content-Type": "application/json",
                    "X-Requesting-Organization": "Hospital-A"
                },
                body: JSON.stringify({
                    resourceType: "Parameters",
                    parameter: [{ name: "emp-entry-id", valueString: oEmpEntry.id }]
                })
            })
                .then(function (response) {
                    if (!response.ok) return response.json().then(function (oErr) {
                        var sCode = oErr && oErr.issue && oErr.issue[0] ? oErr.issue[0].code : "unknown";
                        throw new Error(sCode);
                    });
                    MessageToast.show("Linked to " + oEmpEntry.medicationName);
                    return that._loadMedicationList(sKvnr);
                })
                .then(function () {
                    sap.ui.getCore().getEventBus().publish("epa", "refreshData");
                })
                .catch(function (error) {
                    console.error("Link failed", error);
                    MessageToast.show("Failed to create link: " + error.message);
                })
                .finally(function () { that.getView().setBusy(false); });
        },

        /**
         * Unlink a MedicationStatement from its eMP entry via $unlink-emp.
         * Task 5.9: invoke POST /fhir/MedicationStatement/:id/$unlink-emp
         */
        onUnlinkEntry: function (oEmlEntry) {
            var that = this;
            var oUIModel = this.getView().getModel("ui");
            var sKvnr = oUIModel.getProperty("/currentKVNR");
            var sStatementId = oEmlEntry.medicationStatementId;

            if (!sStatementId) {
                MessageToast.show("No MedicationStatement found for this dispense entry.");
                return;
            }

            this.getView().setBusy(true);
            fetch("/fhir/MedicationStatement/" + sStatementId + "/$unlink-emp", {
                method: "POST",
                headers: {
                    "Content-Type": "application/json",
                    "X-Requesting-Organization": "Hospital-A"
                },
                body: JSON.stringify({ resourceType: "Parameters", parameter: [] })
            })
                .then(function (response) {
                    if (!response.ok) return response.json().then(function (oErr) {
                        var sCode = oErr && oErr.issue && oErr.issue[0] ? oErr.issue[0].code : "unknown";
                        throw new Error(sCode);
                    });
                    MessageToast.show("Unlinked");
                    return that._loadMedicationList(sKvnr);
                })
                .then(function () {
                    sap.ui.getCore().getEventBus().publish("epa", "refreshData");
                })
                .catch(function (error) {
                    console.error("Unlink failed", error);
                    MessageToast.show("Failed to remove link: " + error.message);
                })
                .finally(function () { that.getView().setBusy(false); });
        }
    });
});
