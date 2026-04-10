sap.ui.define([
    "epa/controller/BaseController",
    "sap/m/MessageToast",
    "sap/ui/table/TreeTable",
    "epa/model/formatter",
    "sap/m/ActionSheet",
    "sap/m/Button"
], function (BaseController, MessageToast, TreeTable, formatter, ActionSheet, Button) {
    "use strict";

    return BaseController.extend("epa.controller.Reconciliation", {
        formatter: formatter,

        formatRowHighlight: function (sStatus) {
            switch (sStatus) {
                case "LINKED": return "Success";
                case "PROPOSAL_MATCH": return "Information";
                case "UNLINKED_UPDATE": return "Warning";
                case "ORPHAN_NEW": return "Error";
                default: return "None";
            }
        },

        onInit: function () {
            var oEventBus = sap.ui.getCore().getEventBus();
            oEventBus.subscribe("epa", "refreshData", this._onRefreshData, this);

            this._attachKvnrListener(function (sKvnr) {
                this._loadReconciliation(sKvnr);
            });
        },

        _onRefreshData: function () {
            this._loadReconciliation();
        },

        _onDialogClose: function () {
            this._loadReconciliation();
            sap.ui.getCore().getEventBus().publish("epa", "refreshData");
        },

        /**
         * Build reconciliation data from FHIR resources.
         *
         * Strategy (Decision 8): fetch MedicationDispense + MedicationRequest bundles,
         * derive plain arrays per the Decision 8 contract table, run the soft-match
         * algorithm client-side, store results in the "ui" model at /reconciliation.
         *
         * @param {string} [sKvnr]
         */
        _loadReconciliation: function (sKvnr) {
            // Sanitize: guard against event-object being passed as sKvnr
            if (sKvnr && typeof sKvnr === "object" && sKvnr.sId) {
                sKvnr = null;
            }

            var oUIModel = this.getView().getModel("ui");
            if (!sKvnr && oUIModel) {
                sKvnr = oUIModel.getProperty("/currentKVNR");
            }

            if (!sKvnr) return;

            var that = this;
            this.getView().setBusy(true);

            // Fetch both bundles in parallel
            var pDisp = fetch("/fhir/MedicationDispense/_search", {
                method: "POST",
                headers: { "Content-Type": "application/x-www-form-urlencoded" },
                body: "patient=http%3A%2F%2Ffhir.de%2Fsid%2Fgkv%2Fkvid-10%7C" + encodeURIComponent(sKvnr)
                    + "&_include=MedicationDispense%3Amedication-request"
            }).then(function (r) { return r.ok ? r.json() : { entry: [] }; });

            var pPlan = fetch("/fhir/MedicationRequest/_search", {
                method: "POST",
                headers: { "Content-Type": "application/x-www-form-urlencoded" },
                body: "patient=http%3A%2F%2Ffhir.de%2Fsid%2Fgkv%2Fkvid-10%7C" + encodeURIComponent(sKvnr)
                    + "&_include=MedicationRequest%3Amedication"
            }).then(function (r) { return r.ok ? r.json() : { entry: [] }; });

            Promise.all([pDisp, pPlan])
                .then(function (aResults) {
                    var oDispBundle = aResults[0];
                    var oPlanBundle = aResults[1];

                    var aDispEntries = (oDispBundle && oDispBundle.entry) ? oDispBundle.entry : [];
                    var aPlanEntries = (oPlanBundle && oPlanBundle.entry) ? oPlanBundle.entry : [];

                    // Build Medication lookup maps
                    var oMedMap = {};
                    aDispEntries.concat(aPlanEntries).forEach(function (oE) {
                        var oRes = oE.resource;
                        if (oRes && oRes.resourceType === "Medication") {
                            oMedMap[oRes.id] = oRes;
                        }
                    });

                    // Build MedicationRequest lookup (from both bundles)
                    var oReqMap = {};
                    aDispEntries.concat(aPlanEntries).forEach(function (oE) {
                        var oRes = oE.resource;
                        if (oRes && oRes.resourceType === "MedicationRequest") {
                            oReqMap[oRes.id] = oRes;
                        }
                    });

                    // --- Decision 8: derive eML side ---
                    var aEmlEntries = [];
                    aDispEntries.forEach(function (oE) {
                        if (!oE.search || oE.search.mode !== "match") return;
                        var oDisp = oE.resource;
                        if (!oDisp || oDisp.resourceType !== "MedicationDispense") return;

                        var oLinkedReq = null;
                        var sReqRef = "";
                        if (oDisp.authorizingPrescription && oDisp.authorizingPrescription.length > 0) {
                            sReqRef = oDisp.authorizingPrescription[0].reference || "";
                            var sReqId = sReqRef.replace("MedicationRequest/", "");
                            oLinkedReq = oReqMap[sReqId] || null;
                        }

                        var oDispMed = that._resolveMed(oDisp, oMedMap);
                        var oReqMed = oLinkedReq ? that._resolveMed(oLinkedReq, oMedMap) : null;
                        var oEffMed = oDispMed || oReqMed;

                        var sDosage = "";
                        if (oDisp.dosageInstruction && oDisp.dosageInstruction[0] && oDisp.dosageInstruction[0].text) {
                            sDosage = oDisp.dosageInstruction[0].text;
                        } else if (oLinkedReq && oLinkedReq.dosageInstruction && oLinkedReq.dosageInstruction[0]) {
                            sDosage = oLinkedReq.dosageInstruction[0].text || "";
                        }

                        aEmlEntries.push({
                            dispenseId: oDisp.id,
                            prescriptionRef: sReqRef,
                            medicationName: oEffMed ? (oEffMed.code && oEffMed.code.text) || "" : "",
                            atcCode: that._getCoding(oEffMed, "http://www.whocc.no/atc"),
                            pzn: that._getCoding(oEffMed, "http://fhir.de/CodeSystem/ifa/pzn"),
                            dosage: sDosage,
                            dispensedDate: oDisp.whenHandedOver || "",
                            authoredDate: oLinkedReq ? (oLinkedReq.authoredOn || "") : "",
                            medicationPlanIdentifier: oLinkedReq
                                ? that._getIdentifierValue(oLinkedReq, "https://gematik.de/fhir/sid/emp-identifier")
                                : "",
                            wasSubstituted: !!(oDisp.substitution && oDisp.substitution.wasSubstituted)
                        });
                    });

                    // --- Decision 8: derive eMP side ---
                    var aEmpEntries = [];
                    aPlanEntries.forEach(function (oE) {
                        if (!oE.search || oE.search.mode !== "match") return;
                        var oReq = oE.resource;
                        if (!oReq || oReq.resourceType !== "MedicationRequest") return;

                        var oMed = that._resolveMed(oReq, oMedMap);
                        aEmpEntries.push({
                            id: oReq.id,
                            medicationName: oMed ? (oMed.code && oMed.code.text) || "" : "",
                            atcCode: that._getCoding(oMed, "http://www.whocc.no/atc"),
                            pzn: that._getCoding(oMed, "http://fhir.de/CodeSystem/ifa/pzn"),
                            medicationPlanIdentifier: that._getIdentifierValue(oReq, "https://gematik.de/fhir/sid/emp-identifier"),
                            status: oReq.status || "unknown"
                        });
                    });

                    // Run soft-match algorithm
                    var aItems = that._runSoftMatch(aEmlEntries, aEmpEntries);

                    oUIModel.setProperty("/reconciliation/emlEntries", aEmlEntries);
                    oUIModel.setProperty("/reconciliation/empEntries", aEmpEntries);
                    oUIModel.setProperty("/reconciliation/items", aItems);

                    that._applyClientFilters();
                })
                .catch(function (error) {
                    console.error("Failed to load reconciliation:", error);
                    oUIModel.setProperty("/reconciliation/items", []);
                })
                .finally(function () {
                    that.getView().setBusy(false);
                });
        },

        /**
         * Resolve medicationReference → Medication resource from lookup map.
         */
        _resolveMed: function (oResource, oMedMap) {
            if (!oResource || !oResource.medicationReference || !oResource.medicationReference.reference) return null;
            var sMedId = oResource.medicationReference.reference.replace("Medication/", "");
            return oMedMap[sMedId] || null;
        },

        _getCoding: function (oMed, sSystem) {
            if (!oMed || !oMed.code || !oMed.code.coding) return "";
            var oC = oMed.code.coding.find(function (c) { return c.system === sSystem; });
            return oC ? (oC.code || "") : "";
        },

        _getIdentifierValue: function (oResource, sSystem) {
            if (!oResource || !oResource.identifier) return "";
            var oId = oResource.identifier.find(function (i) { return i.system === sSystem; });
            return oId ? (oId.value || "") : "";
        },

        /**
         * Client-side soft-match algorithm (unchanged logic, new FHIR-derived input).
         * Matches eML entries to eMP entries by ATC code and PZN.
         *
         * @param {object[]} aEmlEntries - derived from Decision 8 contract
         * @param {object[]} aEmpEntries - derived from Decision 8 contract
         * @returns {object[]} reconciliation items for the "ui" model
         */
        _runSoftMatch: function (aEmlEntries, aEmpEntries) {
            var aItems = [];

            aEmlEntries.forEach(function (oEml) {
                var sStatus = "ORPHAN_NEW";
                var sMatchReason = "";

                // Check if an eMP entry has the same medicationPlanIdentifier (explicit link)
                var oExactMatch = aEmpEntries.find(function (oEmp) {
                    return oEmp.medicationPlanIdentifier && oEml.medicationPlanIdentifier
                        && oEmp.medicationPlanIdentifier === oEml.medicationPlanIdentifier;
                });

                if (oExactMatch) {
                    sStatus = "LINKED";
                    sMatchReason = "Linked by eMP identifier";
                } else {
                    // Soft match: ATC or PZN
                    var oAtcMatch = aEmpEntries.find(function (oEmp) {
                        return oEmp.atcCode && oEml.atcCode && oEmp.atcCode === oEml.atcCode;
                    });
                    var oPznMatch = aEmpEntries.find(function (oEmp) {
                        return oEmp.pzn && oEml.pzn && oEmp.pzn === oEml.pzn;
                    });

                    if (oPznMatch || oAtcMatch) {
                        sStatus = "PROPOSAL_MATCH";
                        sMatchReason = oPznMatch ? "PZN match" : "ATC match";
                    }
                }

                aItems.push(Object.assign({}, oEml, {
                    status: sStatus,
                    matchReason: sMatchReason,
                    markedAsError: false,
                    // Display fields for view
                    medicationName: oEml.medicationName,
                    dispensedDate: oEml.dispensedDate,
                    authoredDate: oEml.authoredDate,
                    pzn: oEml.pzn,
                    atcCode: oEml.atcCode,
                    wasSubstituted: oEml.wasSubstituted
                }));
            });

            return aItems;
        },

        onTimeFilterChange: function (oEvent) {
            var sKey = oEvent.getParameter("selectedItem").getKey();
            this.getView().getModel("ui").setProperty("/filterTimeRange", sKey);
            this._loadReconciliation();
        },

        onShowLinkedToggle: function (oEvent) {
            var bShow = oEvent.getParameter("selected");
            this.getView().getModel("ui").setProperty("/filterShowLinked", bShow);
            this._applyClientFilters();
        },

        _applyClientFilters: function () {
            var oUIModel = this.getView().getModel("ui");
            var bShowLinked = oUIModel.getProperty("/filterShowLinked");
            var aItems = oUIModel.getProperty("/reconciliation/items") || [];

            var oTable = this.getView().byId("reconciliationTree");
            if (oTable) {
                var oBinding = oTable.getBinding("rows");
                var aFilters = [];

                if (!bShowLinked) {
                    aFilters.push(new sap.ui.model.Filter("status", sap.ui.model.FilterOperator.NE, "LINKED"));
                }
                aFilters.push(new sap.ui.model.Filter("markedAsError", sap.ui.model.FilterOperator.NE, true));

                if (oBinding) {
                    oBinding.filter(aFilters);
                }

                var iHiddenLinked = !bShowLinked
                    ? aItems.filter(function (item) { return item.status === "LINKED"; }).length
                    : 0;

                if (iHiddenLinked > 0) {
                    oTable.setNoData("No data (" + iHiddenLinked + " Linked hidden)");
                } else {
                    oTable.setNoData("No data");
                }
            }
        },

        onMarkAsError: function (oEvent) {
            var oContext = oEvent.getSource().getBindingContext("ui");
            oContext.getModel().setProperty(oContext.getPath() + "/markedAsError", true);
            this._applyClientFilters();
            MessageToast.show("Entry marked as error and hidden.");
        },

        onLinkFromTree: function (oEvent) {
            var oSource = oEvent.getSource();
            var oContext = oSource.getBindingContext("ui");
            var oReconcileItem = oContext.getObject();

            var that = this;
            var oView = this.getView();
            var oUIModel = oView.getModel("ui");
            var aEmpEntries = oUIModel.getProperty("/reconciliation/empEntries") || [];

            if (aEmpEntries.length === 0) {
                MessageToast.show("No active medication plan entries to link to.");
                return;
            }

            var aButtons = [];
            aEmpEntries.forEach(function (oEmpEntry) {
                if (oEmpEntry.status !== "active") return;
                aButtons.push(new Button({
                    text: "Plan: " + oEmpEntry.medicationName,
                    icon: "sap-icon://chain-link",
                    press: function () { that._performLink(oReconcileItem, oEmpEntry); }
                }));
            });

            if (aButtons.length === 0) {
                MessageToast.show("No eligible active plan entries found.");
                return;
            }

            var oActionSheet = new ActionSheet({
                title: "Link to Plan",
                showCancelButton: true,
                buttons: aButtons,
                afterClose: function () { oActionSheet.destroy(); }
            });
            oView.addDependent(oActionSheet);
            oActionSheet.openBy(oSource);
        },

        /**
         * Perform link via the link-emp operation on the MedicationStatement.
         * The MedicationStatement ID is stored in the ui/medicationStatementMap.
         */
        _performLink: function (oReconcileItem, oEmpEntry) {
            var that = this;
            var oUIModel = this.getView().getModel("ui");
            var sKvnr = oUIModel.getProperty("/currentKVNR");

            // Look up MedicationStatement ID from the statement map
            var oStmtMap = oUIModel.getProperty("/medicationStatementMap") || {};
            var sStatementId = oStmtMap[oReconcileItem.dispenseId] || null;

            if (!sStatementId) {
                MessageToast.show("No MedicationStatement found for this dispense entry. Cannot link.");
                return;
            }

            this.getView().setBusy(true);
            fetch("/fhir/MedicationStatement/" + sStatementId + "/link-emp", {
                method: "POST",
                headers: { "Content-Type": "application/json", "X-Requesting-Organization": "Hospital-A" },
                body: JSON.stringify({
                    resourceType: "Parameters",
                    parameter: [{ name: "emp-entry-id", valueString: oEmpEntry.id }]
                })
            })
                .then(function (response) {
                    if (!response.ok) return response.json().then(function (oErr) {
                        throw new Error((oErr && oErr.issue && oErr.issue[0] && oErr.issue[0].code) || "LINKING_NOT_SUCCESSFUL");
                    });
                    MessageToast.show("Linked successfully");
                    that._loadReconciliation();
                    sap.ui.getCore().getEventBus().publish("epa", "refreshData");
                })
                .catch(function (error) {
                    console.error("Link failed", error);
                    MessageToast.show("Failed to create link: " + error.message);
                })
                .finally(function () { that.getView().setBusy(false); });
        },

        onUnlinkFromTree: function (oEvent) {
            var oContext = oEvent.getSource().getBindingContext("ui");
            var oReconcileItem = oContext.getObject();
            var that = this;
            var oUIModel = this.getView().getModel("ui");
            var oStmtMap = oUIModel.getProperty("/medicationStatementMap") || {};
            var sStatementId = oStmtMap[oReconcileItem.dispenseId] || null;

            if (!sStatementId) {
                MessageToast.show("No MedicationStatement found for this entry. Cannot unlink.");
                return;
            }

            this.getView().setBusy(true);
            fetch("/fhir/MedicationStatement/" + sStatementId + "/unlink-emp", {
                method: "POST",
                headers: { "Content-Type": "application/json", "X-Requesting-Organization": "Hospital-A" },
                body: JSON.stringify({ resourceType: "Parameters", parameter: [] })
            })
                .then(function (response) {
                    if (!response.ok) return response.json().then(function (oErr) {
                        throw new Error((oErr && oErr.issue && oErr.issue[0] && oErr.issue[0].code) || "UNLINKING_NOT_SUCCESSFUL");
                    });
                    MessageToast.show("Unlinked");
                    that._loadReconciliation();
                    sap.ui.getCore().getEventBus().publish("epa", "refreshData");
                })
                .catch(function (error) {
                    console.error("Unlink failed", error);
                    MessageToast.show("Failed to remove link: " + error.message);
                })
                .finally(function () { that.getView().setBusy(false); });
        },

        onAddToPlanFromTree: function (oEvent) {
            var oContext = oEvent.getSource().getBindingContext("ui");
            var oReconcileItem = oContext.getObject();
            // Map reconciliation item to the pre-fill schema AddMedicationDialog expects
            this._openAddDialog({
                medicationName: oReconcileItem.medicationName,
                pzn: oReconcileItem.pzn,
                atcCode: oReconcileItem.atcCode,
                dosageText: oReconcileItem.dosage
            });
        }

    });
});
