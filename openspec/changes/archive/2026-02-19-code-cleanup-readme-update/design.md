## Context

Three SAPUI5 controllers (`MedicationPlan`, `MedicationList`, `Reconciliation`) were developed independently. As a result, identical code for loading data from the backend and opening the `AddMedicationDialog` fragment has been copy-pasted into each controller. `PatientSelection.controller.js` also carries an empty stub method and four unused module imports. `AddMedicationDialog.controller.js` retains a deprecated method that was never removed. The README still lists a generic feature bullet list rather than the actual dgMP use cases the prototype covers.

## Goals / Non-Goals

**Goals:**
- Introduce a `BaseController` that holds shared helpers (`_loadMedicationList`, `_loadMedicationPlan`, `_openAddDialog`, KVNR binding delegate)
- All three feature controllers extend `BaseController` instead of repeating the shared code
- Remove all dead code identified during exploration
- Update `README.md`: add an application overview paragraph + dgMP use case coverage table (US0–US12)

**Non-Goals:**
- No behavioral or API changes
- No UI changes
- No new features
- Not refactoring `AddMedicationDialog.controller.js` or `DuplicateDialog.controller.js` internals beyond the dead method removal

## Decisions

### D1: BaseController via SAPUI5 module extension

SAPUI5 controllers use `Controller.extend()` — not ES class inheritance. `BaseController` will be a standard `sap.ui.core.mvc.Controller` extension exported as an AMD module.

Feature controllers then do:
```js
sap.ui.define(["epa/controller/BaseController", ...], function(BaseController, ...) {
    return BaseController.extend("epa.controller.MedicationPlan", { ... });
});
```

**Alternative considered**: A plain JS object mixin merged into each controller. Rejected — harder to read, no prototype chain, doesn't match SAPUI5 conventions.

### D2: KVNR binding delegate pattern

The current pattern (attaching a `oModel.bindProperty("/currentKVNR").attachChange(...)` listener inside `addEventDelegate.onBeforeRendering`) is repeated identically across three controllers. `BaseController` will provide a `_attachKvnrListener(fnCallback)` helper that each controller calls in its own `onInit`.

Each feature controller remains responsible for calling the right load function in that callback — `BaseController` does not assume which data to load.

### D3: `_openAddDialog` responsibilities

`MedicationPlan` and `Reconciliation` both lazy-load the `AddMedicationDialog` fragment independently. The shared implementation in `BaseController` will cache the fragment promise on `this._pAddMedicationDialog` (same key both controllers currently use), making the behaviour identical but defined once.

**Dependency injection**: The dialog controller needs `getView()` patched in at load time. This patch (`oDialogController.getView = function() { return that.getView(); }`) stays in `BaseController._getAddMedicationDialog()`.

### D4: Dead code removal — no feature flags needed

The removed items (`_mockPZNLookup`, `_addToRecentPatients`, unused imports, commented-out toasts, stale Java comments) are completely unreachable. No deprecation period or feature flags are needed.

## Risks / Trade-offs

- **Risk**: SAPUI5 prototype chain lookup — if a subcontroller accidentally shadows a `BaseController` method with the same name, the base version is silently ignored. → **Mitigation**: Use distinct, prefixed method names in `BaseController` (all `_loadX`, `_openX` naming already avoids conflict with common lifecycle names).
- **Risk**: `_pAddMedicationDialog` cache key collision if a future controller opens two different dialogs. → **Mitigation**: The cache is per-controller instance (stored on `this`), so there is no cross-controller collision. Acceptable for current scope.
- **Trade-off**: The KVNR delegate helper slightly reduces observability of the binding lifecycle in each controller. Offset by less code to maintain and a single place to fix if the pattern needs changing.
