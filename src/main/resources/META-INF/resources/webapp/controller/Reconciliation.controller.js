sap.ui.define([
    "sap/ui/core/mvc/Controller",
    "sap/m/MessageToast",
    "sap/ui/table/TreeTable",
    "epa/model/formatter",
    "sap/m/ActionSheet",
    "sap/m/Button"
], function (Controller, MessageToast, TreeTable, formatter, ActionSheet, Button) {
    "use strict";

    return Controller.extend("epa.controller.Reconciliation", {
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

            this.getView().addEventDelegate({
                onBeforeRendering: function () {
                    var oModel = this.getView().getModel("app");
                    if (oModel) {
                        var sKvnr = oModel.getProperty("/currentKVNR");
                        if (sKvnr) {
                            this._loadReconciliation(sKvnr);
                        }

                        var oBinding = oModel.bindProperty("/currentKVNR");
                        oBinding.attachChange(function (oEvent) {
                            var sNewKvnr = oEvent.getSource().getValue();
                            if (sNewKvnr) {
                                this._loadReconciliation(sNewKvnr);
                            }
                        }, this);
                    }
                }
            }, this);
        },

        _onRefreshData: function () {
            this._loadReconciliation();
        },

        _loadReconciliation: function (sKvnr) {
            // Validate kvnr
            if (sKvnr && typeof sKvnr === 'object' && sKvnr.sId) {
                sKvnr = null;
            }

            var oModel = this.getView().getModel("app");
            if (!sKvnr && oModel) {
                sKvnr = oModel.getProperty("/currentKVNR");
            }

            if (!sKvnr) {
                return;
            }

            var that = this;
            var sTimeRange = oModel.getProperty("/filterTimeRange") || "3_MONTHS";

            // Build query parameters
            var sUrl = "/api/medications/reconciliation/" + sKvnr;
            var aParams = [];
            if (sTimeRange) aParams.push("timeRange=" + sTimeRange);
            if (aParams.length > 0) sUrl += "?" + aParams.join("&");

            // Also load Medication List and Plan for context
            this._loadMedicationList(sKvnr);
            this._loadMedicationPlan(sKvnr);

            fetch(sUrl)
                .then(function (response) {
                    if (!response.ok) {
                        throw new Error("Failed to load reconciliation items (HTTP " + response.status + ")");
                    }
                    return response.json();
                })
                .then(function (items) {
                    // Map backend data to frontend
                    var mappedItems = items.map(function (item) {
                        return Object.assign({}, item, {
                            // Ensure status is valid or default to ORPHAN_NEW
                            status: item.status || "ORPHAN_NEW",
                            markedAsError: false
                        });
                    });

                    oModel.setProperty("/reconciliationItems", mappedItems);
                    that._applyClientFilters(); // Apply client-side filters like 'Show Linked'
                })
                .catch(function (error) {
                    console.error("Failed to load reconciliation:", error);
                    oModel.setProperty("/reconciliationItems", []);
                });
        },

        _loadMedicationList: function (sKvnr) {
            var oModel = this.getView().getModel("app");
            fetch("/api/medications/list/" + sKvnr)
                .then(function (response) {
                    if (!response.ok) throw new Error("List not found");
                    return response.json();
                })
                .then(function (medicationList) {
                    oModel.setProperty("/medicationList", medicationList);
                })
                .catch(function (error) {
                    console.error("Failed to load medication list:", error);
                    oModel.setProperty("/medicationList", { entries: [] });
                });
        },

        _loadMedicationPlan: function (sKvnr) {
            var oModel = this.getView().getModel("app");
            fetch("/api/medications/plan/" + sKvnr)
                .then(function (response) {
                    if (!response.ok) throw new Error("Plan not found");
                    return response.json();
                })
                .then(function (medicationPlan) {
                    oModel.setProperty("/medicationPlan", medicationPlan);
                })
                .catch(function (error) {
                    console.error("Failed to load medication plan:", error);
                    oModel.setProperty("/medicationPlan", { entries: [] });
                });
        },

        onTimeFilterChange: function (oEvent) {
            var sKey = oEvent.getParameter("selectedItem").getKey();
            this.getView().getModel("app").setProperty("/filterTimeRange", sKey);
            this._loadReconciliation();
        },

        onShowLinkedToggle: function (oEvent) {
            var bShow = oEvent.getParameter("selected");
            this.getView().getModel("app").setProperty("/filterShowLinked", bShow);
            this._applyClientFilters();
        },

        _applyClientFilters: function () {
            var oModel = this.getView().getModel("app");
            var bShowLinked = oModel.getProperty("/filterShowLinked");
            var aItems = oModel.getProperty("/reconciliationItems") || [];

            var oTable = this.getView().byId("reconciliationTree"); // View ID scoped

            if (oTable) {
                var oBinding = oTable.getBinding("rows");
                var aFilters = [];

                if (!bShowLinked) {
                    aFilters.push(new sap.ui.model.Filter("status", sap.ui.model.FilterOperator.NE, "LINKED"));
                }

                // Filter 'markedAsError'
                aFilters.push(new sap.ui.model.Filter("markedAsError", sap.ui.model.FilterOperator.NE, true));

                oBinding.filter(aFilters);

                // Update No Data Text
                var iHiddenLinked = 0;
                if (!bShowLinked) {
                    iHiddenLinked = aItems.filter(function (item) { return item.status === "LINKED"; }).length;
                }

                if (iHiddenLinked > 0) {
                    oTable.setNoData("No data (" + iHiddenLinked + " Linked hidden)");
                } else {
                    oTable.setNoData("No data");
                }
            }
        },

        onMarkAsError: function (oEvent) {
            var oContext = oEvent.getSource().getBindingContext("app");

            // Soft delete (hide) by setting a flag in the model
            oContext.getModel().setProperty(oContext.getPath() + "/markedAsError", true);

            // Re-apply filters to hide it
            this._applyClientFilters();
            MessageToast.show("Entry marked as error and hidden.");
        },

        onLinkFromTree: function (oEvent) {
            var oSource = oEvent.getSource();
            var oContext = oSource.getBindingContext("app");
            var oReconcileItem = oContext.getObject();

            var that = this;
            var oView = this.getView();
            var oModel = oView.getModel("app");
            var aPlanEntries = oModel.getProperty("/medicationPlan/entries") || [];

            if (aPlanEntries.length === 0) {
                MessageToast.show("No active medication plan entries to link to.");
                return;
            }

            var aButtons = [];
            aPlanEntries.forEach(function (oEmpEntry) {
                if (oEmpEntry.status !== 'active') return;

                aButtons.push(new Button({
                    text: "Plan: " + oEmpEntry.medicationName,
                    icon: "sap-icon://chain-link",
                    press: function () {
                        that._performLink(oReconcileItem, oEmpEntry);
                    }
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
                afterClose: function () {
                    oActionSheet.destroy();
                }
            });
            oView.addDependent(oActionSheet);
            oActionSheet.openBy(oSource);
        },

        _performLink: function (oReconcileItem, oEmpEntry) {
            // API Call
            var sEmlId = oReconcileItem.id;
            var that = this;
            var oModel = this.getView().getModel("app");
            var sKvnr = oModel.getProperty("/currentKVNR");

            fetch("/api/medications/" + sKvnr + "/link-emp?emlId=" + sEmlId + "&empId=" + oEmpEntry.id, {
                method: "POST",
                headers: {
                    "X-Requesting-Organization": "Hospital-A"
                }
            })
                .then(function (response) {
                    if (!response.ok) throw new Error("Failed to create link");
                    MessageToast.show("Linked successfully");
                    that._loadReconciliation();
                    sap.ui.getCore().getEventBus().publish("epa", "refreshData");
                })
                .catch(function (error) {
                    console.error("Link failed", error);
                    MessageToast.show("Failed to create link");
                });
        },

        onUnlinkFromTree: function (oEvent) {
            var oContext = oEvent.getSource().getBindingContext("app");
            var oReconcileItem = oContext.getObject();
            var that = this;
            var oModel = this.getView().getModel("app");
            var sKvnr = oModel.getProperty("/currentKVNR");

            fetch("/api/medications/" + sKvnr + "/unlink-emp?emlId=" + oReconcileItem.id, {
                method: "POST", // Changed to POST as per new API
                headers: {
                    "X-Requesting-Organization": "Hospital-A"
                }
            })
                .then(function (response) {
                    if (!response.ok) throw new Error("Failed to remove link");
                    MessageToast.show("Unlinked");
                    that._loadReconciliation();
                    sap.ui.getCore().getEventBus().publish("epa", "refreshData");
                })
                .catch(function (error) {
                    console.error("Unlink failed", error);
                    MessageToast.show("Failed to remove link");
                });
        },

        onAddToPlanFromTree: function (oEvent) {
            // Logic to take an orphan eML item and create a new Plan entry
            // This corresponds to opening AddDialog pre-filled with this item

            var oContext = oEvent.getSource().getBindingContext("app");
            var oReconcileItem = oContext.getObject();

            // This needs access to the reusable AddDialog.
            // We can load it here too.
            this._openAddDialog(oReconcileItem);
        },

        _openAddDialog: function (oPreFillData) {
            var that = this;

            // Lazy load the AddDialog helper controller or just fragment?
            // Reuse approach from MedicationPlan controller
            sap.ui.require(["epa/controller/AddMedicationDialog.controller", "sap/ui/core/Fragment"], function (AddMedicationDialogController, Fragment) {
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

                that._pAddMedicationDialog.then(function (oDialog) {
                    if (that._oAddMedicationController) {
                        that._oAddMedicationController.clearEditMode();
                        if (oPreFillData) {
                            that._oAddMedicationController.fillFields(oPreFillData);
                        }
                    }

                    oDialog.open();
                    oDialog.detachAfterClose(that._onDialogClose, that);
                    oDialog.attachAfterClose(that._onDialogClose, that);
                });
            });
        },

        _onDialogClose: function () {
            this._loadReconciliation();
            sap.ui.getCore().getEventBus().publish("epa", "refreshData");
        }

    });
});
