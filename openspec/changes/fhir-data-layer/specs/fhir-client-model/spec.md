# fhir-client-model

## Purpose

Specifies how the UI5 frontend consumes FHIR resources via `openui5-fhir`. Covers model initialization, the `FhirRepository` abstraction module, binding path rules, header injection, error handling, and pagination.

## ADDED Requirements

### Requirement: FHIRModel Initialization

The application SHALL initialize a `FHIRModel` instance as the named model `"fhir"` for all medication and patient resource data.

#### Scenario: Model creation
- **WHEN** the application bootstraps
- **THEN** `models.js` SHALL call `new FHIRModel("/fhir", { ... })` and register it as the named model `"fhir"` on the app component
- **AND** a separate `JSONModel` named `"ui"` SHALL be created for UI-only state (`currentKVNR`, `busy`, `orgId`, dialog state)
- **AND** no medication or patient data SHALL be stored in the `"ui"` JSONModel

#### Scenario: OpenUI5 version pin
- **WHEN** the application is loaded
- **THEN** `index.html` SHALL reference the OpenUI5 CDN at a pinned version URL (e.g. `.../1.120.6/resources/sap-ui-core.js`) — not the unversioned endpoint
- **AND** the pinned version SHALL have been confirmed compatible with `openui5-fhir` v2.4.0 before it is committed

---

### Requirement: FhirRepository Abstraction Module

All controllers SHALL interact with FHIR resources exclusively through a `FhirRepository` module. Controllers MUST NOT call `FHIRModel` methods directly.

#### Scenario: Module interface
- **WHEN** a controller needs to read FHIR resources
- **THEN** it SHALL call `FhirRepository.read(resourceType, searchParams)` which returns a `Promise<Resource[]>`

#### Scenario: Write via FhirRepository
- **WHEN** a controller needs to create, update, or delete a resource
- **THEN** it SHALL call `FhirRepository.create(resource)`, `FhirRepository.update(resource)`, or `FhirRepository.remove(resourceType, id)` to stage the change
- **AND** it SHALL call `FhirRepository.submitChanges()` to flush all staged changes as a single FHIR Batch Bundle

#### Scenario: No direct FHIRModel calls in controllers
- **WHEN** code review is performed on any controller file
- **THEN** direct calls to `FHIRModel.read()`, `FHIRModel.submitChanges()`, or `FHIRModel.setProperty()` for FHIR resources SHALL NOT be present
- **AND** only `FhirRepository.*` calls SHALL appear in controller business logic

---

### Requirement: X-Requesting-Organization Header

All FHIR requests issued by `FHIRModel` SHALL include the `X-Requesting-Organization` header.

#### Scenario: Header injection at initialization
- **WHEN** `FHIRModel` is initialized
- **THEN** it SHALL be configured with `httpHeaders: { "X-Requesting-Organization": "<org-identifier>" }`
- **AND** the org identifier value SHALL be sourced from the `"ui"` JSONModel at session start (set by `PatientSelection` controller)

#### Scenario: Missing header rejected
- **WHEN** the mock server receives a mutating request without `X-Requesting-Organization`
- **THEN** it SHALL return HTTP 422 with `OperationOutcome.issue.code = "SVC_ORG_HEADER_PROFILE_MISMATCH"`

---

### Requirement: FHIR Slicing Paths — No Positional Array Indices

All view XML bindings and controller property access on FHIR resources SHALL use FHIR slicing (secondary-index) path syntax. Positional array indices are prohibited.

#### Scenario: Coding element access
- **WHEN** a view or controller accesses a coding value within a `CodeableConcept`
- **THEN** it SHALL use the form `coding[system=<uri>]/code` (e.g. `medicationCodeableConcept/coding[system=http://www.whocc.no/atc]/code`)
- **AND** it SHALL NOT use the form `coding/0/code` or any other positional index

#### Scenario: Identifier access
- **WHEN** a view or controller accesses a specific identifier value
- **THEN** it SHALL use the form `identifier[system=<uri>]/value`
- **AND** it SHALL NOT use the form `identifier/0/value`

#### Scenario: Automated enforcement
- **WHEN** a commit is staged that modifies view XML or controller JS files
- **THEN** the pre-commit hook SHALL grep for positional index patterns (`/[0-9]+/`) in binding paths and fail the commit if any are found

---

### Requirement: Batch Error Handling

Controllers SHALL handle per-entry errors returned in FHIR `batch-response` bundles.

#### Scenario: Duplicate detection (409)
- **WHEN** `FhirRepository.submitChanges()` returns an error callback with an `OperationOutcome` where `issue[0].code` indicates a duplicate
- **THEN** the responsible controller SHALL open `DuplicateDialog` with the duplicate entry data
- **AND** the pending change SHALL remain staged (not discarded) until the user confirms or cancels

#### Scenario: Chronology mismatch (409)
- **WHEN** `FhirRepository.submitChanges()` returns an error callback with an `OperationOutcome` where `issue[0].code = "MEDSVC_EMP_CHRONOLOGY_ID_MISMATCH"`
- **THEN** the responsible controller SHALL display a stale-edit error message instructing the user to reload before retrying
- **AND** the pending change SHALL be discarded

#### Scenario: Auth header error (422)
- **WHEN** `FhirRepository.submitChanges()` returns an error callback with `issue[0].code = "SVC_ORG_HEADER_PROFILE_MISMATCH"`
- **THEN** the controller SHALL display a generic server error message (the org header misconfiguration is a developer/configuration problem, not a user-recoverable error)

---

### Requirement: Pagination for MedicationList

The `MedicationList` controller SHALL use `FHIRModel`'s built-in FHIR paging rather than implementing custom pagination.

#### Scenario: Growing list binding
- **WHEN** the `MedicationList` view binds to `MedicationStatement` resources
- **THEN** the list control SHALL use `growingThreshold` to request pages from the server
- **AND** `FHIRModel` SHALL automatically request the next page Bundle link when the user scrolls or triggers grow
- **AND** no custom `_count` or offset parameter management SHALL appear in the controller

---

### Requirement: AMTS Input Derivation Contract

The `MedicationPlan` controller SHALL produce a flat array for `AMTSSimulator.checkInteractions()` by mapping from `MedicationRequest` FHIR resources.

#### Scenario: Deriving AMTS input
- **WHEN** the AMTS check is triggered
- **THEN** the controller SHALL call `FhirRepository.read("MedicationRequest", { patient: kvnr, status: "active" })`
- **AND** for each resource it SHALL extract: `id`, `medicationName` (from `medicationCodeableConcept.text`), `atcCode` (from `coding[system=http://www.whocc.no/atc].code`), `pzn` (from `coding[system=http://fhir.de/CodeSystem/ifa/pzn].code`), `activeIngredient` (from `coding[system=http://fhir.de/CodeSystem/ask].display`), `status`
- **AND** the resulting array SHALL be passed to `AMTSSimulator.checkInteractions()` unchanged

---

### Requirement: Reconciliation Input Derivation Contract

The `Reconciliation` controller SHALL produce two flat arrays from FHIR resources and pass them to the existing soft-match algorithm.

#### Scenario: Deriving reconciliation input
- **WHEN** the reconciliation view loads
- **THEN** the controller SHALL call `FhirRepository.read("MedicationStatement", { patient: kvnr })` and `FhirRepository.read("MedicationRequest", { patient: kvnr })`
- **AND** for each `MedicationStatement` it SHALL extract: `id`, `medicationName`, `atcCode`, `pzn`, `medicationPlanIdentifier` (from `identifier[system=https://gematik.de/fhir/sid/emp-identifier].value`), `basedOnRef` (from `basedOn[0].reference`), `authoredDate`
- **AND** for each `MedicationRequest` it SHALL extract: `id`, `medicationName`, `atcCode`, `pzn`, `medicationPlanIdentifier`, `status`
- **AND** the two arrays SHALL be stored in the `"ui"` JSONModel under `/reconciliation/emlEntries` and `/reconciliation/empEntries` for view binding
