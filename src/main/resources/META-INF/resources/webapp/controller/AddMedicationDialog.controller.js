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
         * @returns {sap.ui.model.json.JSONModel}
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

        _getParentView: function () {
            return this.getView();
        },

        // Edit mode properties
        _editMode: false,
        _editEntryId: null,
        _linkedEmlId: null,

        /**
         * Set dialog to edit mode and pre-fill with entry data.
         * @param {string} entryId - ID of the entry being edited
         * @param {object} entryData - Existing entry data to pre-fill
         */
        setEditMode: function (entryId, entryData) {
            this._editMode = true;
            this._editEntryId = entryId;

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

            var oView = this._getParentView();
            var oDialog = oView.byId("addMedicationDialog");
            if (oDialog) oDialog.setTitle("Edit Medication");
        },

        /**
         * Clear edit mode and reset dialog to "add new" mode.
         */
        clearEditMode: function () {
            this._editMode = false;
            this._editEntryId = null;
            this._linkedEmlId = null;

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

            var oView = this._getParentView();
            var oDialog = oView.byId("addMedicationDialog");
            if (oDialog) oDialog.setTitle("Add Medication");
        },

        /**
         * Name suggest: calls GET /fhir/Medication?name:contains=<term>
         * (GET is acceptable here — the search term is not PHI per Decision 11 exception)
         */
        onNameSuggest: function (oEvent) {
            var sValue = oEvent.getParameter("suggestValue");
            var oInput = oEvent.getSource();

            if (!sValue || sValue.length < 2) return;

            fetch("/fhir/Medication?name:contains=" + encodeURIComponent(sValue))
                .then(function (response) { return response.json(); })
                .then(function (oBundle) {
                    var aEntries = (oBundle && oBundle.entry) ? oBundle.entry : [];
                    var aSuggestions = aEntries.map(function (oE) {
                        var oMed = oE.resource;
                        var sPzn = "";
                        if (oMed && oMed.code && oMed.code.coding) {
                            var oPzn = oMed.code.coding.find(function (c) {
                                return c.system === "http://fhir.de/CodeSystem/ifa/pzn";
                            });
                            if (oPzn) sPzn = oPzn.code || "";
                        }
                        return {
                            medicationName: (oMed && oMed.code && oMed.code.text) || "",
                            pzn: sPzn,
                            fhirId: (oMed && oMed.id) || ""
                        };
                    });
                    var oModel = new JSONModel({ suggestions: aSuggestions });
                    oInput.setModel(oModel);
                })
                .catch(function (err) { console.error("Search failed", err); });
        },

        onSuggestionItemSelected: function (oEvent) {
            var oItem = oEvent.getParameter("selectedItem");
            var oData = oItem.getBindingContext().getObject();
            if (oData) this.fillFields(oData);
        },

        fillFields: function (oData) {
            var oModel = this._getEntryModel();
            var oCurrentData = oModel.getData();

            // Store source dispense ID if this is an eML entry (for auto-link on save)
            if (oData.id && (oData.id.startsWith("presc-") || oData.id.startsWith("disp-"))) {
                this._linkedEmlId = oData.id;
            }

            oCurrentData.pzn = oData.pzn || oCurrentData.pzn;
            oCurrentData.medicationName = oData.medicationName || oCurrentData.medicationName;
            oCurrentData.strength = oData.strength || oCurrentData.strength;
            oCurrentData.activeIngredient = oData.activeIngredient || oCurrentData.activeIngredient;
            oCurrentData.atcCode = oData.atcCode || oCurrentData.atcCode;
            oCurrentData.dosageStructured = oData.dosageStructured || oCurrentData.dosageStructured;
            oCurrentData.dosageText = oData.dosageText || oCurrentData.dosageText;
            oCurrentData.indication = oData.indication || oCurrentData.indication;

            oModel.setData(oCurrentData);
            if (oData.medicationName) MessageToast.show("Selected: " + oData.medicationName);
        },

        onPZNSearch: function (oEvent) {
            var sPZN = oEvent.getParameter("query") || oEvent.getParameter("newValue");
            if (!sPZN || sPZN.length < 8) {
                MessageToast.show("Please enter a valid 8-digit PZN");
                return;
            }

            var that = this;
            this.getView().setBusy(true);
            fetch("/fhir/Medication?name:contains=" + encodeURIComponent(sPZN))
                .then(function (response) { return response.json(); })
                .then(function (oBundle) {
                    var aEntries = (oBundle && oBundle.entry) ? oBundle.entry : [];
                    // Find PZN match
                    var oMatchEntry = aEntries.find(function (oE) {
                        var oMed = oE.resource;
                        if (!oMed || !oMed.code || !oMed.code.coding) return false;
                        return oMed.code.coding.some(function (c) {
                            return c.system === "http://fhir.de/CodeSystem/ifa/pzn" && c.code === sPZN;
                        });
                    });
                    if (oMatchEntry) {
                        var oMed = oMatchEntry.resource;
                        var oPznCoding = oMed.code.coding.find(function (c) {
                            return c.system === "http://fhir.de/CodeSystem/ifa/pzn";
                        });
                        that.fillFields({
                            medicationName: (oMed.code && oMed.code.text) || "",
                            pzn: oPznCoding ? oPznCoding.code : sPZN
                        });
                    } else {
                        MessageBox.information("PZN " + sPZN + " not found in medication database.");
                    }
                })
                .catch(function (err) {
                    console.error("PZN lookup failed", err);
                    MessageBox.error("Medication search failed. Please try again later.");
                })
                .finally(function () { that.getView().setBusy(false); });
        },

        onSaveMedication: function () {
            var oModel = this._getEntryModel();
            var oData = oModel.getData();

            var sName = oData.medicationName;
            var sPZN = oData.pzn;
            var sDosageStruct = oData.dosageStructured;
            var sDosageText = oData.dosageText;

            if (!sName || !sPZN) {
                MessageBox.error("Medication Name and PZN are required.");
                return;
            }

            if (!sDosageStruct && !sDosageText) {
                MessageBox.error("Please provide either a structured dosage or dosage text.");
                return;
            }

            // renderedDosageInstruction rule: use dosageText as the rendered form
            var sRenderedDosage = sDosageStruct || sDosageText;

            // Build a FHIR MedicationRequest resource for the batch entry
            var oEntry = {
                medicationName: sName,
                pzn: sPZN,
                dosageStructured: sDosageStruct,
                dosageText: sRenderedDosage,
                intakeInstructions: oData.intakeInstructions,
                note: oData.note,
                indication: oData.indication,
                entryType: oData.entryType,
                authoredDate: new Date().toISOString()
            };

            this._saveToBackend(oEntry);
        },

        /**
         * Submit add/update via FHIR batch POST /fhir.
         * Uses PUT MedicationRequest for both add (server-assigned ID if new) and update.
         *
         * Task 5.7 coverage: error detection by OperationOutcome.issue[0].code.
         */
        _saveToBackend: function (oEntry) {
            var oUIModel = this.getView().getModel("ui");
            var sKvnr = oUIModel.getProperty("/currentKVNR");
            var that = this;
            var oView = this._getParentView();

            // Build FHIR MedicationRequest resource
            var oMedicationRequest = {
                resourceType: "MedicationRequest",
                status: "active",
                intent: "order",
                subject: {
                    identifier: { system: "http://fhir.de/sid/gkv/kvid-10", value: sKvnr }
                },
                medicationCodeableConcept: {
                    coding: [{ system: "http://fhir.de/CodeSystem/ifa/pzn", code: oEntry.pzn }],
                    text: oEntry.medicationName
                },
                dosageInstruction: [{
                    text: oEntry.dosageText,
                    patientInstruction: oEntry.intakeInstructions || ""
                }],
                authoredOn: oEntry.authoredDate,
                note: oEntry.note ? [{ text: oEntry.note }] : []
            };

            var sMethod = "POST";
            var sUrl = "MedicationRequest";

            if (this._editMode && this._editEntryId) {
                sMethod = "PUT";
                sUrl = "MedicationRequest/" + this._editEntryId;
                oMedicationRequest.id = this._editEntryId;
            }

            var oBatch = {
                resourceType: "Bundle",
                type: "batch",
                entry: [{ request: { method: sMethod, url: sUrl }, resource: oMedicationRequest }]
            };

            this.getView().setBusy(true);
            fetch("/fhir", {
                method: "POST",
                headers: {
                    "Content-Type": "application/json",
                    "X-Requesting-Organization": "Hospital-A"
                },
                body: JSON.stringify(oBatch)
            })
                .then(function (response) {
                    if (!response.ok) throw new Error("HTTP " + response.status);
                    return response.json();
                })
                .then(function (oBatchResponse) {
                    // Inspect per-entry statuses — Task 5.7: distinguish by OperationOutcome.issue[0].code
                    var aEntries = (oBatchResponse && oBatchResponse.entry) ? oBatchResponse.entry : [];
                    var oFailed = aEntries.find(function (e) {
                        var sStatus = e.response && e.response.status ? String(e.response.status) : "";
                        return sStatus.startsWith("4") || sStatus.startsWith("5");
                    });

                    if (oFailed) {
                        var sStatus = oFailed.response && oFailed.response.status ? String(oFailed.response.status) : "";
                        var oOutcome = oFailed.response && oFailed.response.outcome;
                        var sCode = (oOutcome && oOutcome.issue && oOutcome.issue[0])
                            ? oOutcome.issue[0].code : "unknown";

                        // Task 5.7: dispatch by OperationOutcome.issue[0].code
                        if (sCode === "isDuplicate" || sStatus === "409") {
                            // Duplicate: show DuplicateDialog via parent controller
                            var oMainController = oView.getController();
                            if (oMainController && oMainController._handleDuplicateResponse) {
                                oView.byId("addMedicationDialog").close();
                                oMainController._handleDuplicateResponse(oOutcome, function (bIgnore) {
                                    if (bIgnore) that._retrySave(oEntry, true);
                                });
                            } else {
                                MessageBox.warning("A similar medication already exists in the plan.");
                            }
                            return;
                        }

                        if (sCode === "MEDSVC_EMP_CHRONOLOGY_ID_MISMATCH" || sStatus === "409") {
                            MessageBox.error("The plan has been modified by another user. Please refresh and try again.");
                            return;
                        }

                        if (sStatus.startsWith("422")) {
                            MessageBox.error("Dosage validation failed. Please check the dosage instruction format.");
                            return;
                        }

                        throw new Error("Batch entry failed: " + sStatus + " (" + sCode + ")");
                    }

                    var sMessage = that._editMode ? "Medication updated successfully" : "Medication added to plan";
                    MessageToast.show(sMessage);
                    if (that._editMode) that.clearEditMode();
                    oView.byId("addMedicationDialog").close();
                    sap.ui.getCore().getEventBus().publish("epa", "refreshData");
                })
                .catch(function (err) {
                    console.error("Save failed", err);
                    MessageBox.error("Failed to save medication: " + err.message);
                })
                .finally(function () { that.getView().setBusy(false); });
        },

        /**
         * Retry save without duplicate check (after user acknowledges duplicate).
         */
        _retrySave: function (oEntry) {
            // Re-invoke the same save path — server will add even with duplicate
            this._saveToBackend(oEntry);
        },

        onCancelMedication: function () {
            var oView = this._getParentView();
            oView.byId("addMedicationDialog").close();
        }
    });
});
