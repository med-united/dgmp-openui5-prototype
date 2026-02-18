sap.ui.define([
    "sap/ui/core/mvc/Controller",
    "sap/ui/model/json/JSONModel",
    "sap/m/MessageToast",
    "sap/m/MessageBox",
    "epa/model/formatter",
    "sap/ui/core/Fragment",
    "epa/model/AMTSSimulator",
    "sap/ui/table/TreeTable",
    "sap/ui/table/Column",
    "sap/ui/table/RowSettings"
], function (Controller, JSONModel, MessageToast, MessageBox, formatter, Fragment, AMTSSimulator, TreeTable, Column, RowSettings) {
    "use strict";

    return Controller.extend("epa.controller.PatientSelection", {
        formatter: formatter,

        onInit: function () {
            var oModel = new JSONModel({
                recentPatients: [],
                patientSelected: false,
                currentPatient: {},
                currentKVNR: "",
                medicationPlan: { entries: [] }, // Shared model
                medicationList: { entries: [] }, // Shared model
                reconciliationItems: [],         // Shared model
                eMLVisible: false,
                filterShowLinked: false,
                filterTimeRange: "3_MONTHS"
            });
            this.getView().setModel(oModel, "app");

            // Load recent patients after a short delay
            var that = this;
            setTimeout(function () {
                that._loadRecentPatients();
            }, 500);
        },

        _setBusy: function (bBusy) {
            this.getView().setBusy(bBusy);
        },

        onSearch: function (oEvent) {
            var sQuery = oEvent.getParameter("query") || oEvent.getParameter("newValue");

            if (!sQuery || sQuery.trim() === "") {
                MessageToast.show("Please enter a KVNR");
                return;
            }

            if (!this._validateKVNR(sQuery)) {
                MessageBox.error("KVNR must be exactly 10 alphanumeric characters");
                return;
            }

            this.getView().byId("kvnrSearchField").setValue(sQuery);
            this._searchPatient(sQuery);
        },

        _validateKVNR: function (sKvnr) {
            var kvnrRegex = /^[A-Z0-9]{10}$/;
            return kvnrRegex.test(sKvnr);
        },

        _addToRecentPatients: function (sKvnr) {
            // Logic moved inside _searchPatient for updating history
        },

        _searchPatient: function (sKvnr) {
            var that = this;
            var oModel = this.getView().getModel("app");

            this._setBusy(true);

            fetch("/api/patients/search?kvnr=" + sKvnr)
                .then(function (response) {
                    if (!response.ok) {
                        throw new Error("Patient not found");
                    }
                    return response.json();
                })
                .then(function (patient) {
                    oModel.setProperty("/currentPatient", patient);
                    oModel.setProperty("/currentKVNR", patient.kvnr); // Triggers sub-controllers
                    oModel.setProperty("/patientSelected", true);

                    // Update recent list
                    var aRecent = oModel.getProperty("/recentPatients");
                    if (!aRecent.find(function (p) { return p.kvnr === patient.kvnr; })) {
                        aRecent.unshift(patient);
                        if (aRecent.length > 5) aRecent.pop();
                        oModel.setProperty("/recentPatients", aRecent);

                        // Persist
                        localStorage.setItem("recentPatients", JSON.stringify(aRecent));
                    }
                })
                .catch(function (error) {
                    console.error("Patient search failed", error);
                    // Minimal fallback
                    oModel.setProperty("/currentKVNR", sKvnr); // Triggers sub-controllers even if patient details fail
                    oModel.setProperty("/currentPatient", { firstName: "Unknown", lastName: "Patient", kvnr: sKvnr });
                    oModel.setProperty("/patientSelected", true);
                })
                .finally(function () {
                    that._setBusy(false);
                });
        },

        _loadRecentPatients: function () {
            var oModel = this.getView().getModel("app");
            var aStored = [
                { firstName: "Max", lastName: "Mustermann", kvnr: "X123456789", birthDate: "1980-01-01" },
                { firstName: "Erika", lastName: "Musterfrau", kvnr: "Y987654321", birthDate: "1990-05-15" }
            ];

            try {
                var sStored = localStorage.getItem("recentPatients");
                if (sStored) {
                    var aParsed = JSON.parse(sStored);
                    if (aParsed && aParsed.length > 0) {
                        aStored = aParsed;
                    }
                }
            } catch (e) { }

            oModel.setProperty("/recentPatients", aStored);
        },

        onRecentPatientSelect: function (oEvent) {
            var oItem = oEvent.getParameter("listItem");
            var oContext = oItem.getBindingContext("app");
            var oPatient = oContext.getObject();

            this.getView().byId("kvnrSearchField").setValue(oPatient.kvnr);
            this._searchPatient(oPatient.kvnr);
        }

    });
});
