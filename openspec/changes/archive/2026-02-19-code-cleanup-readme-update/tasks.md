## 1. Dead Code Removal

- [x] 1.1 Delete `_mockPZNLookup()` method from `AddMedicationDialog.controller.js`
- [x] 1.2 Delete empty `_addToRecentPatients()` stub from `PatientSelection.controller.js`
- [x] 1.3 Remove unused imports from `PatientSelection.controller.js`: `AMTSSimulator`, `TreeTable`, `Column`, `RowSettings`
- [x] 1.4 Remove commented-out `MessageToast.show(...)` lines from `MedicationList.controller.js`
- [x] 1.5 Remove stale `linkMap`/`unlinkedSet` comment block from `MedicationService.java`

## 2. BaseController — Shared Helpers

- [x] 2.1 Create `webapp/controller/BaseController.js` extending `sap.ui.core.mvc.Controller`
- [x] 2.2 Add `_attachKvnrListener(fnCallback)` helper: binds `/currentKVNR` property change and invokes `fnCallback(sKvnr)` on change
- [x] 2.3 Add `_loadMedicationList(sKvnr)` to `BaseController`: fetches `GET /api/medications/list/{kvnr}`, stores result at `/medicationList` on `app` model, falls back to `{ entries: [] }` on error
- [x] 2.4 Add `_loadMedicationPlan(sKvnr)` to `BaseController`: fetches `GET /api/medications/plan/{kvnr}`, stores result at `/medicationPlan` on `app` model, falls back to `{ entries: [] }` on error
- [x] 2.5 Add `_getAddMedicationDialog()` and `_openAddDialog(oPreFillData)` to `BaseController`: lazy-loads `epa.view.AddMedicationDialog` fragment, caches on `this._pAddMedicationDialog`, patches `getView` on dialog controller

## 3. Migrate Feature Controllers to BaseController

- [x] 3.1 Update `MedicationPlan.controller.js`: change `sap.ui.define` dependency from `sap/ui/core/mvc/Controller` to `epa/controller/BaseController`; extend `BaseController`; remove now-covered `_loadMedicationList`, `_getAddMedicationDialog`, `_openAddDialog`; use `_attachKvnrListener` in `onInit`
- [x] 3.2 Update `MedicationList.controller.js`: extend `BaseController`; remove duplicated `_loadMedicationList`; use `_attachKvnrListener` in `onInit`
- [x] 3.3 Update `Reconciliation.controller.js`: extend `BaseController`; remove duplicated `_loadMedicationList`, `_loadMedicationPlan`, `_openAddDialog`; use `_attachKvnrListener` in `onInit`

## 4. README Update

- [x] 4.1 Replace the generic "Features" bullet list in `README.md` with a dgMP Use Cases table (US0–US12) showing name, short description, and implementation status (✅ / ⚠️ / ❌)
- [x] 4.2 Add a short "What is this?" paragraph above the use case table describing the ePA Medication Service context, the prototype's role, and the main actors (physician, pharmacist)

## 5. Verification

- [x] 5.1 Start the application (`mvn quarkus:dev`) and verify all three tabs (eML, eMP, Reconciliation) load correctly for patient X123456789
- [x] 5.2 Verify Add Medication dialog opens from both eMP tab and Reconciliation tab
- [x] 5.3 Verify Link/Unlink works from both eML tab and Reconciliation tab
- [x] 5.4 Confirm no browser console errors related to missing BaseController module or undefined methods
