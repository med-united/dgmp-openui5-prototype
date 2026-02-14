# OpenUI5 Implementation Notes

This document captures important implementation patterns and gotchas discovered during development of the ePA Medication UI.

## UI5 Module Loading

### Issue: Module Path and File Naming Convention

**Problem:** When using `sap.ui.require()` to load custom controllers, the module path must exactly match the file name including the `.controller` suffix.

**Symptom:**
```
ModuleError: failed to load 'epa/controller/AddMedicationDialog.js' from ./controller/AddMedicationDialog.js: script load error
```

**Root Cause:**
- File is named: `AddMedicationDialog.controller.js`
- Code was trying to load: `epa/controller/AddMedicationDialog`
- UI5 appends `.js` automatically, resulting in search for `AddMedicationDialog.js` instead of `AddMedicationDialog.controller.js`

**Solution:**
```javascript
// ❌ Wrong - missing .controller suffix
sap.ui.require(["epa/controller/AddMedicationDialog"], function (Controller) {
    // ...
});

// ✅ Correct - include .controller suffix
sap.ui.require(["epa/controller/AddMedicationDialog.controller"], function (Controller) {
    // ...
});
```

**File Reference:**
- Fixed in: [`index.html:176`](file:///home/dennis/git/fhir-prototype/src/main/resources/META-INF/resources/webapp/index.html#L176)
- Also fixed in: [`index.html:381`](file:///home/dennis/git/fhir-prototype/src/main/resources/META-INF/resources/webapp/index.html#L381)

---

## Fragment Controller Context

### Issue: Controller Cannot Access Fragment Controls with `this.byId()`

**Problem:** When a fragment is loaded with `Fragment.load()` and attached to a parent view using `addDependent()`, the fragment controls get ID-prefixed with the parent view's ID. A manually instantiated controller cannot find these controls using `this.byId()`.

**Symptom:**
```
Uncaught TypeError: can't access property "setValue", this.byId(...) is undefined
```

**Root Cause:**
1. Fragment is loaded with parent view ID prefix: `Fragment.load({ id: parentView.getId(), ... })`
2. Fragment controls get IDs like: `parentViewId--inputMedicationName`
3. Manual controller instance has no view context, so `this.byId("inputMedicationName")` returns `undefined`

**Architecture Pattern:**
```
PatientSelection View (Main)
  └─ AddMedicationDialog Fragment
       ├─ Dialog control (id: mainView--addMedicationDialog)
       ├─ Input controls (id: mainView--inputMedicationName, etc.)
       └─ Controller: manually instantiated, not part of standard UI5 view lifecycle
```

**Solution Pattern:**

In the parent view controller (`index.html`), set a reference to the parent view when creating the dialog controller:

```javascript
// Create instance of the dialog controller
var oDialogController = new AddMedicationDialogController();

// Set parent view reference so fragment controller can access it
oDialogController.getView = function () { return that.getView(); };
```

In the fragment controller ([`AddMedicationDialog.controller.js`](file:///home/dennis/git/fhir-prototype/src/main/resources/META-INF/resources/webapp/controller/AddMedicationDialog.controller.js)):

```javascript
// ❌ Wrong - this.byId() doesn't work in fragment context
onPZNSearch: function (oEvent) {
    this.byId("inputMedicationName").setValue(value);
}

// ✅ Correct - use parent view's byId()
_getParentView: function() {
    return this.getView(); // Returns parent view set in index.html
},

onPZNSearch: function (oEvent) {
    var oView = this._getParentView();
    oView.byId("inputMedicationName").setValue(value);
}
```

**All Fixed Methods:**
- `onPZNSearch` - PZN search and auto-fill
- `onSaveMedication` - Form validation and data collection
- `_saveToBackend` - Dialog close after save
- `onCancelMedication` - Dialog close on cancel

**Alternative Solutions (Not Used):**

1. **Use `sap.ui.getCore().byId()`**: Would require knowing the full prefixed ID
2. **Use data binding**: More complex, requires model setup
3. **Standard controller instantiation**: Would require a full view instead of fragment

**Files Reference:**
- Parent view setup: [`index.html:176-213`](file:///home/dennis/git/fhir-prototype/src/main/resources/META-INF/resources/webapp/index.html#L176-L213)
- Fragment controller: [`AddMedicationDialog.controller.js`](file:///home/dennis/git/fhir-prototype/src/main/resources/META-INF/resources/webapp/controller/AddMedicationDialog.controller.js)
- Fragment definition: [`AddMedicationDialog.fragment.xml`](file:///home/dennis/git/fhir-prototype/src/main/resources/META-INF/resources/webapp/view/AddMedicationDialog.fragment.xml)

---

## Best Practices Summary

### Module Loading
- **Always include the full filename in `sap.ui.require()` paths** (including `.controller`, `.fragment`, etc.)
- **File naming convention**: Use `<Name>.controller.js` for controllers, `<Name>.fragment.xml` for fragments
- **Module path**: Match the file path exactly from the resourceroots configuration

### Fragment Controllers
- **Avoid manual controller instantiation** when possible - use standard UI5 patterns
- **If manual instantiation is required**: 
  - Set parent view reference using `oController.getView = function() { return parentView; }`
  - Use `this._getParentView().byId()` pattern for control access
  - Never use `this.byId()` directly in fragment controllers
- **Consider alternatives**: Data binding, event bus, or moving logic to parent controller

### Testing Fragment Issues
1. Check browser console for `undefined` errors on control access
2. Inspect DOM to see actual prefixed control IDs
3. Use `sap.ui.getCore().byId()` with full ID to verify control exists
4. Check that fragment is properly attached to parent view with `addDependent()`

---

## Related Files

- Main view: [`index.html`](file:///home/dennis/git/fhir-prototype/src/main/resources/META-INF/resources/webapp/index.html)
- Fragment controller: [`AddMedicationDialog.controller.js`](file:///home/dennis/git/fhir-prototype/src/main/resources/META-INF/resources/webapp/controller/AddMedicationDialog.controller.js)
- Fragment XML: [`AddMedicationDialog.fragment.xml`](file:///home/dennis/git/fhir-prototype/src/main/resources/META-INF/resources/webapp/view/AddMedicationDialog.fragment.xml)
- Duplicate dialog controller: [`DuplicateDialog.controller.js`](file:///home/dennis/git/fhir-prototype/src/main/resources/META-INF/resources/webapp/controller/DuplicateDialog.controller.js)

---

*Last updated: 2026-02-14*
