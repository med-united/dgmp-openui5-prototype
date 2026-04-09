# emp-link-identifier

## Purpose
TBD

## Requirements

### Requirement: MedicationPlanIdentifier

Every `MedicationRequest` SHALL carry a `medicationPlanIdentifier` using a FHIR `Identifier` element with `system = "https://gematik.de/fhir/sid/emp-identifier"` and `value` = a UUID.

#### Scenario: Assigning Identifier
- **WHEN** a new `MedicationRequest` is created
- **THEN** the system SHALL generate a random UUID and set it as `identifier[system=https://gematik.de/fhir/sid/emp-identifier].value`

---

### Requirement: Operation link-emp

The system SHALL implement `linkEMP_MedicationSvc` as a FHIR instance operation on `MedicationStatement`.

#### Scenario: Linking an eML entry to an eMP entry
- **WHEN** the client sends `POST /fhir/MedicationStatement/<id>/$link-emp` with a `Parameters` resource containing `emp-entry-id` (the logical ID of the target eMP `MedicationRequest`)
- **AND** the request includes a valid `X-Requesting-Organization` header
- **THEN** the server SHALL set `MedicationStatement.basedOn` to reference the target `MedicationRequest` (eMP entry)
- **AND** SHALL return a `Parameters` resource containing the updated `MedicationStatement` (1..1) and an `EMPChronologyProvenance` (0..1)
- **AND** the link SHALL be visualized in the UI as a green chain icon

#### Scenario: Obtaining the MedicationStatement ID
- **WHEN** the client loads the eML for a patient
- **THEN** the eML bundle query SHALL include `_revinclude=MedicationStatement:derived-from` so that the `MedicationStatement` resources (mode=include) are returned alongside the `MedicationDispense` entries (mode=match)
- **AND** the client SHALL use the returned `MedicationStatement.id` when invoking `$link-emp` or `$unlink-emp`

#### Scenario: Link errors
- **WHEN** `$link-emp` fails
- **THEN** the server SHALL return:
  - `400` with `LINKING_NOT_SUCCESSFUL` if the link could not be established
  - `400` with `MEDSVC_ALREADY_LINKED` if the entry is already linked to an eMP entry
  - `404` with `MSG_RESOURCE_ID_FAIL` if the `MedicationStatement` or target `MedicationRequest` does not exist
  - `409` with `MEDSVC_EMP_CHRONOLOGY_ID_MISMATCH` if the chronology ID has changed
  - `422` with `MEDSVC_NO_VALID_STRUCTURE` or `SVC_ORG_HEADER_PROFILE_MISMATCH` on validation or header failures
  - `431` with `SVC_ORG_HEADER_TOO_LARGE` if the org header exceeds the size limit

---

### Requirement: Operation unlink-emp

The system SHALL implement `unlinkEMP_MedicationSvc` as a FHIR instance operation on `MedicationStatement`.

#### Scenario: Unlinking an eML entry from an eMP entry
- **WHEN** the client sends `POST /fhir/MedicationStatement/<id>/$unlink-emp` with a valid `X-Requesting-Organization` header (no additional input parameters)
- **THEN** the server SHALL remove `MedicationStatement.basedOn` reference to the eMP `MedicationRequest`
- **AND** SHALL return a `Parameters` resource containing:
  - the updated `MedicationStatement` (without the link)
  - the affected eMP `MedicationRequest`
  - an `EPAActivityProvenance`
  - an `EMPChronologyProvenance`

#### Scenario: Unlink errors
- **WHEN** `$unlink-emp` fails
- **THEN** the server SHALL return:
  - `400` with `UNLINKING_NOT_SUCCESSFUL` if the unlink could not be performed
  - `404` with `MSG_RESOURCE_ID_FAIL` if the resource does not exist
  - `410` with `MSG_DELETED` if the resource was deleted
  - `409` with `MEDSVC_EMP_CHRONOLOGY_ID_MISMATCH` on optimistic lock conflict
  - `422` with `SVC_ORG_HEADER_PROFILE_MISMATCH` or `MEDSVC_NO_VALID_STRUCTURE` on validation failures
  - `431` with `SVC_ORG_HEADER_TOO_LARGE` if the org header exceeds the size limit

---

### Requirement: Operation add-emp-entry
The system SHALL provide an `add-emp-entry` operation as a FHIR Batch entry in `POST /fhir` to create a new `MedicationRequest`.

#### Scenario: Adding entry
- **WHEN** the client sends a Batch Bundle containing a `PUT MedicationRequest/<new-id>` entry
- **AND** the Batch request includes a valid `X-Requesting-Organization` header
- **THEN** the system SHALL persist the new `MedicationRequest` with a generated `medicationPlanIdentifier`
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
- **THEN** the system SHALL return a `batch-response` entry with `response.status = "422 Unprocessable Entity"`
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
- **AND** for `$link-emp` and `$unlink-emp`, provenance resources SHALL be returned as entries in the `Parameters` response
- **AND** for `add-emp-entry` and `update-emp-entry`, provenance resources SHALL be returned as additional entries in the `batch-response` Bundle
- **AND** `EMPChronologyProvenance.id` SHALL serve as the new `chronologyId` for subsequent optimistic locking checks
