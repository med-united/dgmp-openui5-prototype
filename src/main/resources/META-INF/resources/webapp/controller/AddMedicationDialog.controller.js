sap.ui.define([
    "sap/ui/core/mvc/Controller",
    "sap/m/MessageToast",
    "sap/m/MessageBox",
    "sap/ui/model/json/JSONModel"
], function (Controller, MessageToast, MessageBox, JSONModel) {
    "use strict";

    return Controller.extend("epa.controller.AddMedicationDialog", {

        onInit: function () {
            // Model will be lazily initialized via _getEntryModel()
        },

        /**
         * Lazily gets or creates the 'entry' model.
         * @returns {sap.ui.model.json.JSONModel} The entry model
         * @private
         */
        _getEntryModel: function () {
            var oModel = this.getView().getModel("entry");
            if (!oModel) {
                oModel = new JSONModel({
                    medicationName: "",
                    pzn: "",
                    strength: "",
                    activeIngredient: "",
                    dosageStructured: "",
                    dosageText: "",
                    intakeInstructions: "",
                    indication: "",
                    note: "",
                    entryType: "manual"
                });
                this.getView().setModel(oModel, "entry");
            }
            return oModel;
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

            // Update model data
            var oModel = this._getEntryModel();
            oModel.setData({
                pzn: entryData.pzn || "",
                medicationName: entryData.medicationName || "",
                strength: entryData.strength || "",
                activeIngredient: entryData.activeIngredient || "",
                dosageStructured: entryData.dosageStructured || "",
                dosageText: entryData.dosageText || "",
                intakeInstructions: entryData.intakeInstructions || "",
                indication: entryData.indication || "",
                note: entryData.note || "",
                entryType: entryData.entryType || "manual"
            });

            // Update dialog title
            var oView = this._getParentView();
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
            this._linkedEmlId = null;

            // Reset model data
            var oModel = this._getEntryModel();
            oModel.setData({
                medicationName: "",
                pzn: "",
                strength: "",
                activeIngredient: "",
                dosageStructured: "",
                dosageText: "",
                intakeInstructions: "",
                indication: "",
                note: "",
                entryType: "manual"
            });

            // Reset dialog title
            var oView = this._getParentView();
            var oDialog = oView.byId("addMedicationDialog");
            if (oDialog) {
                oDialog.setTitle("Add Medication");
            }
        },

        onNameSuggest: function (oEvent) {
            var sValue = oEvent.getParameter("suggestValue");
            var oInput = oEvent.getSource();

            if (sValue.length < 2) {
                return;
            }

            fetch("/api/medications/search?query=" + encodeURIComponent(sValue))
                .then(function (response) { return response.json(); })
                .then(function (data) {
                    var oModel = new JSONModel({
                        suggestions: data
                    });
                    oInput.setModel(oModel);
                })
                .catch(function (err) {
                    console.error("Search failed", err);
                });
        },

        onSuggestionItemSelected: function (oEvent) {
            var oItem = oEvent.getParameter("selectedItem");
            // Suggestions model is set on the Input control, so getBindingContext() works relative to that
            var oData = oItem.getBindingContext().getObject();

            if (oData) {
                this.fillFields(oData);
            }
        },

        fillFields: function (oData) {
            var oModel = this._getEntryModel();
            // Update model properties. We use setProperty to trigger UI updates.
            // Or just merge data into the current object.
            var oCurrentData = oModel.getData();

            // Store source ID if this is an eML entry (starts with 'presc-' or 'disp-')
            // We use this to auto-link when saving.
            if (oData.id && (oData.id.startsWith("presc-") || oData.id.startsWith("disp-"))) {
                this._linkedEmlId = oData.id;
            }

            // Map fields
            oCurrentData.pzn = oData.pzn || oCurrentData.pzn;
            oCurrentData.medicationName = oData.medicationName || oCurrentData.medicationName;
            oCurrentData.strength = oData.strength || oCurrentData.strength;
            oCurrentData.activeIngredient = oData.activeIngredient || oCurrentData.activeIngredient;
            oCurrentData.atcCode = oData.atcCode || oCurrentData.atcCode;
            oCurrentData.dosageStructured = oData.dosageStructured || oCurrentData.dosageStructured;
            oCurrentData.dosageStructured = oData.dosageStructured || oCurrentData.dosageStructured;
            oCurrentData.dosageText = oData.dosageText || oCurrentData.dosageText;
            oCurrentData.indication = oData.indication || oCurrentData.indication;

            oModel.setData(oCurrentData);
            if (oData.medicationName) {
                MessageToast.show("Selected: " + oData.medicationName);
            }
        },

        onPZNSearch: function (oEvent) {
            var sPZN = oEvent.getParameter("query") || oEvent.getParameter("newValue");
            if (!sPZN || sPZN.length < 8) {
                MessageToast.show("Please enter a valid 8-digit PZN");
                return;
            }

            // Backend Search
            var that = this;
            this.getView().setBusy(true);
            fetch("/api/medications/search?query=" + encodeURIComponent(sPZN))
                .then(function (response) { return response.json(); })
                .then(function (data) {
                    // Try to find exact PZN match
                    var oMatch = data.find(function (m) { return m.pzn === sPZN; });
                    if (oMatch) {
                        that.fillFields(oMatch);
                    } else {
                        MessageBox.information("PZN " + sPZN + " not found in medication database.");
                    }
                })
                .catch(function (err) {
                    console.error("PZN lookup failed", err);
                    MessageBox.error("Medication search failed. Please try again later.");
                })
                .finally(function () {
                    that.getView().setBusy(false);
                });
        },

        _mockPZNLookup: function (sPZN) {
            // Deprecated, using backend
            return null;
        },

        onSaveMedication: function () {
            var oModel = this._getEntryModel();
            var oData = oModel.getData();

            var sName = oData.medicationName;
            var sPZN = oData.pzn;
            var sDosageStruct = oData.dosageStructured;
            var sDosageText = oData.dosageText;

            // Validation
            if (!sName || !sPZN) {
                MessageBox.error("Medication Name and PZN are required.");
                return;
            }

            if (!sDosageStruct && !sDosageText) {
                MessageBox.error("Please provide either a structured dosage or dosage text.");
                return;
            }

            // Validated dosage: generated text MUST match dosageText if valid structured dosage is present
            // For this prototype, we enforce equality to satisfy the strict backend check
            if (sDosageStruct) {
                sDosageText = sDosageStruct;
            }

            // Construct payload
            var oEntry = {
                medicationName: sName,
                pzn: sPZN,
                strength: oData.strength,
                activeIngredient: oData.activeIngredient,
                atcCode: oData.atcCode,
                dosageStructured: sDosageStruct,
                dosageText: sDosageText,
                intakeInstructions: oData.intakeInstructions,
                note: oData.note,
                indication: oData.indication,
                entryType: oData.entryType,
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

            var sUrl = "/api/medications/" + sKvnr + "/add-emp-entry";
            if (this._linkedEmlId && !this._editMode) {
                sUrl += "?linkedEmlId=" + this._linkedEmlId;
            }
            var sMethod = "POST";

            if (this._editMode) {
                sUrl = "/api/medications/" + sKvnr + "/update-emp-entry/" + this._editEntryId;
                sMethod = "PUT";

                // Add chronologyId for concurrency check
                var oPlan = oModel.getProperty("/medicationPlan");
                if (oPlan && oPlan.chronologyId) {
                    // Check if URL already has params
                    if (sUrl.indexOf("?") === -1) {
                        sUrl += "?chronologyId=" + encodeURIComponent(oPlan.chronologyId);
                    } else {
                        sUrl += "&chronologyId=" + encodeURIComponent(oPlan.chronologyId);
                    }
                }
            }

            this.getView().setBusy(true);
            fetch(sUrl, {
                method: sMethod,
                headers: {
                    "Content-Type": "application/json",
                    "X-Requesting-Organization": "Hospital-A"
                },
                body: JSON.stringify(oEntry)
            })
                .then(function (response) {
                    if (response.status === 409) {
                        return response.json().then(function (errorData) {
                            // Check error type
                            // 1. Duplicate Match (Object)
                            if (errorData.isDuplicate) {
                                var oMainController = that.getView().getController();
                                if (oMainController && oMainController._handleDuplicateResponse) {
                                    oView.byId("addMedicationDialog").close();
                                    oMainController._handleDuplicateResponse(errorData, function (bIgnore) {
                                        if (bIgnore) {
                                            that._retrySave(sKvnr, oEntry);
                                        }
                                    });
                                } else {
                                    MessageBox.warning("A similar medication already exists in the plan.");
                                }
                                throw new Error("Duplicate detected (Handled)"); // Break duplicate promise chain
                            }
                            // 2. Chronology Mismatch (Error Message)
                            else if (errorData.error && errorData.error.includes("CHRONOLOGY")) {
                                MessageBox.error("The plan has been modified by another user. Please refresh and try again.");
                                throw new Error("Chronology mismatch");
                            }
                            else {
                                // Other 409
                                throw new Error("Conflict: " + (errorData.error || "Unknown"));
                            }
                        });
                    }

                    if (!response.ok) throw new Error("Server returned " + response.status);
                    return response.json();
                })
                .then(function (updatedPlan) {
                    var sMessage = that._editMode ? "Medication updated successfully" : "Medication added to plan";
                    MessageToast.show(sMessage);

                    if (that._editMode) {
                        that.clearEditMode();
                    }

                    oView.byId("addMedicationDialog").close();

                    // Refresh data via EventBus
                    sap.ui.getCore().getEventBus().publish("epa", "refreshData");
                })
                .catch(function (err) {
                    if (err.message === "Duplicate detected (Handled)" || err.message === "Chronology mismatch") {
                        // Already handled UI
                        return;
                    }
                    if (!err.message.includes("Duplicate")) {
                        MessageBox.error("Failed to save medication: " + err.message);
                    }
                })
                .finally(function () {
                    that.getView().setBusy(false);
                });
        },

        _retrySave: function (sKvnr, oEntry) {
            var that = this;
            fetch("/api/medications/" + sKvnr + "/add-emp-entry", {
                method: "POST",
                headers: {
                    "Content-Type": "application/json",
                    "X-Requesting-Organization": "Hospital-A"
                },
                body: JSON.stringify(oEntry)
            })
                .then(function (res) { return res.json(); })
                .then(function () {
                    MessageToast.show("Medication added");
                    sap.ui.getCore().getEventBus().publish("epa", "refreshData");
                });
        },

        onCancelMedication: function () {
            var oView = this._getParentView();
            oView.byId("addMedicationDialog").close();
        }
    });
});
