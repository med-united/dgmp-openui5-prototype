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
                    var mappedItems = items.map(function (item) {
                        return Object.assign({}, item, {
                            status: item.status || "ORPHAN_NEW",
                            markedAsError: false
                        });
                    });

                    oModel.setProperty("/reconciliationItems", mappedItems);
                    that._applyClientFilters();
                })
                .catch(function (error) {
                    console.error("Failed to load reconciliation:", error);
                    oModel.setProperty("/reconciliationItems", []);
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

            var oTable = this.getView().byId("reconciliationTree");

            if (oTable) {
                var oBinding = oTable.getBinding("rows");
                var aFilters = [];

                if (!bShowLinked) {
                    aFilters.push(new sap.ui.model.Filter("status", sap.ui.model.FilterOperator.NE, "LINKED"));
                }

                aFilters.push(new sap.ui.model.Filter("markedAsError", sap.ui.model.FilterOperator.NE, true));

                oBinding.filter(aFilters);

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
            oContext.getModel().setProperty(oContext.getPath() + "/markedAsError", true);
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
                method: "POST",
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
            var oContext = oEvent.getSource().getBindingContext("app");
            var oReconcileItem = oContext.getObject();
            this._openAddDialog(oReconcileItem);
        }

    });
});
