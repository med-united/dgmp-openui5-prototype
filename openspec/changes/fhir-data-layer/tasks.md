## 1. Phase 1 Prep — CDN Pin & Tooling

- [x] 1.1 Pin the OpenUI5 CDN URL in `index.html` to a specific version (target `1.120.6`) — remove the unversioned endpoint (reopened: `index.html` still loads from unversioned `openui5.hana.ondemand.com/resources/sap-ui-core.js`)
- [ ] 1.2 Verify `openui5-fhir` v2.4.0 loads correctly with the pinned OpenUI5 version (manual smoke-test: load app, check browser console for compatibility errors)
- [x] 1.3 Add FHIR validator CLI (`validator_cli.jar`) invocation to Maven `validate` phase with ePA IG package; confirm it runs on a dummy fixture file

## 2. Phase 1 — Fixture Rewriting

- [x] 2.1 Rewrite `medication-plan-X123456789.json` as FHIR `Bundle` (type `searchset`) of `MedicationRequest` resources with included `Medication` entries — include `medicationReference`, PZN/ATC codings on `Medication`, dosage, status
- [x] 2.2 Rewrite `medication-list-X123456789.json` as FHIR `Bundle` (type `searchset`) of `MedicationDispense` (mode=match) and included `MedicationRequest` (mode=include), `Medication` (mode=include), and `MedicationStatement` (mode=include, for link/unlink operation support) — include `authorizingPrescription` on dispenses, `derivedFrom` on statements
- [x] 2.3 Rewrite `patients.json` as FHIR `Bundle` (type `searchset`) of `Patient` resources — include KVNR identifier (`system=http://fhir.de/sid/gkv/kvid-10`), name, birthDate
- [x] 2.4 Update mock server `/api/` endpoint flattening logic to read resources from the new FHIR Bundle fixtures and project back to the flat JSON format the existing client expects
- [x] 2.5 Run FHIR validator on all fixture files (Phase 1 gate): confirm all pass `R4 + ePA IG` validation with zero errors — **reopened: on-disk fixtures have regressed and need re-validation**
- [x] 2.6 Smoke-test existing `/api/` endpoints: open app, select each patient, verify eMP and eML load without errors
- [x] 2.7 Fix `MedicationStatement` entries in `medication-list-X123456789.json` — change their `search.mode` from `match` to `include` (they are not display entries); set `MedicationDispense` entries to `search.mode = "match"`; add `MedicationStatement.derivedFrom` references pointing to the corresponding `MedicationDispense` entries; verify each `MedicationStatement.basedOn` correctly references the linked eMP `MedicationRequest` where applicable; re-run FHIR validator after change
- [x] 2.8 Fix `Medication` resources in both fixture files for KBV_PR_ERP_Medication_PZN conformance: add mandatory KBV extensions (Medication_Category, Medication_Vaccine, normgroesse, Medication_Type), remove `ingredient` (max=0), keep only PZN coding in `code.coding` (closed slicing), add `KBV_EX_ERP_Medication_PackagingSize` extension on `amount.numerator`, remove prohibited `Medication.text`, add `meta.profile`
- [x] 2.9 Add `medicationPlanIdentifier` (`identifier[system=https://gematik.de/fhir/sid/emp-identifier]` with UUID value) to all `MedicationRequest` resources in both fixture files
- [x] 2.10 Fix `Bundle.total` in both fixture files — must count only `search.mode = "match"` entries, not includes

## 3. Phase 2 — Mock Server FHIR Routes

- [x] 3.1 Add `GET` and `POST /_search` routes for `MedicationRequest?patient=<kvnr>` — returns `Bundle` (searchset) of eMP entries from fixture
- [x] 3.2 Add `GET` and `POST /_search` routes for `MedicationDispense?patient=<kvnr>&_include=MedicationDispense:medication-request&_revinclude=MedicationStatement:derived-from` — returns `MedicationDispense` (mode=match) with included `MedicationRequest` (mode=include, via `authorizingPrescription`), `Medication` (mode=include), and `MedicationStatement` (mode=include, via `derivedFrom`, only when `_revinclude` param present)
- [x] 3.3 Add `GET` and `POST /_search` routes for `Patient?identifier=<kvnr>` — returns matching `Patient` resource in `Bundle`
- [x] 3.4 Add `GET /fhir/Medication?name:contains=<term>` endpoint — returns `Medication` resources matching the name fragment (replaces `/api/medications/search`); GET only, no PHI in search term
- [x] 3.5 Add `POST /fhir` batch endpoint — parse incoming `Bundle` (type `batch`), dispatch each entry by operation type (PUT MedicationRequest for add/update-emp-entry, delete)
- [x] 3.5a Add `POST /fhir/MedicationStatement/:id/$link-emp` instance operation — parse `Parameters` body for `emp-entry-id`, set `MedicationStatement.basedOn → MedicationRequest/<emp-entry-id>`, return `Parameters` response with updated `MedicationStatement` + `EMPChronologyProvenance`; error: 400 `LINKING_NOT_SUCCESSFUL` / `MEDSVC_ALREADY_LINKED`, 409 `MEDSVC_EMP_CHRONOLOGY_ID_MISMATCH`
- [x] 3.5b Add `POST /fhir/MedicationStatement/:id/$unlink-emp` instance operation — remove `MedicationStatement.basedOn`, return `Parameters` response with updated `MedicationStatement` + affected `MedicationRequest` + `EPAActivityProvenance` + `EMPChronologyProvenance`; error: 400 `UNLINKING_NOT_SUCCESSFUL`, 409 `MEDSVC_EMP_CHRONOLOGY_ID_MISMATCH`
- [x] 3.6 Implement `X-Requesting-Organization` header validation on all mutating requests — return HTTP 422 `OperationOutcome` (`SVC_ORG_HEADER_PROFILE_MISMATCH`) on profile mismatch; return HTTP 431 `Request Header Fields Too Large` if header size limit exceeded
- [x] 3.7 Implement duplicate detection in batch processing — return `batch-response` entry with `response.status = "409 Conflict"` and `OperationOutcome` (`isDuplicate`) when PZN/ATC/ASK duplicate found
- [x] 3.8 Implement chronology mismatch detection — return `batch-response` entry with `response.status = "409 Conflict"` and `OperationOutcome` (`MEDSVC_EMP_CHRONOLOGY_ID_MISMATCH`) when `acknowledgedChronologyId` mismatches
- [x] 3.9 Implement dosage validation — return `batch-response` entry `response.status = "422 Unprocessable Entity"` and `OperationOutcome` (`MEDSVC_DOSAGE_INVALID`) on FHIR profile validation failure for `renderedDosageInstruction` (per ePA IG release notes: 400 → 422)
- [x] 3.10 Implement server-side Provenance generation — create `EPAActivityProvenance` and `EMPChronologyProvenance` FHIR `Provenance` resources after each successful write; include in `batch-response`; use `EMPChronologyProvenance.id` as new `chronologyId`
- [x] 3.11 Write REST-Assured contract tests for all `/fhir/` endpoints (Phase 2 gates): POST-based search returns valid Bundle, `_include=MedicationDispense:medication-request` returns linked MedicationRequest entries, batch returns `batch-response` with correct per-entry statuses

## 4. Phase 3 — openui5-fhir Integration & Tooling

- [ ] 4.0 Create `Component.js` and `manifest.json` in `webapp/`; migrate `index.html` bootstrap from direct `XMLView.create()` to `ComponentContainer`; declare `FHIRModel` (named `"fhir"`) and `JSONModel` (named `"ui"`) in `manifest.json` `sap.app.dataSources` and `sap.ui5.models` sections — this is a prerequisite for all subsequent Phase 3 tasks; `FHIRModel` POST-based search config must be declared in `manifest.json` per openui5-fhir docs
- [ ] 4.1 Add `openui5-fhir` v2.4.0 as a project dependency (npm/bower per library docs); confirm it is loadable in the UI5 bootstrap
- [ ] 4.2 Update `models.js`: replace `createMedicationsModel()` / `createAppModel()` with `createFHIRModel()` (returns `FHIRModel` at `/fhir` with `httpHeaders: { "X-Requesting-Organization": ... }` and `defaultHttpMethod: "POST"` for search) and `createUIModel()` (returns `JSONModel` for UI-only state)
- [ ] 4.3 Register `FHIRModel` as named model `"fhir"` and `JSONModel` as `"ui"` in the app component

## 5. Phase 3 — Controller & Formatter Rewrites

- [ ] 5.1 Update `PatientSelection.controller.js`: bind `Patient` search via `FHIRModel` list binding with `identifier=<kvnr>`; store selected patient KVNR and org ID in `"ui"` JSONModel
- [ ] 5.2 Update `BaseController.js`: remove all `fetch()` calls (`_loadMedicationPlan`, `_loadMedicationList`); expose `this.getFHIRModel()` helper returning `this.getModel("fhir")` for subcontrollers
- [ ] 5.3 Update `MedicationPlan.controller.js`: bind `MedicationRequest` list via `FHIRModel`; resolve `medicationReference` to included `Medication` for display bindings; rewrite all property bindings to use slicing paths on the resolved `Medication`; implement AMTS input derivation (see Decision 9 contract table — `activeIngredient` passes as `null`, see Decision 9 resolution); remove the local override of `_loadMedicationPlan()` — this method exists in both `BaseController.js` and `MedicationPlan.controller.js` (child overrides without calling `super`); task 5.2 removes the `BaseController` copy but the child copy must also be deleted here
- [ ] 5.4 Update `MedicationList.controller.js`: bind `MedicationDispense` list via `FHIRModel` list binding with `_include=MedicationDispense:medication-request&_revinclude=MedicationStatement:derived-from` and `growingThreshold`; resolve `medicationReference` to included `Medication` for display; group dispenses under their linked eMP entry by `authorizingPrescription` reference; display `substitution.wasSubstituted` indicator per ePA IG Must Support requirement; store `MedicationStatement` IDs from the bundle in the `"ui"` model keyed by `MedicationDispense.id` for use by the link/unlink button handlers
- [ ] 5.5 Update `Reconciliation.controller.js`: derive `emlEntries` from `MedicationDispense` + linked `MedicationRequest` + resolved `Medication` (see Decision 8 contract table); derive `empEntries` from `MedicationRequest` + resolved `Medication`; store in `"ui"` JSONModel; run existing soft-match algorithm unchanged
- [ ] 5.6 Update `AddMedicationDialog.controller.js`: replace `/api/medications/search` call with `FHIRModel` binding on `Medication?name:contains=<term>`; submit add/edit via `FHIRModel.createEntry`/`setProperty` + `submitChanges()`
- [ ] 5.7 Update `DuplicateDialog.controller.js` and edit-save path: subscribe to `FHIRModel.submitChanges()` error callbacks; distinguish `isDuplicate` vs `MEDSVC_EMP_CHRONOLOGY_ID_MISMATCH` vs `422` validation failure by `OperationOutcome.issue[0].code` and trigger appropriate UI response
- [ ] 5.8 Update `formatter.js`: replace flat property access (`atcCode`, `medicationName`, etc.) with FHIR path extraction that resolves `medicationReference` → `Medication.code` for name/PZN/ATC; implement dosage fallback rule (use `MedicationDispense.dosageInstruction[0].text` if present, else `MedicationRequest.dosageInstruction[0].text`); implement medication display precedence (dispense medication over prescription medication); implement `MedicationRequest.status` → UI display string mapping: `active` → "Aktiv", `on-hold` → "Pausiert", `stopped` → "Abgebrochen", `completed` → "Abgeschlossen", `cancelled` → "Storniert", `entered-in-error` → "Fehler", `draft` → "Entwurf", `unknown` → "Unbekannt" (the old flat format used "paused" where FHIR uses "on-hold")
- [ ] 5.9 Implement link/unlink handlers: invoke `$link-emp` / `$unlink-emp` FHIR operations via direct `fetch()` or `FHIRModel` custom query (see fhir-client-model spec — Operation Dispatch requirement); parse `Parameters` response

## 6. Phase 3 — Tests, Regression & Cleanup

- [ ] 6.1 Write Jest unit test: AMTS input contract — construct minimal `MedicationRequest` + `Medication` FHIR JSON, run the controller mapping function, assert all fields from Decision 9 contract table are correctly extracted (including resolved `medicationReference`)
- [ ] 6.2 Write Jest unit test: Reconciliation input contract — construct minimal `MedicationDispense` + linked `MedicationRequest` + resolved `Medication` FHIR JSON, run the controller mapping function, assert all fields from Decision 8 contract table are correctly extracted (including dosage fallback, `wasSubstituted`, and resolved medication data)
- [ ] 6.3 Run `FHIRModel` slicing path smoke test: bind a view control to `coding[system=http://www.whocc.no/atc]/code` on a live fixture resource and verify the correct ATC code value is rendered
- [ ] 6.4 Run US0–US12 regression walkthrough against `/fhir/` routes with `/api/` routes disabled — verify all use cases pass
- [ ] 6.5 Remove all `/api/` routes from the mock server
- [ ] 6.6 Run FHIR validator on fixtures one final time to confirm no drift was introduced during Phase 3
