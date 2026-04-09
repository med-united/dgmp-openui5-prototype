# Senior Code Review: `fhir-data-layer` Change

Review of the current codebase against the change proposal. Perspective: a senior engineer evaluating migration feasibility, technical risk, and code quality.

---

## 1. Architecture Assessment — Current State

### 1.1 Stack Overview

| Layer | Technology | Key Files |
|-------|-----------|-----------|
| Server | Quarkus 3.11, JAX-RS, Jackson | `MedicationResource.java`, `MedicationService.java` (49KB), `PatientResource.java` |
| Client | OpenUI5 (unversioned CDN), `JSONModel`, raw `fetch()` | 7 controllers, `models.js`, `formatter.js` |
| Data | Static JSON fixtures | `fixtures/medication-{plan,list}-X123456789.json`, `patients.json` |
| Build | Maven, FHIR validator in `fhir-validate` profile | `pom.xml` |

### 1.2 Current Data Flow

```mermaid
sequenceDiagram
    participant View
    participant Controller
    participant JSONModel ("app")
    participant fetch()
    participant MedicationResource
    participant MedicationService
    participant Fixtures

    View->>Controller: user action
    Controller->>fetch(): GET /api/medications/plan/{kvnr}
    fetch()->>MedicationResource: HTTP GET
    MedicationResource->>MedicationService: loadMedicationPlan(kvnr)
    MedicationService->>Fixtures: read JSON file
    Fixtures-->>MedicationService: FHIR Bundle
    MedicationService-->>MedicationResource: flat Java model
    MedicationResource-->>fetch(): JSON response
    fetch()-->>Controller: JSON
    Controller->>JSONModel ("app"): setProperty("/medicationPlan", data)
    JSONModel ("app")-->>View: binding update
```

**Key observation**: `MedicationService.java` (49KB) is doing all the heavy lifting — it reads FHIR Bundles from fixtures, flattens them into Java POJOs, handles link/unlink logic, reconciliation tree building, duplicate checking, chronology management, and provenance generation. This is a **god class** that the change proposal doesn't explicitly address decomposing.

---

## 2. Migration Risk Hotspots

### 🔴 R1. `MedicationService.java` — 49KB server-side orchestrator

This file is the single biggest risk in the migration. It currently:
- Reads FHIR Bundles and flattens to Java POJOs
- Implements link/unlink with in-memory state
- Builds reconciliation trees server-side
- Manages chronology IDs
- Generates provenance
- Handles duplicate detection

**Phase 2 impact**: This entire file needs to be rewritten to expose FHIR-native endpoints. The change proposal describes new `/fhir/` routes (tasks 3.1–3.11) but doesn't acknowledge that the existing `MedicationService.java` already implements most of this business logic in a non-FHIR format. The question is: **rewrite or wrap?**

> [!IMPORTANT]
> **Recommendation**: Explicitly include a task to decompose `MedicationService.java` into: (a) a `FhirBundleAssembler` that constructs searchset/batch-response Bundles, (b) a `MedicationStateManager` that handles in-memory state mutations, and (c) operation handlers for `$link-emp`/`$unlink-emp`. The current 49KB monolith is untestable and a refactoring accident waiting to happen during Phase 2.

### 🔴 R2. OpenUI5 CDN still unversioned

[index.html:9](file:///home/dennis/git/dgmp-openui5-prototype/src/main/resources/META-INF/resources/webapp/index.html#L9):
```html
src="https://openui5.hana.ondemand.com/resources/sap-ui-core.js"
```

Task 1.1 is marked `[x]` (done) but the CDN URL is **still unversioned**. This is a Phase 1 prerequisite that directly affects the `openui5-fhir` compatibility gate (task 1.2). Either:
- The pin was reverted, or
- It was never applied

The design doc explicitly calls this a Phase 1 prerequisite (Decision 1) and the fhir-client-model spec has a SHALL requirement for it.

### 🟡 R3. Shared mutable `JSONModel("app")` — everything on one model

[PatientSelection.controller.js:15-26](file:///home/dennis/git/dgmp-openui5-prototype/src/main/resources/META-INF/resources/webapp/controller/PatientSelection.controller.js#L15-L26):
```js
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
```

The entire application shares a single `JSONModel("app")` that holds everything — patient data, medication plans, medication lists, reconciliation items, UI state flags. This is the model the change proposal replaces with:
- `FHIRModel("fhir")` — medication + patient resources
- `JSONModel("ui")` — pure UI state

**Risk**: The `_attachKvnrListener` pattern in `BaseController.js` relies on binding to `/currentKVNR` on the `"app"` model. This is the **primary coordination mechanism** between views. Phase 3 must preserve this pattern or provide an equivalent — the design doc doesn't discuss how `FHIRModel` will trigger data reloads when the KVNR changes.

> [!WARNING]
> The `_attachKvnrListener` pattern binds to `"app"` model's `/currentKVNR` property. After migration, `/currentKVNR` will live on `"ui"` model — but the `FHIRModel("fhir")` list bindings need to be re-parameterized when KVNR changes. The design doc doesn't describe this coordination. `FHIRModel` doesn't have an equivalent to `JSONModel.bindProperty()` for triggering re-reads. This needs explicit design.

### 🟡 R4. `EventBus("epa", "refreshData")` — implicit coupling

Multiple controllers subscribe to `sap.ui.getCore().getEventBus().subscribe("epa", "refreshData", ...)`:
- [MedicationPlan.controller.js:18](file:///home/dennis/git/dgmp-openui5-prototype/src/main/resources/META-INF/resources/webapp/controller/MedicationPlan.controller.js#L18)
- [MedicationList.controller.js:15](file:///home/dennis/git/dgmp-openui5-prototype/src/main/resources/META-INF/resources/webapp/controller/MedicationList.controller.js#L15)
- [Reconciliation.controller.js:26](file:///home/dennis/git/dgmp-openui5-prototype/src/main/resources/META-INF/resources/webapp/controller/Reconciliation.controller.js#L26)

After a mutation (add, edit, link, unlink), the mutating controller publishes `refreshData`, which causes all three controllers to reload their data from the server via `fetch()`. This is a **broadcast refresh** pattern.

After migration to `FHIRModel`, `submitChanges()` will automatically update the model's internal cache with the batch-response. The EventBus broadcast should be unnecessary — but only if all three views bind to the same `FHIRModel` instance and `FHIRModel` properly invalidates its bindings after a write. If it doesn't, you'll need a replacement coordination mechanism.

### 🟡 R5. Hardcoded `"Hospital-A"` org header

[MedicationList.controller.js:147](file:///home/dennis/git/dgmp-openui5-prototype/src/main/resources/META-INF/resources/webapp/controller/MedicationList.controller.js#L147):
```js
"X-Requesting-Organization": "Hospital-A"
```

This appears in 5 places across `MedicationList`, `Reconciliation`, and `AddMedicationDialog`. The change proposal correctly specifies this should come from `FHIRModel` initialization via `httpHeaders`, sourced from the `"ui"` model at session start. But note: there's no UI for the user to **select or configure** the org identifier. `PatientSelection.controller.js` doesn't set an org ID — it only sets KVNR and patient details. Will the org ID be hardcoded in `createFHIRModel()`? If so, that should be explicit.

---

## 3. Code Quality Observations

### 🟡 Q1. Duplicate `_loadMedicationPlan` in BaseController and MedicationPlan

[BaseController.js:74-90](file:///home/dennis/git/dgmp-openui5-prototype/src/main/resources/META-INF/resources/webapp/controller/BaseController.js#L74-L90) — `_loadMedicationPlan()` exists on BaseController.

[MedicationPlan.controller.js:40-63](file:///home/dennis/git/dgmp-openui5-prototype/src/main/resources/META-INF/resources/webapp/controller/MedicationPlan.controller.js#L40-L63) — `_loadMedicationPlan()` is **overridden** with nearly identical code.

The child version doesn't call `super._loadMedicationPlan()` — it re-implements the same `fetch()` logic. This is a copy-paste bug that will silently cause confusion during migration. Phase 3 task 5.2 says "remove all `fetch()` calls" from BaseController, but the MedicationPlan controller has its own copy.

### 🟡 Q2. `formatter.js` status values don't match FHIR

[formatter.js:31-36](file:///home/dennis/git/dgmp-openui5-prototype/src/main/resources/META-INF/resources/webapp/model/formatter.js#L31-L36):
```js
var statusMap = {
    "active": "Aktiv",
    "paused": "Pausiert",
    "planned": "Geplant",
    "completed": "Abgeschlossen"
};
```

FHIR `MedicationRequest.status` uses `active`, `on-hold`, `stopped`, `draft`, `completed`. The formatter maps `paused` and `planned` which are **not FHIR status values**. After migration:
- `paused` → `on-hold`
- `planned` → `draft`

The server currently maps FHIR statuses to these custom strings. After Phase 3, the formatter will receive raw FHIR statuses. Task 5.8 mentions updating `formatter.js` but doesn't call out this specific status mapping issue.

### 🟡 Q3. `AMTSSimulator.js` — purely ingredient-based, fragile matching

[AMTSSimulator.js:17-22](file:///home/dennis/git/dgmp-openui5-prototype/src/main/resources/META-INF/resources/webapp/model/AMTSSimulator.js#L17-L22):
```js
var aIngredients = aMedications.map(function (med) {
    return {
        name: med.activeIngredient ? med.activeIngredient.toLowerCase() : "",
        medicationName: med.medicationName
    };
});
```

AMTSSimulator uses `med.activeIngredient` (string matching with `.includes()`) to check drug interactions. It doesn't use ATC codes at all — it's purely string-based ingredient matching (`"ibuprofen"`, `"acetylsalicyl"`, `"ramipril"`). 

This means the KBV `ingredient` prohibition (from spec review M4) is a real blocker — without `activeIngredient`, the only remaining option is ATC-code-based interaction checking, which would require rewriting the AMTSSimulator rules. The current three rules all use ingredient names, not ATC codes.

**Recommendation**: Either (a) switch AMTS to ATC-code-based rules (e.g., "C09AA + B01AC = interaction") which is medically more correct anyway, or (b) accept that AMTS won't work with KBV_PR_ERP_Medication_PZN fixtures and acknowledge this scope reduction.

### 🔵 Q4. No Component.js — views loaded directly in index.html

[index.html:15-27](file:///home/dennis/git/dgmp-openui5-prototype/src/main/resources/META-INF/resources/webapp/index.html#L15-L27):
```js
sap.ui.getCore().attachInit(function () {
    sap.ui.require(["sap/ui/core/mvc/XMLView", ...], function (XMLView) {
        XMLView.create({ viewName: "epa.view.PatientSelection" })
            .then(function (oView) { oView.placeAt("content"); });
    });
});
```

There is no `Component.js` — the app bootstraps directly by creating an XMLView. `FHIRModel` needs to be registered as a named model on a component (task 4.3: "Register `FHIRModel` as named model `"fhir"` [...] in the app component"). **There is no app component.**

> [!CAUTION]
> Phase 3 requires creating a `Component.js` first, then migrating the bootstrap to `ComponentContainer`. This is not mentioned in any task. Without a Component, you cannot register named models that propagate to all child views. The current approach of creating the model in `PatientSelection.controller.js` and setting it on its view only works because all other views are embedded as fragments/child views of `PatientSelection.view.xml` — but `FHIRModel` needs component-level registration for proper lifecycle management.

### 🔵 Q5. `addEmpEntry` endpoint URL mismatch

The `MedicationResource.java` has two different endpoint patterns for adding entries:

1. [Line 93](file:///home/dennis/git/dgmp-openui5-prototype/src/main/java/de/servicehealth/epa/medication/MedicationResource.java#L93): `POST /api/medications/plan/{kvnr}/entries` (older)
2. [Line 317](file:///home/dennis/git/dgmp-openui5-prototype/src/main/java/de/servicehealth/epa/medication/MedicationResource.java#L317): `POST /api/medications/{kvnr}/add-emp-entry` (newer, with org header)

The client uses #2 (`/add-emp-entry`). Route #1 is dead code. Similarly, `PUT /plan/{kvnr}/entries/{entryId}` (line 138) is dead — the client uses `/update-emp-entry/{entryId}` (line 338). These dead endpoints should be cleaned up before adding `/fhir/` routes to avoid confusion.

### 🔵 Q6. KVNR in URLs everywhere — privacy debt

Every current `/api/` endpoint puts the KVNR in the URL path:
- `GET /api/medications/plan/{kvnr}`
- `GET /api/medications/list/{kvnr}`
- `POST /api/medications/{kvnr}/link-emp?emlId=...&empId=...`
- `GET /api/patients/search?kvnr={kvnr}`

The change proposal correctly addresses this (Decision 11: POST-based search), but it's worth noting that the current server logs every KVNR in access logs — a compliance issue the migration explicitly solves.

---

## 4. Gap Analysis — What the Proposal Doesn't Cover

| Gap | Impact | Recommendation |
|-----|--------|----------------|
| **No `Component.js`** — app has no UI5 Component | `FHIRModel` can't be registered at component level; Phase 3 is blocked | Add task: Create `Component.js` + `manifest.json` as Phase 3 prerequisite |
| **No KVNR→FHIRModel coordination** | `_attachKvnrListener` won't trigger `FHIRModel` re-reads | Design the KVNR change → list binding re-parameterization flow |
| **`MedicationService.java` decomposition** | 49KB monolith with FHIR Bundle reading + flat model projection + business logic | Plan incremental decomposition alongside Phase 2 route addition |
| **Dead API endpoints** | Two duplicate endpoint patterns on `MedicationResource.java` | Clean up before Phase 2 to reduce confusion |
| **Reconciliation is server-side** | Current reconciliation runs on server (`GET /api/medications/reconciliation/{kvnr}`), design says it moves client-side | Verify: does Phase 3 remove the server-side reconciliation? Or keep both? |
| **No `manifest.json`** | Required for UI5 Component, model registration, routing | Create alongside `Component.js` |
| **Status value mapping** | `paused`→`on-hold`, `planned`→`draft` | Add explicit mapping to formatter migration (task 5.8) |
| **Org ID configuration** | No UI, no config — hardcoded `"Hospital-A"` | Decision needed: hardcode in `createFHIRModel()` or add session input |

---

## 5. Positive Observations

1. **Phased approach is sound** — keeping `/api/` alive during Phase 2 while adding `/fhir/` is well-designed for incremental migration.

2. **FHIR validator in Maven** — the `fhir-validate` profile with the full IG chain is properly set up and ready for CI integration.

3. **Contract tables in design.md** — the derived-array contracts (Decision 8, 9) with explicit FHIR paths are exactly the right specification artifact for a FHIR migration. They serve as the test oracle for Phase 3 unit tests.

4. **`AMTSSimulator` isolation** — keeping AMTS FHIR-unaware and feeding it a derived plain array is the correct abstraction boundary.

5. **EventBus pattern** — while the broadcast refresh is crude, it works reliably as a coordination mechanism and can be incrementally replaced.

6. **Test infrastructure** — REST-Assured is already in `pom.xml`, ready for Phase 2 contract tests.

---

## 6. Priority Actions Before Phase 2

| Priority | Action | Blocks |
|----------|--------|--------|
| **P0** | Pin OpenUI5 version in `index.html` (task 1.1 is falsely marked done) | task 1.2, all of Phase 3 |
| **P0** | Fix fixture validation (task 2.5 reopened, tasks 2.8–2.10 added) | Phase 1 gate |
| **P1** | Create `Component.js` + `manifest.json` | Phase 3 model registration |
| **P1** | Design KVNR→FHIRModel coordination pattern | Phase 3 controller rewrite |
| **P2** | Decompose `MedicationService.java` before adding `/fhir/` routes | Phase 2 sanity |
| **P2** | Decide AMTS strategy (ATC-based vs ingredient-based) | Phase 3 AMTS task (5.3) |
| **P3** | Clean up dead `/api/` endpoints | Phase 2 clarity |
| **P3** | Decide org ID source (hardcode vs. config) | Phase 3 FHIRModel init |
