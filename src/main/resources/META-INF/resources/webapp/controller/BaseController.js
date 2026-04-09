sap.ui.define([
    "sap/ui/core/mvc/Controller",
    "sap/m/MessageToast",
    "sap/ui/core/Fragment"
], function (Controller, MessageToast, Fragment) {
    "use strict";

    return Controller.extend("epa.controller.BaseController", {

        /**
         * Returns the FHIRModel registered as "fhir" on this view.
         * @returns {sap.fhir.model.r4.FHIRModel}
         */
        getFHIRModel: function () {
            return this.getView().getModel("fhir");
        },

        /**
         * Attaches a listener to /currentKVNR on the "ui" model.
         * When the KVNR changes, fnCallback is invoked with the new value.
         * Call this from onInit in feature controllers.
         *
         * Pattern per openui5-fhir docs: controllers re-bind their list controls
         * via bindItems()/bindAggregation() with the new patient= parameter when
         * the KVNR changes. Each controller is responsible for its own re-binding.
         *
         * @param {function} fnCallback - called with (sKvnr) on each change
         */
        _attachKvnrListener: function (fnCallback) {
            var that = this;
            this.getView().addEventDelegate({
                onBeforeRendering: function () {
                    var oModel = that.getView().getModel("ui");
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
