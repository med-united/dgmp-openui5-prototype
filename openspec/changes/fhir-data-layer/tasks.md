## 1. Phase 1 Prep — CDN Pin & Tooling

- [x] 1.1 Pin the OpenUI5 CDN URL in `index.html` to a specific version (target `1.120.6`) — remove the unversioned endpoint
- [ ] 1.2 Verify `openui5-fhir` v2.4.0 loads correctly with the pinned OpenUI5 version (manual smoke-test: load app, check browser console for compatibility errors)
- [x] 1.3 Add FHIR validator CLI (`validator_cli.jar`) invocation to Maven `validate` phase with ePA IG package; confirm it runs on a dummy fixture file

## 2. Phase 1 — Fixture Rewriting

- [x] 2.1 Rewrite `medication-plan-X123456789.json`, `medication-plan-Y987654321.json`, `medication-plan-Z555111222.json` as FHIR `Bundle` (type `searchset`) of `MedicationRequest` resources conforming to `KBV_PR_ERP_MedicationRequest` — include `medicationPlanIdentifier` identifier, PZN/ATC codings, dosage, status
- [x] 2.2 Rewrite `medication-list-X123456789.json`, `medication-list-Y987654321.json`, `medication-list-Z555111222.json` as FHIR `Bundle` (type `searchset`) of `MedicationStatement` (prescriptions) and `MedicationDispense` (dispensements, `GEM_ERP_PR_MedicationDispense`) resources — include `basedOn`/`is-emp` extension on linked statements, `authorizingPrescription` and `partOf` on dispenses
- [ ] 2.3 Rewrite `patients.json` as FHIR `Bundle` (type `searchset`) of `Patient` resources — include KVNR identifier (`system=http://fhir.de/sid/gkv/kvid-10`), name, birthDate
- [x] 2.4 Update mock server `/api/` endpoint flattening logic to read resources from the new FHIR Bundle fixtures and project back to the flat JSON format the existing client expects
- [ ] 2.5 Run FHIR validator on all fixture files (Phase 1 gate): confirm all pass `R4 + ePA IG` validation with zero errors
- [ ] 2.6 Smoke-test existing `/api/` endpoints: open app, select each patient, verify eMP and eML load without errors

## 3. Phase 2 — Mock Server FHIR Routes

- [ ] 3.1 Add `GET /fhir/MedicationRequest?patient=<kvnr>` endpoint — returns `Bundle` (searchset) from fixture
- [ ] 3.2 Add `GET /fhir/MedicationStatement?patient=<kvnr>&_include=MedicationStatement:based-on` endpoint — returns `MedicationStatement` entries with included `MedicationRequest` resources
- [ ] 3.3 Add `GET /fhir/MedicationDispense?patient=<kvnr>&_include=MedicationDispense:prescription` endpoint — returns `MedicationDispense` entries with included `MedicationRequest` resources
- [ ] 3.4 Add `GET /fhir/Patient?identifier=<kvnr>` endpoint — returns matching `Patient` resource in `Bundle`
- [ ] 3.5 Add `GET /fhir/Medication?name:contains=<term>` endpoint — returns `Medication` resources matching the name fragment (replaces `/api/medications/search`)
- [ ] 3.6 Add `POST /fhir` batch endpoint — parse incoming `Bundle` (type `batch`), dispatch each entry by operation type (PUT MedicationRequest, link-emp, unlink-emp, delete)
- [ ] 3.7 Implement `X-Requesting-Organization` header validation on all mutating requests — return HTTP 422 `OperationOutcome` (`SVC_ORG_HEADER_PROFILE_MISMATCH`) on failure
- [ ] 3.8 Implement duplicate detection in batch processing — return `batch-response` entry with `response.status = "409 Conflict"` and `OperationOutcome` (`isDuplicate`) when PZN/ATC/ASK duplicate found
- [ ] 3.9 Implement chronology mismatch detection — return `batch-response` entry with `response.status = "409 Conflict"` and `OperationOutcome` (`MEDSVC_EMP_CHRONOLOGY_ID_MISMATCH`) when `acknowledgedChronologyId` mismatches
- [ ] 3.10 Implement dosage validation — return `batch-response` entry `response.status = "400 Bad Request"` and `OperationOutcome` (`MEDSVC_DOSAGE_INVALID`) on invalid `renderedDosageInstruction`
- [ ] 3.11 Implement server-side Provenance generation — create `EPAActivityProvenance` and `EMPChronologyProvenance` FHIR `Provenance` resources after each successful write; include in `batch-response`; use `EMPChronologyProvenance.id` as new `chronologyId`
- [ ] 3.12 Write REST-Assured contract tests for all `/fhir/` endpoints (Phase 2 gates): search returns valid Bundle, `_include` returns linked resources, batch returns `batch-response` with correct per-entry statuses

## 4. Phase 3 — openui5-fhir Integration & Tooling

- [ ] 4.1 Add `openui5-fhir` v2.4.0 as a project dependency (npm/bower per library docs); confirm it is loadable in the UI5 bootstrap
- [ ] 4.2 Implement `model/FhirRepository.js` module with five methods: `read(resourceType, searchParams)`, `create(resource)`, `update(resource)`, `remove(resourceType, id)`, `submitChanges()`
- [ ] 4.3 Update `models.js`: replace `createMedicationsModel()` / `createAppModel()` with `createFHIRModel()` (returns `FHIRModel` at `/fhir` with `httpHeaders: { "X-Requesting-Organization": ... }`) and `createUIModel()` (returns `JSONModel` for UI-only state)
- [ ] 4.4 Register `FHIRModel` as named model `"fhir"` and `JSONModel` as `"ui"` in the app component

## 5. Phase 3 — Controller & Formatter Rewrites

- [ ] 5.1 Update `PatientSelection.controller.js`: fetch `Patient` resources via `FhirRepository.read("Patient", { identifier: kvnr })`; store selected patient KVNR and org ID in `"ui"` JSONModel
- [ ] 5.2 Update `BaseController.js`: remove all `fetch()` calls (`_loadMedicationPlan`, `_loadMedicationList`); provide `FhirRepository` as a shared accessor method for subcontrollers
- [ ] 5.3 Update `MedicationPlan.controller.js`: load `MedicationRequest` resources via `FhirRepository`; rewrite all property bindings to use slicing paths; implement AMTS input derivation (see Decision 9 contract table)
- [ ] 5.4 Update `MedicationList.controller.js`: bind `MedicationStatement` and `MedicationDispense` via `FHIRModel` list bindings with `growingThreshold`; assemble dispensements under their parent prescription by `authorizingPrescription` reference
- [ ] 5.5 Update `Reconciliation.controller.js`: derive `emlEntries` and `empEntries` arrays from `FhirRepository.read()` calls (see Decision 8 contract table); store in `"ui"` JSONModel; run existing soft-match algorithm unchanged
- [ ] 5.6 Update `AddMedicationDialog.controller.js`: replace `/api/medications/search` call with `FhirRepository.read("Medication", { "name:contains": term })`; submit add/edit via `FhirRepository.create/update + submitChanges()`
- [ ] 5.7 Update `DuplicateDialog.controller.js` and edit-save path: subscribe to `FhirRepository.submitChanges()` error callbacks; distinguish `isDuplicate` vs `MEDSVC_EMP_CHRONOLOGY_ID_MISMATCH` by `OperationOutcome.issue[0].code` and trigger appropriate UI response
- [ ] 5.8 Update `formatter.js`: replace flat property access (`atcCode`, `medicationName`, etc.) with FHIR `CodeableConcept`/`Dosage` path extraction to match new binding inputs

## 6. Phase 3 — Tests, Regression & Cleanup

- [ ] 6.1 Write Jest unit test: AMTS input contract — construct minimal `MedicationRequest` FHIR JSON, run the controller mapping function, assert all fields from Decision 9 contract table are correctly extracted
- [ ] 6.2 Write Jest unit test: Reconciliation input contract — construct minimal `MedicationStatement` + `MedicationRequest` FHIR JSON, run the controller mapping function, assert all fields from Decision 8 contract table are correctly extracted
- [ ] 6.3 Run `FHIRModel` slicing path smoke test: bind a view control to `coding[system=http://www.whocc.no/atc]/code` on a live fixture resource and verify the correct ATC code value is rendered
- [ ] 6.4 Run US0–US12 regression walkthrough against `/fhir/` routes with `/api/` routes disabled — verify all use cases pass
- [ ] 6.5 Remove all `/api/` routes from the mock server
- [ ] 6.6 Run FHIR validator on fixtures one final time to confirm no drift was introduced during Phase 3
