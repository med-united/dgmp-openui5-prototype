# fhir-resource-model

## Purpose

Defines the canonical FHIR R4 resource shapes, profiles, coding systems, and extension URLs used throughout the prototype. All fixture files, mock server responses, and client-side resource construction MUST conform to these definitions.

## Requirements

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
- **THEN** it SHALL contain `id` (logical resource ID), `status` (`active` | `on-hold` | `stopped` | `draft` | `completed`), `intent` (`order`), `subject` (Reference to Patient), `authoredOn` (ISO 8601), and `medicationReference` (Reference to `Medication`)
- **AND** it SHALL carry a `meta.profile` element naming the applicable profile URL

#### Scenario: Medication reference
- **WHEN** a `MedicationRequest` references medication data
- **THEN** `medicationReference` SHALL be a `Reference` to a `Medication` resource (e.g. `Medication/med-pzn-01476329`)
- **AND** the referenced `Medication` resource SHALL be included in the Bundle with `search.mode = "include"`
- **AND** medication name, PZN, ATC, dosage form, and ingredient data SHALL be sourced from the resolved `Medication` resource (not inline on `MedicationRequest`)

#### Scenario: Dosage
- **WHEN** a `MedicationRequest` has structured dosage
- **THEN** `dosageInstruction[0].text` SHALL carry the rendered dosage string (e.g. `"1-0-1-0"`)
- **AND** `dosageInstruction[0].additionalInstruction` SHOULD carry free-text intake notes where applicable

#### Scenario: Medication plan identifier
- **WHEN** a `MedicationRequest` is created
- **THEN** `identifier` SHALL contain an entry with `system = "https://gematik.de/fhir/sid/emp-identifier"` and `value` = a UUID

#### Scenario: Provenance reference (Phase 2+)
- **WHEN** a `MedicationRequest` has been written by the server
- **THEN** `supportingInformation` MAY carry a reference to `EMPChronologyProvenance` representing the current `chronologyId`
- **AND** Phase 1 fixtures are exempt from this requirement (provenance is server-generated)

---

### Requirement: Medication Resource Shape

Each medication SHALL be represented as a `Medication` resource conforming to the `KBV_PR_ERP_Medication_PZN` profile (version 1.1.0).

#### Scenario: Profile conformance
- **WHEN** a `Medication` resource is created
- **THEN** it SHALL carry `meta.profile` naming `https://fhir.kbv.de/StructureDefinition/KBV_PR_ERP_Medication_PZN|1.1.0`

#### Scenario: Mandatory extensions
- **WHEN** a `Medication` resource is created
- **THEN** it SHALL include the following extensions:
  - `KBV_EX_ERP_Medication_Category` (`valueCoding` with code `"00"` for Arzneimittel)
  - `KBV_EX_ERP_Medication_Vaccine` (`valueBoolean` = `false` for non-vaccine)
  - `http://fhir.de/StructureDefinition/normgroesse` (`valueCode` = `N1` | `N2` | `N3`)
  - `KBV_EX_Base_Medication_Type` (`valueCoding` with SNOMED code `763158003` = Medicinal product)

#### Scenario: Code (PZN) — closed slicing
- **WHEN** a `Medication` resource carries a PZN code
- **THEN** `code.coding` SHALL contain exactly one entry in the `pznCode` slice with `system = "http://fhir.de/CodeSystem/ifa/pzn"`
- **AND** `code.text` SHALL carry the human-readable medication name
- **AND** no additional codings (ATC, SNOMED) SHALL be present in `code.coding` (the slicing is CLOSED)

#### Scenario: Packaging size
- **WHEN** a `Medication` resource specifies packaging
- **THEN** `amount.numerator` SHALL use the `KBV_EX_ERP_Medication_PackagingSize` extension with a `valueString` (e.g. `"100"`)
- **AND** `amount.numerator.value` SHALL NOT be present (prohibited by the profile)
- **AND** `amount.denominator.value` SHALL be `1`

#### Scenario: Dosage form
- **WHEN** a `Medication` resource has a dosage form
- **THEN** `form.coding` SHALL use `system = "https://fhir.kbv.de/CodeSystem/KBV_CS_SFHIR_KBV_DARREICHUNGSFORM"` with a code from the `KBV_VS_SFHIR_KBV_DARREICHUNGSFORM` ValueSet
- **AND** `display` SHALL match the canonical display name for the code

#### Scenario: Prohibited elements
- **WHEN** a `Medication` resource conforms to `KBV_PR_ERP_Medication_PZN`
- **THEN** `Medication.text` SHALL NOT be present (`max=0`)
- **AND** `Medication.ingredient` SHALL NOT be present (`max=0`)

---

### Requirement: MedicationDispense Resource Shape (eML Dispensement)

Each eML dispensement event SHALL be represented as a `MedicationDispense` resource (profile: `GEM_ERP_PR_MedicationDispense`). Dispensements are NOT `MedicationStatement` resources.

#### Scenario: Required fields
- **WHEN** a `MedicationDispense` is created
- **THEN** it SHALL contain `id`, `status` (`completed`), `subject` (Reference to Patient), `whenHandedOver` (ISO 8601), `medicationReference` (Reference to `Medication`), and `performer`

#### Scenario: Link to authorizing prescription
- **WHEN** a `MedicationDispense` results from a prescription
- **THEN** `authorizingPrescription` SHALL contain a `Reference` to `MedicationRequest/<id>` (the eMP entry)

#### Scenario: Dosage display precedence
- **WHEN** rendering dosage for an eML entry
- **THEN** `MedicationDispense.dosageInstruction[0].text` SHALL be used if present
- **AND** the display SHALL fall back to `MedicationRequest.dosageInstruction[0].text` only if no dispense dosage is present

#### Scenario: Medication display precedence
- **WHEN** a dispensement exists for a prescription
- **THEN** `MedicationDispense.medicationReference → Medication` SHALL take precedence over `MedicationRequest.medicationReference → Medication` for displaying medication name, PZN, and dosage form

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

The eMP identifier on a `MedicationRequest` SHALL use a canonical FHIR `Identifier` element so that clients can correlate eMP entries with their corresponding eML dispenses.

#### Scenario: Identifier structure
- **WHEN** a `MedicationRequest` carries a medication plan identifier
- **THEN** `identifier` SHALL contain an entry with `system = "https://gematik.de/fhir/sid/emp-identifier"` and `value` = a UUID (format `urn:uuid:<uuid>` or bare UUID)

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
- **AND** that file SHALL be a FHIR `Bundle` of type `searchset` containing `MedicationDispense` entries (mode=match), included `MedicationRequest` entries (mode=include), included `Medication` entries (mode=include), and optionally `MedicationStatement` entries (mode=include, present when `_revinclude=MedicationStatement:derived-from` is requested)

#### Scenario: Patient fixture
- **WHEN** the mock server loads patient data
- **THEN** it SHALL read from `src/main/resources/fixtures/patients.json`
- **AND** that file SHALL be a FHIR `Bundle` of type `searchset` containing `Patient` entries
