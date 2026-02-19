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
            // Subscribe to event bus for refresh
            var oEventBus = sap.ui.getCore().getEventBus();
            oEventBus.subscribe("epa", "refreshData", this._onRefreshData, this);

            // Attach KVNR change listener via BaseController helper
            this._attachKvnrListener(function (sKvnr) {
                this._loadMedicationPlan(sKvnr);
            });
        },

        _onRefreshData: function () {
            var oModel = this.getView().getModel("app");
            if (oModel) {
                var sKvnr = oModel.getProperty("/currentKVNR");
                if (sKvnr) {
                    this._loadMedicationPlan(sKvnr);
                }
            }
        },

        _setBusy: function (bBusy) {
            this.getView().setBusy(bBusy);
        },

        _loadMedicationPlan: function (sKvnr) {
            var that = this;
            var oModel = this.getView().getModel("app");

            // Also load Medication List for History Counts
            this._loadMedicationList(sKvnr);

            return fetch("/api/medications/plan/" + sKvnr)
                .then(function (response) {
                    if (!response.ok) {
                        throw new Error("Medication plan not found");
                    }
                    return response.json();
                })
                .then(function (medicationPlan) {
                    oModel.setProperty("/medicationPlan", medicationPlan);
                    return medicationPlan;
                })
                .catch(function (error) {
                    console.error("Failed to load medication plan:", error);
                    oModel.setProperty("/medicationPlan", { entries: [] });
                    return { entries: [] };
                });
        },

        _onDialogClose: function () {
            var oModel = this.getView().getModel("app");
            var sKvnr = oModel.getProperty("/currentKVNR");
            if (sKvnr) {
                this._loadMedicationPlan(sKvnr);
            }
        },

        onShowLinkedHistory: function (oEvent) {
            var oSource = oEvent.getSource();
            var oContext = oSource.getBindingContext("app");
            var oEmpEntry = oContext.getObject();
            var oModel = this.getView().getModel("app");

            // Get all eML entries
            var aEmlEntries = oModel.getProperty("/medicationList/entries") || [];

            // Filter to only linked entries (Prescriptions)
            var aLinkedPrescriptions = aEmlEntries.filter(function (entry) {
                return oEmpEntry.linkedEmlIds && oEmpEntry.linkedEmlIds.includes(entry.id);
            });

            if (aLinkedPrescriptions.length === 0) {
                MessageToast.show("No linked history entries found");
                return;
            }

            // Flatten structure: Prescription + its Dispensations
            var aHistoryEvents = [];
            aLinkedPrescriptions.forEach(function (presc) {
                // Add Prescription
                aHistoryEvents.push({
                    date: presc.authoredDate,
                    type: "Prescription",
                    medication: presc.medicationName,
                    pzn: presc.pzn,
                    flag: null,
                    raw: presc
                });

                // Add Dispensations
                if (presc.dispensations && presc.dispensations.length > 0) {
                    presc.dispensations.forEach(function (disp) {
                        var sFlag = null;
                        if (disp.substituted) {
                            sFlag = "[Substitution]";
                        } else {
                            sFlag = "[Refill]";
                        }

                        aHistoryEvents.push({
                            date: disp.authoredDate,
                            type: "Dispense",
                            medication: disp.medicationName,
                            pzn: disp.pzn,
                            flag: sFlag,
                            raw: disp
                        });
                    });
                }
            });

            // Sort by Date Descending
            aHistoryEvents.sort(function (a, b) {
                return new Date(b.date) - new Date(a.date); // Newest first
            });

            // Create message
            var sMessage = "Therapy History (" + aHistoryEvents.length + " events):\n\n";
            aHistoryEvents.forEach(function (event) {
                var sDateStr = event.date ? new Date(event.date).toLocaleDateString() : "No date";

                sMessage += "• " + sDateStr + " - " + event.type;
                if (event.flag) {
                    sMessage += " " + event.flag;
                }
                sMessage += "\n";
                sMessage += "  " + event.medication + " (PZN: " + event.pzn + ")\n\n";
            });

            MessageBox.information(sMessage, {
                title: "Medication History: " + oEmpEntry.medicationName,
                styleClass: "sapUiResponsivePadding--header sapUiResponsivePadding--content sapUiResponsivePadding--footer"
            });
        },

        onPauseMedication: function (oEvent) {
            var oItem = oEvent.getSource().getParent().getParent();
            var oContext = oItem.getBindingContext("app");
            var oEntry = oContext.getObject();
            var sKvnr = this.getView().getModel("app").getProperty("/currentKVNR");
            var that = this;

            var sNewStatus = oEntry.status === 'active' ? 'paused' : 'active';
            var sAction = sNewStatus === 'paused' ? 'Pause' : 'Reactivate';

            // Optimistic update
            var sOldStatus = oEntry.status;
            oContext.getModel().setProperty(oContext.getPath() + "/status", sNewStatus);
            this._setBusy(true);
            fetch("/api/medications/plan/" + sKvnr + "/entries/" + oEntry.id + "/status?status=" + sNewStatus, {
                method: "PATCH"
            })
                .then(function (response) {
                    if (!response.ok) {
                        throw new Error("Failed to update status");
                    }
                    return response.json();
                })
                .then(function (updatedPlan) {
                    MessageToast.show(sAction + " successful for " + oEntry.medicationName);
                    oContext.getModel().setProperty("/medicationPlan", updatedPlan);
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

        onDeleteMedication: function (oEvent) {
            var oItem = oEvent.getSource().getParent().getParent();
            var oContext = oItem.getBindingContext("app");
            var oEntry = oContext.getObject();
            var sKvnr = this.getView().getModel("app").getProperty("/currentKVNR");
            var that = this;

            MessageBox.confirm("Are you sure you want to delete " + oEntry.medicationName + "? This cannot be undone.", {
                icon: MessageBox.Icon.WARNING,
                title: "Delete Medication",
                actions: [MessageBox.Action.DELETE, MessageBox.Action.CANCEL],
                onClose: function (sAction) {
                    if (sAction === MessageBox.Action.DELETE) {
                        that._setBusy(true);
                        fetch("/api/medications/plan/" + sKvnr + "/entries/" + oEntry.id, {
                            method: "DELETE"
                        })
                            .then(function (response) {
                                if (!response.ok) {
                                    throw new Error("Failed to delete entry");
                                }
                                MessageToast.show("Deleted " + oEntry.medicationName);
                                return that._loadMedicationPlan(sKvnr);
                            })
                            .catch(function (error) {
                                console.error("Delete failed", error);
                                MessageBox.error("Failed to delete medication: " + error.message);
                            })
                            .finally(function () {
                                that._setBusy(false);
                            });
                    }
                }
            });
        },

        onAMTSCheck: function () {
            var oModel = this.getView().getModel("app");
            var oPlan = oModel.getProperty("/medicationPlan");

            if (!oPlan || !oPlan.entries || oPlan.entries.length === 0) {
                MessageToast.show("No medications in plan to check.");
                return;
            }

            var aActiveMeds = oPlan.entries.filter(function (m) {
                return m.status === "active";
            });

            if (aActiveMeds.length < 2) {
                MessageToast.show("Need at least 2 active medications to check for interactions.");
                return;
            }

            var aWarnings = AMTSSimulator.checkInteractions(aActiveMeds);

            if (aWarnings.length === 0) {
                MessageBox.success("AMTS Check: No interactions found.", {
                    title: "Safety Check Passed"
                });
            } else {
                var sMessage = "Found " + aWarnings.length + " potential interaction(s):\n\n";
                aWarnings.forEach(function (w) {
                    sMessage += "• " + w.title + "\n" + w.description + "\n\n";
                });

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
            var oContext = oItem.getBindingContext("app");
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
