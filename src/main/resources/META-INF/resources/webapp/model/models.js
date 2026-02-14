sap.ui.define([
    "sap/ui/model/json/JSONModel"
], function (JSONModel) {
    "use strict";

    return {
        /**
         * Create and initialize app models
         * @returns {sap.ui.model.json.JSONModel} Application model
         */
        createAppModel: function () {
            return new JSONModel({
                currentPatient: null,
                recentPatients: [],
                busy: false,
                error: null
            });
        },

        /**
         * Create medications model
         * @returns {sap.ui.model.json.JSONModel} Medications model
         */
        createMedicationsModel: function () {
            return new JSONModel({
                medicationList: [],
                medicationPlan: [],
                reconciliation: []
            });
        }
    };
});
