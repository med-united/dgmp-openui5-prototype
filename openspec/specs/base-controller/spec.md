## ADDED Requirements

### Requirement: Shared BaseController Module
The system SHALL provide a shared `BaseController` (at `webapp/controller/BaseController.js`) that all feature controllers extend, removing duplicated helper methods.

#### Scenario: Feature controller extends BaseController
- **WHEN** `MedicationPlan.controller.js`, `MedicationList.controller.js`, or `Reconciliation.controller.js` is loaded
- **THEN** each controller SHALL extend `epa.controller.BaseController` instead of `sap.ui.core.mvc.Controller` directly

### Requirement: Shared KVNR Binding Delegate
The `BaseController` SHALL provide a reusable `onInit` helper that attaches the standard KVNR change listener so that feature controllers do not repeat this pattern.

#### Scenario: KVNR changes trigger data reload
- **WHEN** the `app` model's `/currentKVNR` property changes
- **THEN** the `BaseController` delegate SHALL invoke a `_onKvnrChange(sKvnr)` hook on the sub-controller
- **AND** each feature controller SHALL implement `_onKvnrChange` to trigger its specific data load

### Requirement: Shared Medication List Loader
The `BaseController` SHALL provide a `_loadMedicationList(sKvnr)` method that fetches `GET /api/medications/list/{kvnr}` and stores the result at `/medicationList` in the `app` model.

#### Scenario: Successful load
- **WHEN** `_loadMedicationList(sKvnr)` is called with a valid KVNR
- **THEN** the method SHALL fetch the medication list from the backend
- **AND** set the result on the shared `app` model at `/medicationList`

#### Scenario: Failed load
- **WHEN** the backend returns a non-OK response
- **THEN** the method SHALL set `{ entries: [] }` on `/medicationList` and log the error

### Requirement: Shared Medication Plan Loader
The `BaseController` SHALL provide a `_loadMedicationPlan(sKvnr)` method that fetches `GET /api/medications/plan/{kvnr}` and stores the result at `/medicationPlan` in the `app` model.

#### Scenario: Successful load
- **WHEN** `_loadMedicationPlan(sKvnr)` is called with a valid KVNR
- **THEN** the method SHALL fetch the medication plan from the backend
- **AND** set the result on the shared `app` model at `/medicationPlan`

#### Scenario: Failed load
- **WHEN** the backend returns a non-OK response
- **THEN** the method SHALL set `{ entries: [] }` on `/medicationPlan` and log the error

### Requirement: Shared AddMedicationDialog Loader
The `BaseController` SHALL provide `_getAddMedicationDialog()` and `_openAddDialog(oPreFillData)` methods encapsulating the lazy fragment-load pattern for the `AddMedicationDialog`, so this pattern is not duplicated across controllers.

#### Scenario: Dialog opened the first time
- **WHEN** `_openAddDialog()` is called and no dialog instance exists
- **THEN** the `BaseController` SHALL load `epa.view.AddMedicationDialog` fragment and cache it on `_pAddMedicationDialog`

#### Scenario: Dialog opened subsequently
- **WHEN** `_openAddDialog()` is called and the dialog is already cached
- **THEN** the `BaseController` SHALL reuse the cached dialog instance without reloading the fragment
