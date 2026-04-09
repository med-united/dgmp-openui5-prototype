sap.ui.define([
    "epa/controller/BaseController",
    "sap/m/MessageToast",
    "sap/m/MessageBox",
    "epa/model/formatter",
    "epa/model/AMTSSimulator",
    "sap/ui/core/Fragment",
    "sap/ui/model/json/JSONModel"
], function (BaseController, MessageToast, MessageBox, formatter, AMTSSimulator, Fragment, JSONModel) {
    "use strict";

    return BaseController.extend("epa.controller.MedicationPlan", {
        formatter: formatter,

        onInit: function () {
            var oEventBus = sap.ui.getCore().getEventBus();
            oEventBus.subscribe("epa", "refreshData", this._onRefreshData, this);

            this._attachKvnrListener(function (sKvnr) {
                this._loadMedicationPlan(sKvnr);
            });
        },

        _onRefreshData: function () {
            var oUIModel = this.getView().getModel("ui");
            if (oUIModel) {
                var sKvnr = oUIModel.getProperty("/currentKVNR");
                if (sKvnr) {
                    this._loadMedicationPlan(sKvnr);
                }
            }
        },

        _setBusy: function (bBusy) {
            this.getView().setBusy(bBusy);
        },

        /**
         * Fetch MedicationRequest bundle from FHIR, resolve medicationReference → Medication,
         * and store a flat derived array in the "ui" model at /medicationPlan/entries.
         *
         * @param {string} sKvnr
         * @returns {Promise}
         */
        _loadMedicationPlan: function (sKvnr) {
            var that = this;
            var oUIModel = this.getView().getModel("ui");
            this._setBusy(true);

            return fetch("/fhir/MedicationRequest/_search", {
                method: "POST",
                headers: { "Content-Type": "application/x-www-form-urlencoded" },
                body: "patient=http%3A%2F%2Ffhir.de%2Fsid%2Fgkv%2Fkvid-10%7C" + encodeURIComponent(sKvnr)
                    + "&_include=MedicationRequest%3Amedication"
            })
                .then(function (response) {
                    if (!response.ok) throw new Error("Plan not found");
                    return response.json();
                })
                .then(function (oBundle) {
                    var aEntries = (oBundle && oBundle.entry) ? oBundle.entry : [];

                    // Build a Medication lookup map: Medication/id → resource
                    var oMedMap = {};
                    aEntries.forEach(function (oE) {
                        var oRes = oE.resource;
                        if (oRes && oRes.resourceType === "Medication") {
                            oMedMap[oRes.id] = oRes;
                        }
                    });

                    // Derive flat display array from MedicationRequest match entries
                    var aDerived = [];
                    aEntries.forEach(function (oE) {
                        if (!oE.search || oE.search.mode !== "match") return;
                        var oReq = oE.resource;
                        if (!oReq || oReq.resourceType !== "MedicationRequest") return;

                        // Resolve Medication via medicationReference
                        var oMed = null;
                        if (oReq.medicationReference && oReq.medicationReference.reference) {
                            var sMedId = oReq.medicationReference.reference.replace("Medication/", "");
                            oMed = oMedMap[sMedId] || null;
                        }

                        var sMedName = oMed ? (oMed.code && oMed.code.text) || "" : "";
                        var sPzn = that._getCoding(oMed, "http://fhir.de/CodeSystem/ifa/pzn");
                        var sAtc = that._getCoding(oMed, "http://www.whocc.no/atc");
                        var sEmpId = that._getIdentifierValue(oReq, "https://gematik.de/fhir/sid/emp-identifier");
                        var sDosage = (oReq.dosageInstruction && oReq.dosageInstruction[0])
                            ? (oReq.dosageInstruction[0].text || "") : "";

                        aDerived.push({
                            id: oReq.id,
                            medicationName: sMedName,
                            pzn: sPzn,
                            atcCode: sAtc,
                            activeIngredient: null, // KBV_PR_ERP_Medication_PZN prohibits ingredient (max=0)
                            status: oReq.status || "unknown",
                            dosageText: sDosage,
                            dosageStructured: sDosage,
                            authoredOn: oReq.authoredOn || "",
                            medicationPlanIdentifier: sEmpId,
                            // Retain raw ref for link/unlink support
                            medicationReference: oReq.medicationReference,
                            // Chronicle ID comes from EMPChronologyProvenance server-side
                            chronologyId: null
                        });
                    });

                    oUIModel.setProperty("/medicationPlan", { entries: aDerived, chronologyId: null });
                    return aDerived;
                })
                .catch(function (error) {
                    console.error("Failed to load medication plan:", error);
                    oUIModel.setProperty("/medicationPlan", { entries: [] });
                    return [];
                })
                .finally(function () {
                    that._setBusy(false);
                });
        },

        /**
         * Extracts a code value from a Medication resource by coding system URL.
         * @param {object|null} oMed - FHIR Medication resource
         * @param {string} sSystem
         * @returns {string}
         */
        _getCoding: function (oMed, sSystem) {
            if (!oMed || !oMed.code || !oMed.code.coding) return "";
            var oC = oMed.code.coding.find(function (c) { return c.system === sSystem; });
            return oC ? (oC.code || "") : "";
        },

        /**
         * Extracts an identifier value by system URL from a FHIR resource.
         * @param {object} oResource
         * @param {string} sSystem
         * @returns {string}
         */
        _getIdentifierValue: function (oResource, sSystem) {
            if (!oResource || !oResource.identifier) return "";
            var oId = oResource.identifier.find(function (i) { return i.system === sSystem; });
            return oId ? (oId.value || "") : "";
        },

        _onDialogClose: function () {
            var oUIModel = this.getView().getModel("ui");
            var sKvnr = oUIModel.getProperty("/currentKVNR");
            if (sKvnr) {
                this._loadMedicationPlan(sKvnr);
            }
        },

        onShowLinkedHistory: function (oEvent) {
            var oSource = oEvent.getSource();
            var oContext = oSource.getBindingContext("ui");
            var oEmpEntry = oContext.getObject();
            var oUIModel = this.getView().getModel("ui");

            // Get all eML entries from ui model (populated by MedicationList controller)
            var aMedList = oUIModel.getProperty("/medicationList");
            var aEmlEntries = (aMedList && aMedList.entries) ? aMedList.entries : [];

            // Filter to linked dispenses (by medicationPlanIdentifier match)
            var aLinked = aEmlEntries.filter(function (entry) {
                return entry.medicationPlanIdentifier
                    && entry.medicationPlanIdentifier === oEmpEntry.medicationPlanIdentifier;
            });

            if (aLinked.length === 0) {
                MessageToast.show("No linked history entries found");
                return;
            }

            var sMessage = "Therapy History (" + aLinked.length + " entries):\n\n";
            aLinked.forEach(function (entry) {
                var sDate = entry.dispensedDate ? new Date(entry.dispensedDate).toLocaleDateString() : "n/a";
                sMessage += "\u2022 " + sDate + " \u2014 " + entry.medicationName + " (PZN: " + (entry.pzn || "n/a") + ")\n";
            });

            MessageBox.information(sMessage, {
                title: "Medication History: " + oEmpEntry.medicationName,
                styleClass: "sapUiResponsivePadding--header sapUiResponsivePadding--content sapUiResponsivePadding--footer"
            });
        },

        onPauseMedication: function (oEvent) {
            var oItem = oEvent.getSource().getParent().getParent();
            var oContext = oItem.getBindingContext("ui");
            var oEntry = oContext.getObject();
            var oUIModel = this.getView().getModel("ui");
            var sKvnr = oUIModel.getProperty("/currentKVNR");
            var that = this;

            // Toggle: active → on-hold (FHIR R4 "paused"), on-hold → active
            var sNewStatus = oEntry.status === "active" ? "on-hold" : "active";
            var sAction = sNewStatus === "on-hold" ? "Pause" : "Reactivate";

            // Optimistic update
            var sOldStatus = oEntry.status;
            oContext.getModel().setProperty(oContext.getPath() + "/status", sNewStatus);
            this._setBusy(true);

            this._submitStatusBatch(sKvnr, oEntry.id, sNewStatus)
                .then(function () {
                    MessageToast.show(sAction + " successful for " + oEntry.medicationName);
                    return that._loadMedicationPlan(sKvnr);
                })
                .catch(function (error) {
                    console.error("Status update failed", error);
                    MessageBox.error("Failed to " + sAction.toLowerCase() + " medication: " + error.message);
                    // Revert optimistic update
                    oContext.getModel().setProperty(oContext.getPath() + "/status", sOldStatus);
                })
                .finally(function () {
                    that._setBusy(false);
                });
        },

        /**
         * Submit a status-change batch to POST /fhir (FHIR batch Bundle).
         */
        _submitStatusBatch: function (sKvnr, sEntryId, sNewStatus) {
            var oBatch = {
                resourceType: "Bundle",
                type: "batch",
                entry: [{
                    request: { method: "PUT", url: "MedicationRequest/" + sEntryId },
                    resource: {
                        resourceType: "MedicationRequest",
                        id: sEntryId,
                        status: sNewStatus,
                        intent: "order",
                        subject: { identifier: { system: "http://fhir.de/sid/gkv/kvid-10", value: sKvnr } },
                        medicationCodeableConcept: { text: "status-update-only" }
                    }
                }]
            };
            return fetch("/fhir", {
                method: "POST",
                headers: { "Content-Type": "application/json", "X-Requesting-Organization": "Hospital-A" },
                body: JSON.stringify(oBatch)
            }).then(function (response) {
                if (!response.ok) throw new Error("Batch failed: HTTP " + response.status);
                return response.json();
            }).then(function (oResponse) {
                var aEntries = (oResponse && oResponse.entry) ? oResponse.entry : [];
                var oFailed = aEntries.find(function (e) {
                    var sStatus = e.response && e.response.status ? String(e.response.status) : "";
                    return sStatus.startsWith("4") || sStatus.startsWith("5");
                });
                if (oFailed) throw new Error("Batch entry failed: " + (oFailed.response && oFailed.response.status));
            });
        },

        onDeleteMedication: function (oEvent) {
            var oItem = oEvent.getSource().getParent().getParent();
            var oContext = oItem.getBindingContext("ui");
            var oEntry = oContext.getObject();
            var oUIModel = this.getView().getModel("ui");
            var sKvnr = oUIModel.getProperty("/currentKVNR");
            var that = this;

            MessageBox.confirm("Are you sure you want to delete " + oEntry.medicationName + "? This cannot be undone.", {
                icon: MessageBox.Icon.WARNING,
                title: "Delete Medication",
                actions: [MessageBox.Action.DELETE, MessageBox.Action.CANCEL],
                onClose: function (sAction) {
                    if (sAction === MessageBox.Action.DELETE) {
                        that._setBusy(true);
                        var oBatch = {
                            resourceType: "Bundle",
                            type: "batch",
                            entry: [{ request: { method: "DELETE", url: "MedicationRequest/" + oEntry.id } }]
                        };
                        fetch("/fhir", {
                            method: "POST",
                            headers: { "Content-Type": "application/json", "X-Requesting-Organization": "Hospital-A" },
                            body: JSON.stringify(oBatch)
                        })
                            .then(function (response) {
                                if (!response.ok) throw new Error("Failed to delete entry");
                                MessageToast.show("Deleted " + oEntry.medicationName);
                                return that._loadMedicationPlan(sKvnr);
                            })
                            .catch(function (error) {
                                console.error("Delete failed", error);
                                MessageBox.error("Failed to delete medication: " + error.message);
                            })
                            .finally(function () { that._setBusy(false); });
                    }
                }
            });
        },

        onAMTSCheck: function () {
            var oUIModel = this.getView().getModel("ui");
            var oPlan = oUIModel.getProperty("/medicationPlan");

            if (!oPlan || !oPlan.entries || oPlan.entries.length === 0) {
                MessageToast.show("No medications in plan to check.");
                return;
            }

            // Decision 9: pass only active entries; activeIngredient = null (PZN profile prohibits ingredient)
            var aActiveMeds = oPlan.entries.filter(function (m) { return m.status === "active"; });

            if (aActiveMeds.length < 2) {
                MessageToast.show("Need at least 2 active medications to check for interactions.");
                return;
            }

            var aWarnings = AMTSSimulator.checkInteractions(aActiveMeds);

            if (aWarnings.length === 0) {
                MessageBox.success("AMTS Check: No interactions found.", { title: "Safety Check Passed" });
            } else {
                var sMessage = "Found " + aWarnings.length + " potential interaction(s):\n\n";
                aWarnings.forEach(function (w) { sMessage += "\u2022 " + w.title + "\n" + w.description + "\n\n"; });
                MessageBox.warning(sMessage, {
                    title: "Safety Check Warnings",
                    styleClass: "sapUiResponsivePadding--header sapUiResponsivePadding--content sapUiResponsivePadding--footer"
                });
            }
        },

        onAddMedication: function () {
            this._openAddDialog();
        },

        onEditMedication: function (oEvent) {
            var oItem = oEvent.getSource().getParent().getParent();
            var oContext = oItem.getBindingContext("ui");
            var oEntry = oContext.getObject();
            var that = this;

            this._getAddMedicationDialog().then(function (oDialog) {
                if (that._oAddMedicationController) {
                    that._oAddMedicationController.setEditMode(oEntry.id, oEntry);
                }
                oDialog.open();
                oDialog.detachAfterClose(that._onDialogClose, that);
                oDialog.attachAfterClose(that._onDialogClose, that);
            });
        },

        onExportPDF: function () {
            MessageToast.show("Export PDF action triggered");
        }

    });
});
