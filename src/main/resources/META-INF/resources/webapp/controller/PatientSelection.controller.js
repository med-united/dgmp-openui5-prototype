sap.ui.define([
    "epa/controller/BaseController",
    "sap/m/MessageToast",
    "sap/m/MessageBox",
    "epa/model/formatter"
], function (BaseController, MessageToast, MessageBox, formatter) {
    "use strict";

    return BaseController.extend("epa.controller.PatientSelection", {
        formatter: formatter,

        onInit: function () {
            // Models ('fhir' and 'ui') are created by the Component via manifest.json.
            // Populate the recent-patients list from localStorage after a short delay.
            var that = this;
            setTimeout(function () {
                that._loadRecentPatients();
            }, 500);
        },

        _setBusy: function (bBusy) {
            this.getView().getModel("ui").setProperty("/busy", bBusy);
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

        /**
         * Look up a patient by KVNR using the FHIRModel.
         * Uses POST /fhir/Patient/_search with identifier param (no KVNR in URL).
         *
         * @param {string} sKvnr
         */
        _searchPatient: function (sKvnr) {
            var that = this;
            var oUIModel = this.getView().getModel("ui");

            this._setBusy(true);

            // Use fetch for patient lookup — FHIRModel list binding is overkill for a
            // single one-shot search triggered by a user action. The KVNR goes in the POST body.
            fetch("/fhir/Patient/_search", {
                method: "POST",
                headers: { "Content-Type": "application/x-www-form-urlencoded" },
                body: "identifier=http%3A%2F%2Ffhir.de%2Fsid%2Fgkv%2Fkvid-10%7C" + encodeURIComponent(sKvnr)
            })
                .then(function (response) {
                    if (!response.ok) throw new Error("Patient not found");
                    return response.json();
                })
                .then(function (oBundle) {
                    var aEntries = (oBundle && oBundle.entry) ? oBundle.entry : [];
                    var oPatientResource = aEntries.length > 0 ? aEntries[0].resource : null;

                    var oPatient;
                    if (oPatientResource) {
                        // Map FHIR Patient resource to a flat display object
                        var oName = (oPatientResource.name && oPatientResource.name[0]) || {};
                        var sFamily = oName.family || "";
                        var sGiven = (oName.given && oName.given[0]) || "";
                        oPatient = {
                            kvnr: sKvnr,
                            firstName: sGiven,
                            lastName: sFamily,
                            birthDate: oPatientResource.birthDate || "",
                            fhirId: oPatientResource.id || sKvnr
                        };
                    } else {
                        // Fallback: patient not found but proceed with KVNR only
                        oPatient = { kvnr: sKvnr, firstName: "Unbekannt", lastName: "Patient", birthDate: "", fhirId: sKvnr };
                    }

                    oUIModel.setProperty("/currentPatient", oPatient);
                    oUIModel.setProperty("/currentKVNR", sKvnr);
                    oUIModel.setProperty("/patientSelected", true);

                    // Update recent list
                    var aRecent = oUIModel.getProperty("/recentPatients") || [];
                    if (!aRecent.find(function (p) { return p.kvnr === sKvnr; })) {
                        aRecent.unshift(oPatient);
                        if (aRecent.length > 5) aRecent.pop();
                        oUIModel.setProperty("/recentPatients", aRecent);
                        try { localStorage.setItem("recentPatients", JSON.stringify(aRecent)); } catch (e) { /* ignore */ }
                    }
                })
                .catch(function (error) {
                    console.error("Patient search failed", error);
                    // Graceful fallback: let sub-controllers load data by KVNR anyway
                    oUIModel.setProperty("/currentKVNR", sKvnr);
                    oUIModel.setProperty("/currentPatient", { kvnr: sKvnr, firstName: "Unbekannt", lastName: "Patient", birthDate: "" });
                    oUIModel.setProperty("/patientSelected", true);
                })
                .finally(function () {
                    that._setBusy(false);
                });
        },

        _loadRecentPatients: function () {
            var oUIModel = this.getView().getModel("ui");
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
            } catch (e) { /* ignore */ }

            oUIModel.setProperty("/recentPatients", aStored);
        },

        onRecentPatientSelect: function (oEvent) {
            var oItem = oEvent.getParameter("listItem");
            var oContext = oItem.getBindingContext("ui");
            var oPatient = oContext.getObject();

            this.getView().byId("kvnrSearchField").setValue(oPatient.kvnr);
            this._searchPatient(oPatient.kvnr);
        }

    });
});
