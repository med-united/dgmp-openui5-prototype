sap.ui.define([
    "sap/ui/core/mvc/Controller",
    "sap/m/MessageToast",
    "sap/m/MessageBox",
    "epa/model/formatter",
    "epa/model/AMTSSimulator",
    "sap/ui/core/Fragment",
    "sap/ui/model/json/JSONModel"
], function (Controller, MessageToast, MessageBox, formatter, AMTSSimulator, Fragment, JSONModel) {
    "use strict";

    return Controller.extend("epa.controller.MedicationPlan", {
        formatter: formatter,

        onInit: function () {
            // Subscribe to event bus for refresh
            var oEventBus = sap.ui.getCore().getEventBus();
            oEventBus.subscribe("epa", "refreshData", this._onRefreshData, this);

            // Listen to model property changes? 
            // The model "app" is propagated from parent view.
            // We can attach to the binding of /currentKVNR
            this.getView().addEventDelegate({
                onBeforeRendering: function () {
                    var oModel = this.getView().getModel("app");
                    if (oModel) {
                        // Initial check if we have a KVNR
                        var sKvnr = oModel.getProperty("/currentKVNR");
                        if (sKvnr) {
                            this._loadMedicationPlan(sKvnr);
                        }

                        // Binding listener for future changes
                        var oBinding = oModel.bindProperty("/currentKVNR");
                        oBinding.attachChange(function (oEvent) {
                            var sNewKvnr = oEvent.getSource().getValue();
                            if (sNewKvnr) {
                                this._loadMedicationPlan(sNewKvnr);
                            }
                        }, this);
                    }
                }
            }, this);
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

        onShowLinkedHistory: function (oEvent) {
            var oSource = oEvent.getSource();
            var oContext = oSource.getBindingContext("app");
            var oEmpEntry = oContext.getObject();
            var oModel = this.getView().getModel("app");

            // Get all eML entries
            var aEmlEntries = oModel.getProperty("/medicationList/entries") || [];

            // Filter to only linked entries
            var aLinkedEntries = aEmlEntries.filter(function (entry) {
                return oEmpEntry.linkedEmlIds && oEmpEntry.linkedEmlIds.includes(entry.id);
            });

            if (aLinkedEntries.length === 0) {
                MessageToast.show("No linked history entries found");
                return;
            }

            // Create message with linked entries
            var sMessage = "Linked History (" + aLinkedEntries.length + "):\n\n";
            aLinkedEntries.forEach(function (entry) {
                sMessage += "• " + entry.medicationName + "\n";
                sMessage += "  " + entry.entryType + " - " + (entry.authoredDate || "No date") + "\n\n";
            });

            MessageBox.information(sMessage, {
                title: "Linked eML Entries for " + oEmpEntry.medicationName
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
                    // Refresh plan from backend to ensure consistency (version, timestamp)
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
                                // Reload plan
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
            // 1. Get current medication plan
            var oModel = this.getView().getModel("app");
            var oPlan = oModel.getProperty("/medicationPlan");

            if (!oPlan || !oPlan.entries || oPlan.entries.length === 0) {
                MessageToast.show("No medications in plan to check.");
                return;
            }

            // 2. Filter for active medications
            var aActiveMeds = oPlan.entries.filter(function (m) {
                return m.status === "active";
            });

            if (aActiveMeds.length < 2) {
                MessageToast.show("Need at least 2 active medications to check for interactions.");
                return;
            }

            // 3. Run simulation
            var aWarnings = AMTSSimulator.checkInteractions(aActiveMeds);

            // 4. Display results
            if (aWarnings.length === 0) {
                MessageBox.success("AMTS Check: No interactions found.", {
                    title: "Safety Check Passed"
                });
            } else {
                // Build warning message
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

        _getAddMedicationDialog: function () {
            var that = this;
            return new Promise(function (resolve) {
                sap.ui.require(["epa/controller/AddMedicationDialog.controller"], function (AddMedicationDialogController) {
                    if (!that._pAddMedicationDialog) {
                        var oDialogController = new AddMedicationDialogController();
                        that._oAddMedicationController = oDialogController;

                        that._pAddMedicationDialog = Fragment.load({
                            id: that.getView().getId(),
                            name: "epa.view.AddMedicationDialog",
                            controller: oDialogController
                        }).then(function (oDialog) {
                            that.getView().addDependent(oDialog);
                            oDialogController.getView = function () { return that.getView(); };
                            return oDialog;
                        });
                    }
                    resolve(that._pAddMedicationDialog);
                });
            });
        },

        onAddMedication: function () {
            var that = this;
            var oModel = this.getView().getModel("app");
            var sKvnr = oModel.getProperty("/currentKVNR");

            // Open Selection Dialog directly or Add Dialog? 
            // Logic in original was load eML then open Selection Dialog
            // We can trigger that if needed, or just open Add Dialog

            // Reusing logic: Load eML (we might not have it loaded in this controller context yet? model is shared)
            // We'll rely on shared model. 
            // Wait, loadMedicationList is on the other controller. 
            // We should probably just open the simple Add Dialog or communicate to MedicationList controller?
            // For simplicity, let's open the Add Dialog directly here which is reusable.
            // OR replicate the "Select from eML" flow. 

            // Let's implement basic Add here for now using _getMedicationSelectionDialog if locally available, 
            // but MedicationSelectionDialog logic was in PatientSelection.

            // Simplification: Direct Add Dialog for now.
            this._openAddDialog();
        },

        _openAddDialog: function (oPreFillData) {
            var that = this;
            this._getAddMedicationDialog().then(function (oDialog) {
                if (that._oAddMedicationController) {
                    that._oAddMedicationController.clearEditMode();
                }

                if (oPreFillData) {
                    that._oAddMedicationController.fillFields(oPreFillData);
                }

                oDialog.open();

                // Hack: Override the confirm action key to refresh this controller's data
                oDialog.detachAfterClose(that._onDialogClose, that);
                oDialog.attachAfterClose(that._onDialogClose, that);
            });
        },

        _onDialogClose: function () {
            var oModel = this.getView().getModel("app");
            var sKvnr = oModel.getProperty("/currentKVNR");
            if (sKvnr) {
                this._loadMedicationPlan(sKvnr);
            }
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
