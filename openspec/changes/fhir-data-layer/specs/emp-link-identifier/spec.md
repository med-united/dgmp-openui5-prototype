# emp-link-identifier (delta)

Delta spec for the `fhir-data-layer` change. Updates the existing `emp-link-identifier` spec with concrete FHIR R4 resource paths, extension structures, and batch entry shapes. All requirements below replace the corresponding requirements in `openspec/specs/emp-link-identifier/spec.md`.

## MODIFIED Requirements

### Requirement: MedicationPlanIdentifier & Hard Link Mechanisms
Every `MedicationRequest` and `MedicationStatement` SHALL carry a `medicationPlanIdentifier` using a FHIR `Identifier` element with `system = "https://gematik.de/fhir/sid/emp-identifier"` and `value` = a UUID.
A "Hard Link" between a `MedicationStatement` and a `MedicationRequest` SHALL be established by:
1. Both resources sharing the same `medicationPlanIdentifier` value.
2. The `MedicationStatement` carrying a `basedOn` reference to `MedicationRequest/<id>` with extension `{"url": "https://gematik.de/fhir/epa-medication/StructureDefinition/is-emp", "valueBoolean": true}`.

#### Scenario: Assigning Identifier
- **WHEN** a new `MedicationRequest` is created
- **THEN** the system SHALL generate a random UUID and set it as `identifier[system=https://gematik.de/fhir/sid/emp-identifier].value`

#### Scenario: Establishing Hard Link
- **WHEN** a link operation (`link-emp`, `update-emp-entry`) is successful
- **THEN** the `MedicationStatement` SHALL carry `identifier[system=https://gematik.de/fhir/sid/emp-identifier].value` equal to the `MedicationRequest`'s identifier value
- **AND** `MedicationStatement.basedOn[0]` SHALL be `{"reference": "MedicationRequest/<id>", "extension": [{"url": "https://gematik.de/fhir/epa-medication/StructureDefinition/is-emp", "valueBoolean": true}]}`
- **AND** the link SHALL be visualized in the UI as a green chain icon

---

### Requirement: Operation link-emp
The system SHALL provide a `link-emp` operation as a FHIR Batch entry in `POST /fhir` to explicitly link an existing `MedicationStatement` to an existing `MedicationRequest`.

#### Scenario: Linking entries
- **WHEN** the client sends a Batch Bundle containing a `link-emp` entry with `MedicationStatement.id` and `MedicationRequest.id`
- **AND** the Batch request includes a valid `X-Requesting-Organization` header
- **THEN** the system SHALL set `MedicationStatement.basedOn[0].reference = "MedicationRequest/<id>"`
- **AND** SHALL set `MedicationStatement.basedOn[0].extension[is-emp].valueBoolean = true`
- **AND** SHALL synchronize `medicationPlanIdentifier` on both resources
- **AND** SHALL generate an `EPAActivityProvenance` and an `EMPChronologyProvenance` and return them in the `batch-response`

---

### Requirement: Operation unlink-emp
The system SHALL provide an `unlink-emp` operation as a FHIR Batch entry in `POST /fhir` to remove a hard link.

#### Scenario: Unlinking entries
- **WHEN** the client sends a Batch Bundle containing an `unlink-emp` entry with the `MedicationStatement.id`
- **AND** the Batch request includes a valid `X-Requesting-Organization` header
- **THEN** the system SHALL remove `MedicationStatement.basedOn` and the `is-emp` extension
- **AND** SHALL NOT modify the `MedicationRequest` or its `medicationPlanIdentifier`
- **AND** SHALL generate an `EPAActivityProvenance` and an `EMPChronologyProvenance` and return them in the `batch-response`

---

### Requirement: Operation add-emp-entry
The system SHALL provide an `add-emp-entry` operation as a FHIR Batch entry in `POST /fhir` to create a new `MedicationRequest`.

#### Scenario: Adding entry
- **WHEN** the client sends a Batch Bundle containing a `PUT MedicationRequest/<new-id>` entry
- **AND** the Batch request includes a valid `X-Requesting-Organization` header
- **THEN** the system SHALL persist the new `MedicationRequest` with a generated `medicationPlanIdentifier`
- **AND** if a `MedicationStatement.id` is referenced, SHALL establish the hard link as per the link-emp operation
- **AND** SHALL generate an `EPAActivityProvenance` and an `EMPChronologyProvenance` and return them in the `batch-response`

---

### Requirement: Operation update-emp-entry
The system SHALL provide an `update-emp-entry` operation as a FHIR Batch entry in `POST /fhir` to update clinical data of an existing `MedicationRequest`.

#### Scenario: Updating entry successfully
- **WHEN** the client sends a Batch Bundle containing a `PUT MedicationRequest/<id>` entry
- **AND** the resource carries `EMPChronologyProvenance` reference in `supportingInformation` representing the `acknowledgedChronologyId`
- **AND** the Batch request includes a valid `X-Requesting-Organization` header
- **AND** the `acknowledgedChronologyId` matches the current server-side `EMPChronologyProvenance` ID
- **THEN** the system SHALL update the `MedicationRequest`
- **AND** SHALL generate updated `EPAActivityProvenance` and `EMPChronologyProvenance` and return them in the `batch-response`

#### Scenario: Version Conflict (Optimistic Locking)
- **WHEN** the `acknowledgedChronologyId` in `supportingInformation` does NOT match the current `EMPChronologyProvenance` ID
- **THEN** the system SHALL return a `batch-response` entry with `response.status = "409 Conflict"`
- **AND** the entry `resource` SHALL be a FHIR `OperationOutcome` with `issue[0].code = "MEDSVC_EMP_CHRONOLOGY_ID_MISMATCH"`

#### Scenario: Dosage Validation Failure
- **WHEN** the request contains a structured dosage whose `renderedDosageInstruction` does not conform to the rendering algorithm
- **THEN** the system SHALL return a `batch-response` entry with `response.status = "400 Bad Request"`
- **AND** the entry `resource` SHALL be a FHIR `OperationOutcome` with `issue[0].code = "MEDSVC_DOSAGE_INVALID"`

#### Scenario: Header Validation Failure
- **WHEN** the `X-Requesting-Organization` header is missing or malformed
- **THEN** the system SHALL return HTTP 422 (top-level, not a per-entry `batch-response` error) with `OperationOutcome.issue[0].code = "SVC_ORG_HEADER_PROFILE_MISMATCH"`

---

### Requirement: Provenance Generation
The system SHALL automatically generate `EPAActivityProvenance` and `EMPChronologyProvenance` FHIR `Provenance` resources for all write operations.

#### Scenario: Provenance creation
- **WHEN** a write operation (link-emp, unlink-emp, add-emp-entry, update-emp-entry) is performed
- **THEN** `EPAActivityProvenance` SHALL be a FHIR `Provenance` resource with `target` referencing the updated/created resource, and `agent` populated from the `X-Requesting-Organization` header
- **AND** `EMPChronologyProvenance` SHALL be a FHIR `Provenance` resource capturing a snapshot of all currently `active` or `on-hold` `MedicationRequest` entries at the time of the write
- **AND** both provenance resources SHALL be returned as additional entries in the `batch-response` Bundle
- **AND** `EMPChronologyProvenance.id` SHALL serve as the new `chronologyId` for subsequent optimistic locking checks
