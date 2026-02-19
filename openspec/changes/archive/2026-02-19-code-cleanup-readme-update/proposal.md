## Why

The codebase has grown organically and now contains dead code, duplicated fetch helpers copy-pasted across three controllers, and a README that doesn't reflect the implemented dgMP use cases. This change cleans up the code and brings the documentation up to date.

## What Changes

- **Remove dead code**:
  - Delete `_mockPZNLookup()` in `AddMedicationDialog.controller.js` (deprecated, returns `null`, never called)
  - Delete empty `_addToRecentPatients()` stub in `PatientSelection.controller.js`
  - Remove unused `sap.ui.define` imports in `PatientSelection.controller.js` (`AMTSSimulator`, `TreeTable`, `Column`, `RowSettings`)
  - Remove commented-out `MessageToast.show(...)` noise in `MedicationList.controller.js`
  - Remove stale implementation-note comments in `MedicationService.java`

- **Refactor duplicates**:
  - Extract `_loadMedicationList(sKvnr)` into a shared `BaseController` — currently duplicated in `MedicationPlan.controller.js` and `Reconciliation.controller.js`
  - Extract `_loadMedicationPlan(sKvnr)` similarly — duplicated in `Reconciliation.controller.js`
  - Extract `_openAddDialog(oPreFillData)` and AddMedicationDialog lazy-load pattern from both `MedicationPlan.controller.js` and `Reconciliation.controller.js` into `BaseController`
  - Extract the repeated `onInit` KVNR binding delegate pattern (same boilerplate in `MedicationPlan`, `MedicationList`, `Reconciliation`) into `BaseController`

- **Update README.md**:
  - Replace the generic feature list with a precise list of implemented dgMP use cases (US0–US12 from `spec.md`), indicating which are fully implemented, which are placeholder, and which are deferred
  - Add a short section which describes the current state of the application and what it does

## Capabilities

### New Capabilities

- `base-controller`: A shared SAPUI5 `BaseController` providing reusable helpers (`_loadMedicationList`, `_loadMedicationPlan`, `_openAddDialog`, KVNR binding delegate) that all feature controllers extend
- `readme-documentation`: README updated with the dgMP use case coverage status

### Modified Capabilities

_(no spec-level behavioral changes — this is purely structural/presentational)_

## Impact

- **Frontend**:
  - `webapp/controller/BaseController.js` — **new file**
  - `webapp/controller/MedicationPlan.controller.js` — extends `BaseController`, removes duplicated methods
  - `webapp/controller/MedicationList.controller.js` — extends `BaseController`, removes duplicated boilerplate
  - `webapp/controller/Reconciliation.controller.js` — extends `BaseController`, removes duplicated methods
  - `webapp/controller/AddMedicationDialog.controller.js` — removes `_mockPZNLookup`
  - `webapp/controller/PatientSelection.controller.js` — removes empty stub + unused imports
- **Backend**:
  - `MedicationService.java` — minor comment cleanup only
- **Docs**:
  - `README.md` — updated with dgMP use case coverage
- **No API changes, no behavioral changes, no breaking changes**
