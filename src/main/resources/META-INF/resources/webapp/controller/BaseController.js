sap.ui.define([
    "sap/ui/core/mvc/Controller",
    "sap/m/MessageToast",
    "sap/ui/core/Fragment"
], function (Controller, MessageToast, Fragment) {
    "use strict";

    return Controller.extend("epa.controller.BaseController", {

        /**
         * Attaches a listener to /currentKVNR on the "app" model.
         * When the KVNR changes, fnCallback is invoked with the new value.
         * Call this from onInit in feature controllers.
         *
         * @param {function} fnCallback - called with (sKvnr) on each change
         */
        _attachKvnrListener: function (fnCallback) {
            var that = this;
            this.getView().addEventDelegate({
                onBeforeRendering: function () {
                    var oModel = that.getView().getModel("app");
                    if (oModel) {
                        var sKvnr = oModel.getProperty("/currentKVNR");
                        if (sKvnr) {
                            fnCallback.call(that, sKvnr);
                        }

                        var oBinding = oModel.bindProperty("/currentKVNR");
                        oBinding.attachChange(function (oEvent) {
                            var sNewKvnr = oEvent.getSource().getValue();
                            if (sNewKvnr) {
                                fnCallback.call(that, sNewKvnr);
                            }
                        }, that);
                    }
                }
            }, this);
        },

        /**
         * Loads the medication list for the given KVNR and stores it on the
         * shared "app" model at /medicationList.
         *
         * @param {string} sKvnr
         * @returns {Promise}
         */
        _loadMedicationList: function (sKvnr) {
            var oModel = this.getView().getModel("app");
            return fetch("/api/medications/list/" + sKvnr)
                .then(function (response) {
                    if (!response.ok) throw new Error("List not found");
                    return response.json();
                })
                .then(function (medicationList) {
                    oModel.setProperty("/medicationList", medicationList);
                    oModel.setProperty("/eMLVisible", true);
                    return medicationList;
                })
                .catch(function (error) {
                    console.error("Failed to load medication list:", error);
                    oModel.setProperty("/medicationList", { entries: [] });
                    oModel.setProperty("/eMLVisible", true);
                    return { entries: [] };
                });
        },

        /**
         * Loads the medication plan for the given KVNR and stores it on the
         * shared "app" model at /medicationPlan.
         *
         * @param {string} sKvnr
         * @returns {Promise}
         */
        _loadMedicationPlan: function (sKvnr) {
            var oModel = this.getView().getModel("app");
            return fetch("/api/medications/plan/" + sKvnr)
                .then(function (response) {
                    if (!response.ok) throw new Error("Plan not found");
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

        /**
         * Lazily loads and returns the AddMedicationDialog fragment.
         * The dialog instance is cached on this._pAddMedicationDialog.
         *
         * @returns {Promise<sap.m.Dialog>}
         */
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

        /**
         * Opens the AddMedicationDialog, optionally pre-filling fields.
         * Clears edit mode first unless setEditMode is called separately.
         *
         * @param {object} [oPreFillData] - optional medication data to pre-fill
         */
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
                oDialog.detachAfterClose(that._onDialogClose, that);
                oDialog.attachAfterClose(that._onDialogClose, that);
            });
        },

        /**
         * Called when the AddMedicationDialog closes. Override in sub-controllers
         * to trigger the appropriate data reload.
         */
        _onDialogClose: function () { }

    });
});
