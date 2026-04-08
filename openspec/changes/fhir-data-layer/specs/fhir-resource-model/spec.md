# fhir-resource-model

## Purpose

Defines the canonical FHIR R4 resource shapes, profiles, coding systems, and extension URLs used throughout the prototype. All fixture files, mock server responses, and client-side resource construction MUST conform to these definitions.

## ADDED Requirements

### Requirement: Profile Conformance

All resources in fixture files and mock server responses SHALL conform to the ePA Medication Service Implementation Guide profiles rather than plain FHIR R4 base resources.

#### Scenario: MedicationRequest profile
- **WHEN** a `MedicationRequest` resource is written to a fixture or returned by the mock server
- **THEN** it SHALL conform to the `KBV_PR_ERP_MedicationRequest` profile (or the applicable ePA IG successor)
- **AND** it SHALL carry a `meta.profile` element naming the profile URL

#### Scenario: MedicationDispense profile
- **WHEN** a `MedicationDispense` resource is written to a fixture or returned by the mock server
- **THEN** it SHALL conform to the `GEM_ERP_PR_MedicationDispense` profile
- **AND** it SHALL carry a `meta.profile` element naming the profile URL

#### Scenario: Fixture validation
- **WHEN** any fixture file is added or modified
- **THEN** it SHALL pass validation by the HL7 FHIR validator CLI with the ePA IG package loaded
- **AND** the Maven `validate` phase SHALL fail if any fixture is non-conformant

---

### Requirement: MedicationRequest Resource Shape (eMP Entry)

Each eMP entry SHALL be represented as a `MedicationRequest` resource.

#### Scenario: Required fields
- **WHEN** a `MedicationRequest` is created
- **THEN** it SHALL contain `id` (logical resource ID), `status` (`active` | `on-hold` | `stopped`), `intent` (`order`), `subject` (Reference to Patient), `authoredOn` (ISO 8601), and `medicationCodeableConcept`

#### Scenario: Medication coding
- **WHEN** a `MedicationRequest` carries medication coding
- **THEN** `medicationCodeableConcept.coding` SHALL contain an entry with `system = "http://fhir.de/CodeSystem/ifa/pzn"` for the PZN
- **AND** `medicationCodeableConcept.coding` SHALL contain an entry with `system = "http://www.whocc.no/atc"` for the ATC code
- **AND** `medicationCodeableConcept.text` SHALL carry the human-readable medication name

#### Scenario: Dosage
- **WHEN** a `MedicationRequest` has structured dosage
- **THEN** `dosageInstruction[0].text` SHALL carry the rendered dosage string (e.g. `"1-0-1-0"`)
- **AND** `dosageInstruction[0].additionalInstruction` SHALL carry free-text intake notes

---

### Requirement: MedicationStatement Resource Shape (eML Prescription)

Each eML prescription event SHALL be represented as a `MedicationStatement` resource.

#### Scenario: Required fields
- **WHEN** a `MedicationStatement` is created
- **THEN** it SHALL contain `id`, `status` (`active` | `completed`), `subject` (Reference to Patient), `dateAsserted` (ISO 8601), `medicationCodeableConcept`, and `informationSource` (Reference to practitioner or organization)

#### Scenario: Link to eMP entry
- **WHEN** a `MedicationStatement` is hard-linked to a `MedicationRequest`
- **THEN** `basedOn` SHALL contain a `Reference` to `MedicationRequest/<id>`
- **AND** `basedOn[0]` SHALL carry the extension `https://gematik.de/fhir/epa-medication/StructureDefinition/is-emp` with `valueBoolean = true`

---

### Requirement: MedicationDispense Resource Shape (eML Dispensement)

Each eML dispensement event SHALL be represented as a `MedicationDispense` resource (profile: `GEM_ERP_PR_MedicationDispense`). Dispensements are NOT `MedicationStatement` resources.

#### Scenario: Required fields
- **WHEN** a `MedicationDispense` is created
- **THEN** it SHALL contain `id`, `status` (`completed`), `subject` (Reference to Patient), `whenHandedOver` (ISO 8601), `medicationCodeableConcept`, and `performer`

#### Scenario: Link to authorizing prescription
- **WHEN** a `MedicationDispense` results from a prescription
- **THEN** `authorizingPrescription` SHALL contain a `Reference` to `MedicationRequest/<id>` (the eMP entry)
- **AND** `partOf` SHALL contain a `Reference` to `MedicationStatement/<id>` (the parent prescription event)

#### Scenario: Substitution flag
- **WHEN** a dispensement involved substitution
- **THEN** `substitution.wasSubstituted` SHALL be `true`
- **AND** `substitution.type.coding[system=http://terminology.hl7.org/CodeSystem/v3-substanceAdminSubstitution]` SHALL carry the substitution code

---

### Requirement: Patient Resource Shape

Each patient SHALL be represented as a FHIR `Patient` resource.

#### Scenario: KVNR identifier
- **WHEN** a `Patient` resource is created
- **THEN** `identifier` SHALL contain an entry with `system = "http://fhir.de/sid/gkv/kvid-10"` and `value` = the 10-character KVNR
- **AND** `name[0].family` and `name[0].given[0]` SHALL carry the patient's name
- **AND** `birthDate` SHALL be present in `YYYY-MM-DD` format

#### Scenario: Patient lookup
- **WHEN** the mock server receives `GET /fhir/Patient?identifier=<kvnr>`
- **THEN** it SHALL return a `Bundle` of type `searchset` containing the matching `Patient` resource

---

### Requirement: Bundle Format for Collections

All multi-resource responses from the mock server SHALL use FHIR `Bundle` resources.

#### Scenario: Read bundle
- **WHEN** the mock server responds to a search request (e.g. `GET /fhir/MedicationRequest?patient=X`)
- **THEN** the response SHALL be a `Bundle` of type `searchset`
- **AND** each entry SHALL carry the full resource under `entry[n].resource`
- **AND** `Bundle.total` SHALL reflect the number of matching resources

#### Scenario: Batch response bundle
- **WHEN** the mock server responds to `POST /fhir` (batch)
- **THEN** the response SHALL be a `Bundle` of type `batch-response`
- **AND** each entry SHALL carry `response.status` (e.g. `"200 OK"`, `"409 Conflict"`)
- **AND** failed entries SHALL include an `OperationOutcome` resource under `entry[n].resource`

---

### Requirement: medicationPlanIdentifier Extension

The shared link identifier between `MedicationStatement` and `MedicationRequest` SHALL use a canonical FHIR `Identifier` element.

#### Scenario: Identifier structure
- **WHEN** a `MedicationRequest` or `MedicationStatement` carries a medication plan identifier
- **THEN** `identifier` SHALL contain an entry with `system = "https://gematik.de/fhir/sid/emp-identifier"` and `value` = a UUID (format `urn:uuid:<uuid>` or bare UUID)
- **AND** this identifier SHALL be the same value on both the `MedicationRequest` and its linked `MedicationStatement`

---

### Requirement: isEMP Extension URL

The flag marking a `MedicationStatement.basedOn` reference as pointing to an eMP entry SHALL use the canonical ePA IG extension URL.

#### Scenario: Extension on basedOn
- **WHEN** a `MedicationStatement` is hard-linked to a `MedicationRequest`
- **THEN** `basedOn[0].extension` SHALL contain `{"url": "https://gematik.de/fhir/epa-medication/StructureDefinition/is-emp", "valueBoolean": true}`
- **AND** this extension SHALL NOT be present on `basedOn` references that are not eMP links

---

### Requirement: Fixture Files as FHIR Bundles

All static test data SHALL be stored as FHIR R4 `Bundle` resources.

#### Scenario: Medication plan fixture
- **WHEN** the mock server loads a patient's eMP data
- **THEN** it SHALL read from `src/main/resources/fixtures/medication-plan-<kvnr>.json`
- **AND** that file SHALL be a FHIR `Bundle` of type `searchset` containing `MedicationRequest` entries

#### Scenario: Medication list fixture
- **WHEN** the mock server loads a patient's eML data
- **THEN** it SHALL read from `src/main/resources/fixtures/medication-list-<kvnr>.json`
- **AND** that file SHALL be a FHIR `Bundle` of type `searchset` containing `MedicationStatement` and `MedicationDispense` entries

#### Scenario: Patient fixture
- **WHEN** the mock server loads patient data
- **THEN** it SHALL read from `src/main/resources/fixtures/patients.json`
- **AND** that file SHALL be a FHIR `Bundle` of type `searchset` containing `Patient` entries
