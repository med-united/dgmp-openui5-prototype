sap.ui.define([
    "sap/ui/model/json/JSONModel",
    "sap/fhir/model/r4/FHIRModel"
], function (JSONModel, FHIRModel) {
    "use strict";

    return {
        /**
         * Create the FHIRModel bound to /fhir.
         * POST-based search is configured via serviceUrl.search.defaultHttpMethod.
         * X-Requesting-Organization header is injected into all mutating requests.
         *
         * @param {string} [sOrgId="Hospital-A"] - Organisation identifier for X-Requesting-Organization
         * @returns {sap.fhir.model.r4.FHIRModel}
         */
        createFHIRModel: function (sOrgId) {
            return new FHIRModel("/fhir", {
                defaultHttpMethod: "POST",
                httpHeaders: {
                    "X-Requesting-Organization": sOrgId || "Hospital-A"
                }
            });
        },

        /**
         * Create the UI JSONModel for pure UI state (no FHIR equivalent).
         * Holds: currentKVNR, orgId, patientSelected, recentPatients,
         *        busy, eMLVisible, filters, reconciliation derived arrays,
         *        and the MedicationStatement-by-dispense map for link/unlink.
         *
         * @returns {sap.ui.model.json.JSONModel}
         */
        createUIModel: function () {
            return new JSONModel({
                currentPatient: null,
                currentKVNR: "",
                orgId: "Hospital-A",
                patientSelected: false,
                recentPatients: [],
                busy: false,
                eMLVisible: false,
                filterShowLinked: false,
                filterTimeRange: "3_MONTHS",
                reconciliation: {
                    emlEntries: [],
                    empEntries: [],
                    items: []
                },
                // Map: MedicationDispense.id → MedicationStatement.id
                // populated by MedicationList controller from the eML bundle
                medicationStatementMap: {}
            });
        }
    };
});
