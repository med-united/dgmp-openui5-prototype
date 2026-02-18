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

        formatRowHighlight: function (sStatus) {
            switch (sStatus) {
                case "LINKED": return "Success";
                case "UNLINKED_UPDATE": return "Warning";
                case "ORPHAN_NEW": return "Error";
                default: return "None";
            }
        },

        onInit: function () {
            var oModel = new JSONModel({
                recentPatients: [],
                patientSelected: false,
                currentPatient: {},
                currentKVNR: "",
                medicationPlan: { entries: [] },
                medicationList: { entries: [] },
                reconciliationItems: [],
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
            var that = this;

            if (!sQuery || sQuery.trim() === "") {
                MessageToast.show("Please enter a KVNR");
                return;
            }

            if (!this._validateKVNR(sQuery)) {
                MessageBox.error("KVNR must be exactly 10 alphanumeric characters");
                return;
            }

            this.getView().getModel("app").setProperty("/patientSelected", true);
            this._addToRecentPatients(sQuery);

            // Load data
            this._setBusy(true);
            Promise.all([
                this._loadMedicationPlan(sQuery),
                this._loadMedicationList(sQuery),
                this._loadReconciliation(sQuery)
            ]).finally(function () {
                that._setBusy(false);
            });
        },

        _validateKVNR: function (sKvnr) {
            var kvnrRegex = /^[A-Z0-9]{10}$/;
            return kvnrRegex.test(sKvnr);
        },

        _addToRecentPatients: function (sKvnr) {
            this._searchPatient(sKvnr);
        },

        _searchPatient: function (sKvnr) {
            var that = this;
            var oModel = this.getView().getModel("app");

            fetch("/api/patients/search?kvnr=" + sKvnr)
                .then(function (response) {
                    if (!response.ok) {
                        throw new Error("Patient not found");
                    }
                    return response.json();
                })
                .then(function (patient) {
                    oModel.setProperty("/currentPatient", patient);
                    oModel.setProperty("/currentKVNR", patient.kvnr);

                    // Update recent list
                    var aRecent = oModel.getProperty("/recentPatients");
                    if (!aRecent.find(function (p) { return p.kvnr === patient.kvnr; })) {
                        aRecent.unshift(patient);
                        if (aRecent.length > 5) aRecent.pop();
                        oModel.setProperty("/recentPatients", aRecent);
                    }
                })
                .catch(function (error) {
                    console.error("Patient search failed", error);
                    // Minimal fallback
                    oModel.setProperty("/currentKVNR", sKvnr);
                    oModel.setProperty("/currentPatient", { firstName: "Unknown", lastName: "Patient", kvnr: sKvnr });
                });
        },

        onRecentPatientSelect: function (oEvent) {
            var oItem = oEvent.getParameter("listItem");
            var oContext = oItem.getBindingContext("app");
            var oPatient = oContext.getObject();
            var that = this;

            this.getView().byId("kvnrSearchField").setValue(oPatient.kvnr);
            this.getView().getModel("app").setProperty("/patientSelected", true);
            this.getView().getModel("app").setProperty("/currentPatient", oPatient);
            this.getView().getModel("app").setProperty("/currentKVNR", oPatient.kvnr);

            this._setBusy(true);
            Promise.all([
                this._loadMedicationPlan(oPatient.kvnr),
                this._loadMedicationList(oPatient.kvnr),
                this._loadReconciliation(oPatient.kvnr)
            ]).finally(function () {
                that._setBusy(false);
            });
        },

        _loadRecentPatients: function () {
            // Mock implementation - usually from local storage or backend
            var oModel = this.getView().getModel("app");
            // Check if we have some stored
            var aStored = [
                { kvnr: "X123456789", firstName: "Erika", lastName: "Mustermann", dateOfBirth: "1975-08-12", insuranceType: "GKV" },
                { kvnr: "Y987654321", firstName: "Max", lastName: "Beispiel", dateOfBirth: "1989-03-25", insuranceType: "GKV" },
                { kvnr: "Z555111222", firstName: "Anna", lastName: "Test", dateOfBirth: "1992-11-05", insuranceType: "PKV" }
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

        _loadMedicationPlan: function (sKvnr) {
            var that = this;
            var oModel = this.getView().getModel("app");

            return fetch("/api/medications/plan/" + sKvnr)
                .then(function (response) {
                    if (!response.ok) {
                        throw new Error("Medication plan not found");
                    }
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
                        MessageToast.show("Loaded " + medicationList.entries.length + " medication entries");
                    } else {
                        MessageToast.show("No medications found in eML");
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

        _loadReconciliation: function (sKvnr) {
            // UI5 passes event object when called from button press - ignore it
            if (sKvnr && typeof sKvnr === 'object' && sKvnr.sId) {
                sKvnr = null;
            }

            var oModel = this.getView().getModel("app");
            if (!sKvnr && oModel) {
                sKvnr = oModel.getProperty("/currentKVNR");
            }

            console.log("_loadReconciliation called with KVNR:", sKvnr);

            if (!sKvnr) {
                console.warn("No KVNR available for reconciliation");
                return;
            }

            var that = this;
            var sTimeRange = oModel.getProperty("/filterTimeRange") || "3_MONTHS";

            // Build query parameters
            var sUrl = "/api/medications/reconciliation/" + sKvnr;
            var aParams = [];
            if (sTimeRange) aParams.push("timeRange=" + sTimeRange);
            if (aParams.length > 0) sUrl += "?" + aParams.join("&");

            console.log("Fetching reconciliation from:", sUrl);

            fetch(sUrl)
                .then(function (response) {
                    console.log("Reconciliation API response status:", response.status, "for KVNR:", sKvnr);
                    if (!response.ok) {
                        throw new Error("Failed to load reconciliation items (HTTP " + response.status + ")");
                    }
                    return response.json();
                })
                .then(function (items) {
                    if (items.length === 0) {
                        MessageToast.show("No reconciliation items found");
                    } else {
                        // MessageToast.show("Loaded " + items.length + " reconciliation items");
                    }

                    // Map backend data to frontend
                    var mappedItems = items.map(function (item) {
                        return Object.assign({}, item, {
                            // Ensure status is valid or default to ORPHAN_NEW
                            status: item.status || "ORPHAN_NEW",
                            markedAsError: false
                        });
                    });

                    oModel.setProperty("/reconciliationItems", mappedItems);
                    that._applyClientFilters(); // Apply client-side filters like 'Show Linked'
                })
                .catch(function (error) {
                    console.error("Failed to load reconciliation:", error);
                    oModel.setProperty("/reconciliationItems", []);
                });
        },

        onTimeFilterChange: function (oEvent) {
            var sKey = oEvent.getParameter("selectedItem").getKey();
            this.getView().getModel("app").setProperty("/filterTimeRange", sKey);
            this._loadReconciliation();
        },

        onShowLinkedToggle: function (oEvent) {
            var bShow = oEvent.getParameter("selected");
            this.getView().getModel("app").setProperty("/filterShowLinked", bShow);
            this._applyClientFilters();
        },

        _applyClientFilters: function () {
            var oModel = this.getView().getModel("app");
            var bShowLinked = oModel.getProperty("/filterShowLinked");
            var aItems = oModel.getProperty("/reconciliationItems") || [];

            var oTable = this.getView().byId("reconciliationTree");
            console.log("DEBUG: _applyClientFilters - Table found:", !!oTable, "ID:", oTable ? oTable.getId() : "null");
            console.trace("DEBUG: _applyClientFilters trace");

            var aModelItems = oModel.getProperty("/reconciliationItems");
            console.log("DEBUG: /reconciliationItems in model:", aModelItems ? aModelItems.length : "null/undefined");

            if (oTable) {
                var oBinding = oTable.getBinding("rows");
                console.log("DEBUG: _applyClientFilters - Binding found:", !!oBinding, "Length:", oBinding ? oBinding.getLength() : "N/A");

                var aFilters = [];

                if (!bShowLinked) {
                    console.log("DEBUG: Adding filter status != LINKED");
                    aFilters.push(new sap.ui.model.Filter("status", sap.ui.model.FilterOperator.NE, "LINKED"));
                }

                // Filter 'markedAsError'
                aFilters.push(new sap.ui.model.Filter("markedAsError", sap.ui.model.FilterOperator.NE, true));

                console.log("DEBUG: Applying filters:", aFilters);
                oBinding.filter(aFilters);

                // Update No Data Text
                var iHiddenLinked = 0;
                if (!bShowLinked) {
                    iHiddenLinked = aItems.filter(function (item) { return item.status === "LINKED"; }).length;
                }

                if (iHiddenLinked > 0) {
                    oTable.setNoData("No data (" + iHiddenLinked + " Linked hidden)");
                } else {
                    oTable.setNoData("No data");
                }
            }
        },

        onMarkAsError: function (oEvent) {
            var oContext = oEvent.getSource().getBindingContext("app");
            var oItem = oContext.getObject();

            // Soft delete (hide) by setting a flag in the model
            oContext.getModel().setProperty(oContext.getPath() + "/markedAsError", true);

            // Re-apply filters to hide it
            this._applyClientFilters();
            MessageToast.show("Entry marked as error and hidden.");
        },

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

        onAddMedication: function () {
            var that = this;
            var oModel = this.getView().getModel("app");
            var sKvnr = oModel.getProperty("/currentKVNR");

            // Load eML first
            this._loadMedicationList(sKvnr).then(function () {
                // Open Selection Dialog
                that._getMedicationSelectionDialog().then(function (oDialog) {
                    oDialog.open();
                });
            });
        },

        // Helper for Medication Selection Dialog
        _getMedicationSelectionDialog: function () {
            var that = this;
            return new Promise(function (resolve) {
                if (!that._pMedicationSelectionDialog) {
                    that._pMedicationSelectionDialog = Fragment.load({
                        id: that.getView().getId(),
                        name: "epa.view.MedicationSelectionDialog",
                        controller: that
                    }).then(function (oDialog) {
                        that.getView().addDependent(oDialog);
                        return oDialog;
                    });
                }
                resolve(that._pMedicationSelectionDialog);
            });
        },

        onSelectionCancel: function () {
            this.byId("medicationSelectionDialog").close();
        },

        onManualEntry: function () {
            this.byId("medicationSelectionDialog").close();
            this._openAddDialog();
        },

        onMedicationSelectionChange: function (oEvent) {
            var oItem = oEvent.getParameter("listItem");
            var oContext = oItem.getBindingContext("app");
            var oEntry = oContext.getObject();

            this.byId("medicationSelectionDialog").close();
            this._openAddDialog(oEntry);
        },

        onMedicationHistorySearch: function (oEvent) {
            var sQuery = oEvent.getParameter("query");
            var oFilter = new sap.ui.model.Filter("medicationName", sap.ui.model.FilterOperator.Contains, sQuery);
            var oBinding = this.byId("selectionTable").getBinding("items");
            oBinding.filter([oFilter]);
        },

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
            });
        },

        _handleDuplicateResponse: function (match, retryCallback) {
            var oView = this.getView();
            var that = this;

            // Load duplicate dialog if needed
            if (!this._pDuplicateDialog) {
                this._pDuplicateDialog = Fragment.load({
                    id: oView.getId(),
                    name: "epa.view.DuplicateDialog",
                    controller: this
                }).then(function (oDialog) {
                    oView.addDependent(oDialog);
                    return oDialog;
                });
            }

            this._pDuplicateDialog.then(function (oDialog) {
                // Set data
                var oDuplicateModel = new JSONModel({
                    existingEntry: match.existingEntry,
                    newEntry: {
                        medicationName: oView.byId("inputMedicationName").getValue(),
                        pzn: oView.byId("inputPZN").getValue(),
                        dosageStructured: oView.byId("inputDosageStructured").getValue(),
                        dosageText: oView.byId("inputDosageText").getValue()
                    }
                });
                oDialog.setModel(oDuplicateModel, "duplicate");

                // Store retry callback
                that._retryAddEntry = retryCallback;

                oDialog.open();
            });
        },

        onConfirmDuplicate: function () {
            // User chose to create new entry (ignore duplicates)
            var oDialog = this.byId("duplicateDialog");
            oDialog.close();

            if (this._retryAddEntry) {
                this._retryAddEntry(true); // Retry with ignoreDuplicates=true
                this._retryAddEntry = null;
            }
        },

        onCancelDuplicate: function () {
            var oDialog = this.byId("duplicateDialog");
            oDialog.close();
            this._retryAddEntry = null;
        },

        // Dialog Actions (Duplicate Dialog)
        onDuplicateUpdate: function () {
            if (this._retryAddEntry) {
                // Close dialog first
                this.byId("duplicateDialog").close();
                this._retryAddEntry(true);
            }
        },

        onDuplicateCreateNew: function () {
            if (this._retryAddEntry) {
                this.byId("duplicateDialog").close();
                this._retryAddEntry(true);
            }
        },

        onDuplicateCancel: function () {
            this.byId("duplicateDialog").close();
        },

        onExportPDF: function () {
            MessageToast.show("Export PDF action triggered");
        },

        onAMTSCheck: function () {
            // 1. Get current medication plan
            var oModel = this.getView().getModel("app");
            var oPlan = oModel.getProperty("/medicationPlan");

            if (!oPlan || !oPlan.entries || oPlan.entries.length === 0) {
                MessageToast.show("No medications in plan to check.");
                return;
            }

            // 2. Filter for active medications
            var aActiveMeds = oPlan.entries.filter(function (m) {
                return m.status === "active";
            });

            if (aActiveMeds.length < 2) {
                MessageToast.show("Need at least 2 active medications to check for interactions.");
                return;
            }

            // 3. Run simulation
            var aWarnings = AMTSSimulator.checkInteractions(aActiveMeds);

            // 4. Display results
            if (aWarnings.length === 0) {
                MessageBox.success("AMTS Check: No interactions found.", {
                    title: "Safety Check Passed"
                });
            } else {
                // Build warning message
                var sMessage = "Found " + aWarnings.length + " potential interaction(s):\n\n";
                aWarnings.forEach(function (w) {
                    sMessage += "• " + w.title + "\n" + w.description + "\n\n";
                });

                MessageBox.warning(sMessage, {
                    title: "Safety Check Warnings",
                    styleClass: "sapUiResponsivePadding--header sapUiResponsivePadding--content sapUiResponsivePadding--footer"
                });
            }
        },


        onEditMedication: function (oEvent) {
            var oItem = oEvent.getSource().getParent().getParent();
            var oContext = oItem.getBindingContext("app");
            var oEntry = oContext.getObject();
            var that = this;

            this._getAddMedicationDialog().then(function (oDialog) {
                if (that._oAddMedicationController) {
                    that._oAddMedicationController.setEditMode(oEntry.id, oEntry);
                }
                oDialog.open();
            });
        },

        onPauseMedication: function (oEvent) {
            var oItem = oEvent.getSource().getParent().getParent();
            var oContext = oItem.getBindingContext("app");
            var oEntry = oContext.getObject();
            var sKvnr = this.getView().getModel("app").getProperty("/currentKVNR");
            var that = this;

            var sNewStatus = oEntry.status === 'active' ? 'paused' : 'active';
            var sAction = sNewStatus === 'paused' ? 'Pause' : 'Reactivate';

            // Optimistic update
            var sOldStatus = oEntry.status;
            oContext.getModel().setProperty(oContext.getPath() + "/status", sNewStatus);
            this._setBusy(true);
            fetch("/api/medications/plan/" + sKvnr + "/entries/" + oEntry.id + "/status?status=" + sNewStatus, {
                method: "PATCH"
            })
                .then(function (response) {
                    if (!response.ok) {
                        throw new Error("Failed to update status");
                    }
                    return response.json();
                })
                .then(function (updatedPlan) {
                    MessageToast.show(sAction + " successful for " + oEntry.medicationName);
                    // Refresh plan from backend to ensure consistency (version, timestamp)
                    oContext.getModel().setProperty("/medicationPlan", updatedPlan);
                })
                .catch(function (error) {
                    console.error("Status update failed", error);
                    MessageBox.error("Failed to " + sAction.toLowerCase() + " medication: " + error.message);
                    // Revert optimistic update
                    oContext.getModel().setProperty(oContext.getPath() + "/status", sOldStatus);
                })
                .finally(function () {
                    that._setBusy(false);
                });
        },

        onDeleteMedication: function (oEvent) {
            var oItem = oEvent.getSource().getParent().getParent();
            var oContext = oItem.getBindingContext("app");
            var oEntry = oContext.getObject();
            var sKvnr = this.getView().getModel("app").getProperty("/currentKVNR");
            var that = this;

            MessageBox.confirm("Are you sure you want to delete " + oEntry.medicationName + "? This cannot be undone.", {
                icon: MessageBox.Icon.WARNING,
                title: "Delete Medication",
                actions: [MessageBox.Action.DELETE, MessageBox.Action.CANCEL],
                onClose: function (sAction) {
                    if (sAction === MessageBox.Action.DELETE) {
                        that._setBusy(true);
                        fetch("/api/medications/plan/" + sKvnr + "/entries/" + oEntry.id, {
                            method: "DELETE"
                        })
                            .then(function (response) {
                                if (!response.ok) {
                                    throw new Error("Failed to delete entry");
                                }
                                MessageToast.show("Deleted " + oEntry.medicationName);
                                // Reload plan
                                return that._loadMedicationPlan(sKvnr);
                            })
                            .catch(function (error) {
                                console.error("Delete failed", error);
                                MessageBox.error("Failed to delete medication: " + error.message);
                            })
                            .finally(function () {
                                that._setBusy(false);
                            });
                    }
                }
            });
        },

        onLinkPress: function (oEvent) {
            var oItem = oEvent.getSource().getParent().getParent().getParent().getParent(); // Helper method nesting?
            // The source is the button in HBox in VBox in HBox in CustomListItem... ui5 structure can be deep
            // Let's use getBindingContext directly from source
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
                    // Filter out paused/completed? Maybe not, you might want to link to a paused one.
                    // But probably only active makes most sense for now.
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

            fetch("/api/medications/link?emlId=" + oEmlEntry.id + "&empId=" + oEmpEntry.id, {
                method: "POST"
            })
                .then(function (response) {
                    if (!response.ok) throw new Error("Failed to create link");
                    MessageToast.show("Linked to " + oEmpEntry.medicationName);
                    that._loadMedicationList(sKvnr); // Refresh list to show link status
                    that._loadMedicationPlan(sKvnr); // Refresh plan to show history indicator
                })
                .catch(function (error) {
                    console.error("Link failed", error);
                    MessageToast.show("Failed to create link");
                });
        },

        onUnlinkEntry: function (oEmlEntry) {
            var that = this;
            var sKvnr = this.getView().getModel("app").getProperty("/currentKVNR");

            fetch("/api/medications/link?emlId=" + oEmlEntry.id, {
                method: "DELETE"
            })
                .then(function (response) {
                    if (!response.ok) throw new Error("Failed to remove link");
                    MessageToast.show("Unlinked");
                    that._loadMedicationList(sKvnr); // Refresh list
                    that._loadMedicationPlan(sKvnr); // Refresh plan
                })
                .catch(function (error) {
                    console.error("Unlink failed", error);
                    MessageToast.show("Failed to remove link");
                });
        },

        onShowLinkedHistory: function (oEvent) {
            var oSource = oEvent.getSource();
            var oContext = oSource.getBindingContext("app");
            var oEmpEntry = oContext.getObject();
            var oModel = this.getView().getModel("app");

            // Get all eML entries
            var aEmlEntries = oModel.getProperty("/medicationList/entries") || [];

            // Filter to only linked entries
            var aLinkedEntries = aEmlEntries.filter(function (entry) {
                return oEmpEntry.linkedEmlIds && oEmpEntry.linkedEmlIds.includes(entry.id);
            });

            if (aLinkedEntries.length === 0) {
                MessageToast.show("No linked history entries found");
                return;
            }

            // Create message with linked entries
            var sMessage = "Linked History (" + aLinkedEntries.length + "):\n\n";
            aLinkedEntries.forEach(function (entry) {
                sMessage += "• " + entry.medicationName + "\n";
                sMessage += "  " + entry.entryType + " - " + (entry.authoredDate || "No date") + "\n\n";
            });

            MessageBox.information(sMessage, {
                title: "Linked eML Entries for " + oEmpEntry.medicationName
            });
        },



        onAddToPlan: function (oEvent) {
            var oItem = oEvent.getSource().getParent();
            var oContext = oItem.getBindingContext("app");
            var oReconcileItem = oContext.getObject();
            var oEmlEntry = oReconcileItem.emlEntry;

            this._openAddDialog(oEmlEntry);
        },

        // Enhanced reconciliation actions
        onAddFromReconciliation: function (oEvent) {
            var oButton = oEvent.getSource();
            var oContext = oButton.getBindingContext("app");
            var oReconcileItem = oContext.getObject();
            var oEmlEntry = oReconcileItem.emlEntry;

            // Show dialog with prefilled data from eML
            this._openAddDialog(oEmlEntry);
        },

        onLinkFromReconciliation: function (oEvent) {
            var oButton = oEvent.getSource();
            var oContext = oButton.getBindingContext("app");
            var oReconcileItem = oContext.getObject();
            var oEmlEntry = oReconcileItem.emlEntry;
            var oEmpEntry = oReconcileItem.empEntry; // The probable match

            if (!oEmpEntry) {
                MessageToast.show("No match found to link.");
                return;
            }

            // Call backend to create link
            var that = this;
            var kvnr = this.getView().getModel("app").getProperty("/currentPatient/kvnr");

            fetch(`/api/medications/link?kvnr=${kvnr}&emlId=${oEmlEntry.id}&empId=${oEmpEntry.id}`, {
                method: 'POST'
            })
                .then(response => {
                    if (response.ok) {
                        MessageToast.show("Linked eML entry to plan entry");
                        that._loadReconciliation(kvnr);
                        that._loadMedicationList(kvnr);
                        that._loadMedicationPlan(kvnr);
                    } else {
                        MessageBox.error("Failed to create link");
                    }
                })
                .catch(error => {
                    MessageBox.error("Error linking entries: " + error.message);
                });
        },

        // TreeTable Actions
        onAddToPlanFromTree: function (oEvent) {
            var oContext = oEvent.getSource().getBindingContext("app");
            if (!oContext) return;

            var oGroup = oContext.getObject();
            var oEntry = oGroup.emlEntry || oGroup.prescription;

            this._openAddDialog(oEntry);
        },

        onLinkFromTree: function (oEvent) {
            var oContext = oEvent.getSource().getBindingContext("app");
            if (!oContext) return;
            var oGroup = oContext.getObject();
            var that = this;
            var kvnr = this.getView().getModel("app").getProperty("/currentPatient/kvnr");

            // Case A: Direct matching candidate available (Probable match)
            if (oGroup.empReference) {
                var emlId = oGroup.emlEntry ? oGroup.emlEntry.id : oGroup.prescription.id;
                var empId = oGroup.empReference;

                this._executeLink(kvnr, emlId, empId);
            }
            // Case B: No candidate (Orphan) - Manual Selection
            else {
                var oView = this.getView();
                var oModel = oView.getModel("app");
                var aPlanEntries = oModel.getProperty("/medicationPlan/entries") || [];

                var aActiveEntries = aPlanEntries.filter(function (e) { return e.status === 'active'; });

                if (aActiveEntries.length === 0) {
                    MessageToast.show("No active plan entries available to link.");
                    return;
                }

                sap.ui.require(["sap/m/ActionSheet", "sap/m/Button"], function (ActionSheet, Button) {
                    var aButtons = [];
                    aActiveEntries.forEach(function (oEmpEntry) {
                        aButtons.push(new Button({
                            text: oEmpEntry.medicationName + " (" + (oEmpEntry.dosageStructured || oEmpEntry.dosageText || "-") + ")",
                            icon: "sap-icon://chain-link",
                            press: function () {
                                var emlId = oGroup.emlEntry ? oGroup.emlEntry.id : oGroup.prescription.id;
                                that._executeLink(kvnr, emlId, oEmpEntry.id);
                            }
                        }));
                    });

                    var oActionSheet = new ActionSheet({
                        title: "Link '" + oGroup.medicationName + "' to:",
                        showCancelButton: true,
                        buttons: aButtons
                    });
                    oView.addDependent(oActionSheet);
                    oActionSheet.openBy(oEvent.getSource());
                });
            }
        },

        _executeLink: function (kvnr, emlId, empId) {
            var that = this;
            fetch(`/api/medications/link?kvnr=${kvnr}&emlId=${emlId}&empId=${empId}`, {
                method: 'POST'
            })
                .then(response => {
                    if (response.ok) {
                        MessageToast.show("Linked successfully");
                        that._loadReconciliation(kvnr);
                        that._loadMedicationList(kvnr);
                        that._loadMedicationPlan(kvnr);
                    } else {
                        MessageBox.error("Failed to create link");
                    }
                })
                .catch(error => {
                    MessageBox.error("Error linking: " + error.message);
                });
        },

        onUnlinkFromTree: function (oEvent) {
            var oContext = oEvent.getSource().getBindingContext("app");
            if (!oContext) return;
            var oGroup = oContext.getObject();

            var that = this;
            var kvnr = this.getView().getModel("app").getProperty("/currentPatient/kvnr");
            var emlId = oGroup.emlEntry ? oGroup.emlEntry.id : oGroup.prescription.id;

            fetch(`/api/medications/link?kvnr=${kvnr}&emlId=${emlId}`, {
                method: 'DELETE'
            })
                .then(response => {
                    if (response.ok) {
                        MessageToast.show("Unlinked entry");
                        that._loadReconciliation(kvnr);
                        that._loadMedicationList(kvnr);
                        that._loadMedicationPlan(kvnr);
                    } else {
                        MessageBox.error("Failed to remove link");
                    }
                })
                .catch(error => {
                    MessageBox.error("Error unlinking: " + error.message);
                });
        },

        _formatEntryTypeIcon: function (entryType) {
            return entryType === "prescription" ? "sap-icon://document" : "sap-icon://product";
        },

        _formatDateTime: function (dateTimeStr) {
            if (!dateTimeStr) return "";
            var date = new Date(dateTimeStr);
            return date.toLocaleDateString() + " " + date.toLocaleTimeString();
        },

        onMedicationRowSelect: function (oEvent) {
            var oContext;

            // Handle sap.m.List selection
            var oListItem = oEvent.getParameter("listItem");
            if (oListItem) {
                oContext = oListItem.getBindingContext("app");
            }

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
        }
    });
});
