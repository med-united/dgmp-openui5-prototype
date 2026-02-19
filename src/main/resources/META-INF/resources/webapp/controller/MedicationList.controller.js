sap.ui.define([
    "sap/ui/core/mvc/Controller",
    "sap/m/MessageToast",
    "sap/m/MessageBox",
    "epa/model/formatter",
    "sap/ui/core/Fragment"
], function (Controller, MessageToast, MessageBox, formatter, Fragment) {
    "use strict";

    return Controller.extend("epa.controller.MedicationList", {
        formatter: formatter,

        onInit: function () {
            var oEventBus = sap.ui.getCore().getEventBus();
            oEventBus.subscribe("epa", "refreshData", this._onRefreshData, this);

            this.getView().addEventDelegate({
                onBeforeRendering: function () {
                    var oModel = this.getView().getModel("app");
                    if (oModel) {
                        var sKvnr = oModel.getProperty("/currentKVNR");
                        if (sKvnr) {
                            this._loadMedicationList(sKvnr);
                        }

                        var oBinding = oModel.bindProperty("/currentKVNR");
                        oBinding.attachChange(function (oEvent) {
                            var sNewKvnr = oEvent.getSource().getValue();
                            if (sNewKvnr) {
                                this._loadMedicationList(sNewKvnr);
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
                    this._loadMedicationList(sKvnr);
                }
            }
        },

        _loadMedicationList: function (sKvnr) {
            var that = this;
            var oModel = this.getView().getModel("app");

            return fetch("/api/medications/list/" + sKvnr)
                .then(function (response) {
                    if (!response.ok) {
                        throw new Error("Medication list not found");
                    }
                    return response.json();
                })
                .then(function (medicationList) {
                    oModel.setProperty("/medicationList", medicationList);
                    oModel.setProperty("/eMLVisible", true);

                    if (medicationList.entries && medicationList.entries.length > 0) {
                        // MessageToast.show("Loaded " + medicationList.entries.length + " medication entries");
                    } else {
                        // MessageToast.show("No medications found in eML");
                    }
                    return medicationList;
                })
                .catch(function (error) {
                    console.error("Failed to load medication list:", error);
                    oModel.setProperty("/medicationList", { entries: [] });
                    oModel.setProperty("/eMLVisible", true);
                    return { entries: [] };
                });
        },

        onMedicationRowSelect: function (oEvent) {
            var oContext;

            // Handle sap.ui.table.TreeTable selection
            var iRowIndex = oEvent.getParameter("rowIndex");
            if (iRowIndex !== undefined && iRowIndex > -1) {
                oContext = oEvent.getSource().getContextByIndex(iRowIndex);
            }

            if (!oContext) {
                return;
            }

            var oMed = oContext.getObject();

            if (!oMed) {
                return;
            }

            // Optional: check if it's a detail popup or something. 
            // Previous code showed popup on select
            MessageBox.information(
                "Medication Details:\n\n" +
                "Type: " + oMed.entryType + "\n" +
                "Medication: " + oMed.medicationName + "\n" +
                "Active Ingredient: " + oMed.activeIngredient + "\n" +
                "Strength: " + (oMed.strength || "N/A") + "\n" +
                "PZN: " + oMed.pzn + "\n" +
                "Dosage: " + (oMed.dosageText || oMed.dosageStructured || "N/A") + "\n" +
                "Indication: " + (oMed.indication || "N/A") + "\n" +
                "Date: " + this._formatDateTime(oMed.authoredDate) + "\n" +
                (oMed.prescriber ? "Prescriber: " + oMed.prescriber + "\n" : "") +
                (oMed.pharmacy ? "Pharmacy: " + oMed.pharmacy + "\n" : "")
            );
        },

        _formatDateTime: function (sDate) {
            if (!sDate) return "N/A";
            return new Date(sDate).toLocaleString();
        },

        onLinkPress: function (oEvent) {
            // Logic to handle linking from the TreeTable directly?
            // Or was this the simple button in list?
            // In TreeTable, button press event source is the button.
            var oContext = oEvent.getSource().getBindingContext("app");
            var oEntry = oContext.getObject();
            var that = this;
            var oView = this.getView();

            // If already linked, offer to unlink
            if (oEntry.linkedToPlanId) {
                sap.ui.require(["sap/m/ActionSheet", "sap/m/Button"], function (ActionSheet, Button) {
                    var oActionSheet = new ActionSheet({
                        title: "Medication Link",
                        showCancelButton: true,
                        buttons: [
                            new Button({
                                text: "Unlink from Plan",
                                icon: "sap-icon://broken-link",
                                type: "Reject",
                                press: function () {
                                    that.onUnlinkEntry(oEntry);
                                }
                            })
                        ]
                    });
                    oView.addDependent(oActionSheet);
                    oActionSheet.openBy(oEvent.getSource());
                });
                return;
            }

            // If not linked, show candidates from eMP
            var oModel = oView.getModel("app");
            var aPlanEntries = oModel.getProperty("/medicationPlan/entries") || [];

            if (aPlanEntries.length === 0) {
                MessageToast.show("No active medication plan entries to link to.");
                return;
            }

            sap.ui.require(["sap/m/ActionSheet", "sap/m/Button"], function (ActionSheet, Button) {
                var aButtons = [];

                aPlanEntries.forEach(function (oEmpEntry) {
                    if (oEmpEntry.status !== 'active') return;

                    aButtons.push(new Button({
                        text: "Plan: " + oEmpEntry.medicationName + " (" + (oEmpEntry.dosageStructured || oEmpEntry.dosageText || "No dosage") + ")",
                        icon: "sap-icon://chain-link",
                        press: function () {
                            that.onLinkToPlanEntry(oEntry, oEmpEntry);
                        }
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

        onLinkToPlanEntry: function (oEmlEntry, oEmpEntry) {
            var that = this;
            var sKvnr = this.getView().getModel("app").getProperty("/currentKVNR");

            fetch("/api/medications/" + sKvnr + "/link-emp?emlId=" + oEmlEntry.id + "&empId=" + oEmpEntry.id, {
                method: "POST",
                headers: {
                    "X-Requesting-Organization": "Hospital-A"
                }
            })
                .then(function (response) {
                    if (!response.ok) throw new Error("Failed to create link");
                    MessageToast.show("Linked to " + oEmpEntry.medicationName);

                    that._loadMedicationList(sKvnr);
                    // Notify other controllers
                    sap.ui.getCore().getEventBus().publish("epa", "refreshData");
                })
                .catch(function (error) {
                    console.error("Link failed", error);
                    MessageToast.show("Failed to create link");
                });
        },

        onUnlinkEntry: function (oEmlEntry) {
            var that = this;
            var sKvnr = this.getView().getModel("app").getProperty("/currentKVNR");

            fetch("/api/medications/" + sKvnr + "/unlink-emp?emlId=" + oEmlEntry.id, {
                method: "POST",
                headers: {
                    "X-Requesting-Organization": "Hospital-A"
                }
            })
                .then(function (response) {
                    if (!response.ok) throw new Error("Failed to remove link");
                    MessageToast.show("Unlinked");

                    that._loadMedicationList(sKvnr);
                    // Notify others
                    sap.ui.getCore().getEventBus().publish("epa", "refreshData");
                })
                .catch(function (error) {
                    console.error("Unlink failed", error);
                    MessageToast.show("Failed to remove link");
                });
        }
    });
});
