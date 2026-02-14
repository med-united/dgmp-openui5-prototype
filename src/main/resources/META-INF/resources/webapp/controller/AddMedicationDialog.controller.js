sap.ui.define([
    "sap/ui/core/mvc/Controller",
    "sap/m/MessageToast",
    "sap/m/MessageBox",
    "sap/ui/model/json/JSONModel"
], function (Controller, MessageToast, MessageBox, JSONModel) {
    "use strict";

    return Controller.extend("epa.controller.AddMedicationDialog", {

        onInit: function () {
            // Init logic if needed
        },

        // Helper to get parent view (set in index.html)
        _getParentView: function () {
            return this.getView();
        },

        // Edit mode properties
        _editMode: false,
        _editEntryId: null,

        /**
         * Set dialog to edit mode and pre-fill with entry data.
         * @param {string} entryId - ID of the entry being edited
         * @param {object} entryData - Existing entry data to pre-fill
         */
        setEditMode: function (entryId, entryData) {
            this._editMode = true;
            this._editEntryId = entryId;

            // Pre-fill form fields
            var oView = this._getParentView();
            oView.byId("inputPZN").setValue(entryData.pzn || "");
            oView.byId("inputMedicationName").setValue(entryData.medicationName || "");
            oView.byId("inputStrength").setValue(entryData.strength || "");
            oView.byId("inputActiveIngredient").setValue(entryData.activeIngredient || "");
            oView.byId("inputDosageStructured").setValue(entryData.dosageStructured || "");
            oView.byId("inputDosageText").setValue(entryData.dosageText || "");
            oView.byId("inputIntakeInstructions").setValue(entryData.intakeInstructions || "");
            oView.byId("inputIndication").setValue(entryData.indication || "");

            // Update dialog title
            var oDialog = oView.byId("addMedicationDialog");
            if (oDialog) {
                oDialog.setTitle("Edit Medication");
            }
        },

        /**
         * Clear edit mode and reset dialog to "add new" mode.
         */
        clearEditMode: function () {
            this._editMode = false;
            this._editEntryId = null;

            // Reset dialog title
            var oView = this._getParentView();
            var oDialog = oView.byId("addMedicationDialog");
            if (oDialog) {
                oDialog.setTitle("Add Medication");
            }
        },

        onPZNSearch: function (oEvent) {
            var sPZN = oEvent.getParameter("query") || oEvent.getParameter("newValue");
            if (!sPZN || sPZN.length < 8) {
                MessageToast.show("Please enter a valid 8-digit PZN");
                return;
            }

            // Mock PZN Lookup
            var oMockData = this._mockPZNLookup(sPZN);

            if (oMockData) {
                // Use parent view's byId since controls are prefixed with parent view ID
                var oView = this._getParentView();

                oView.byId("inputMedicationName").setValue(oMockData.medicationName);
                oView.byId("inputStrength").setValue(oMockData.strength);
                oView.byId("inputActiveIngredient").setValue(oMockData.activeIngredient);
                oView.byId("inputDosageStructured").setValue(oMockData.dosageStructured);

                MessageToast.show("PZN found: " + oMockData.medicationName);
            } else {
                MessageToast.show("PZN not found (Mock database)");
            }
        },

        _mockPZNLookup: function (sPZN) {
            var mDatabase = {
                "12345678": {
                    medicationName: "Ibuprofen 400mg",
                    strength: "400mg",
                    activeIngredient: "Ibuprofen",
                    dosageStructured: "1-0-1-0"
                },
                "87654321": {
                    medicationName: "Paracetamol 500mg",
                    strength: "500mg",
                    activeIngredient: "Paracetamol",
                    dosageStructured: "1-1-1-1"
                },
                "11223344": {
                    medicationName: "Aspirin 100mg",
                    strength: "100mg",
                    activeIngredient: "Acetylsalicylsäure",
                    dosageStructured: "1-0-0-0"
                }
            };
            return mDatabase[sPZN];
        },

        onSaveMedication: function () {
            var oView = this._getParentView();
            var sName = oView.byId("inputMedicationName").getValue();
            var sPZN = oView.byId("inputPZN").getValue();
            var sDosageStruct = oView.byId("inputDosageStructured").getValue();
            var sDosageText = oView.byId("inputDosageText").getValue();

            // Validation
            if (!sName || !sPZN) {
                MessageBox.error("Medication Name and PZN are required.");
                return;
            }

            if (!sDosageStruct && !sDosageText) {
                MessageBox.error("Please provide either a structured dosage or dosage text.");
                return;
            }

            // Construct payload
            var oEntry = {
                medicationName: sName,
                pzn: sPZN,
                strength: oView.byId("inputStrength").getValue(),
                activeIngredient: oView.byId("inputActiveIngredient").getValue(),
                dosageStructured: sDosageStruct,
                dosageText: sDosageText,
                intakeInstructions: oView.byId("inputInstructions").getValue(),
                note: oView.byId("inputNote").getValue(),
                indication: oView.byId("inputIndication").getValue(),
                entryType: oView.byId("selectEntryType").getSelectedKey(),
                authoredDate: new Date().toISOString()
            };

            this._saveToBackend(oEntry);
        },

        _saveToBackend: function (oEntry) {
            // Need KVNR. It's in the app model.
            var oModel = this.getView().getModel("app");
            var sKvnr = oModel.getProperty("/currentKVNR");
            var that = this;
            var oView = this._getParentView();

            var sUrl = "/api/medications/plan/" + sKvnr + "/entries";
            var sMethod = "POST";

            if (this._editMode) {
                sUrl += "/" + this._editEntryId;
                sMethod = "PUT";
            }

            fetch(sUrl, {
                method: sMethod,
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(oEntry)
            })
                .then(function (response) {
                    if (response.status === 409) {
                        return response.json().then(function (match) {
                            var oMainController = that.getView().getController();
                            if (oMainController && oMainController._handleDuplicateResponse) {
                                oView.byId("addMedicationDialog").close();
                                oMainController._handleDuplicateResponse(match, function (bIgnore) {
                                    // Retry callback
                                    if (bIgnore) {
                                        that._retrySave(sKvnr, oEntry);
                                    }
                                });
                            } else {
                                MessageBox.warning("Duplicate detected but could not open comparison dialog.");
                            }
                        });
                    }

                    if (!response.ok) throw new Error("Failed to save");
                    return response.json();
                })
                .then(function (updatedPlan) {
                    var sMessage = that._editMode ? "Medication updated successfully" : "Medication added successfully";
                    MessageToast.show(sMessage);

                    if (that._editMode) {
                        that.clearEditMode();
                    }

                    oView.byId("addMedicationDialog").close();
                    // Refresh data
                    var oMainController = that.getView().getController();
                    if (oMainController && oMainController._loadMedicationPlan) {
                        oMainController._loadMedicationPlan(sKvnr);
                    }
                })
                .catch(function (err) {
                    if (!err.message.includes("Duplicate")) {
                        MessageBox.error("Error: " + err.message);
                    }
                });
        },

        _retrySave: function (sKvnr, oEntry) {
            var that = this;
            fetch("/api/medications/plan/" + sKvnr + "/entries?ignoreDuplicates=true", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(oEntry)
            })
                .then(function (res) { return res.json(); })
                .then(function () {
                    MessageToast.show("Medication added (duplicate ignored)");
                    var oMainController = that.getView().getController();
                    if (oMainController) oMainController._loadMedicationPlan(sKvnr);
                });
        },

        onCancelMedication: function () {
            var oView = this._getParentView();
            oView.byId("addMedicationDialog").close();
        }
    });
});
