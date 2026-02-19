# emp-link-identifier

## Purpose
TBD

## Requirements

### Requirement: MedicationPlanIdentifier & Hard Link Mechanisms
Every `MedicationRequest` and `MedicationStatement` SHALL have a globally unique `medicationPlanIdentifier` (UUID) using the system `https://gematik.de/fhir/sid/emp-identifier`. 
A "Hard Link" between a `MedicationStatement` and a `MedicationRequest` SHALL be established technically by:
1. Matching `medicationPlanIdentifier`s.
2. The `MedicationStatement` MUST contain a `basedOn` reference pointing to the `MedicationRequest` ID, and this reference MUST contain the extension `isEMP` = true.

#### Scenario: Assigning Identifier
- **WHEN** a new `MedicationRequest` is created
- **THEN** the system generates a random UUID for `medicationPlanIdentifier`

#### Scenario: Establishing Hard Link
- **WHEN** a link operation (`link-emp`, `update-emp-entry`) is successful
- **THEN** the `MedicationStatement` contains the `medicationPlanIdentifier` of the `MedicationRequest`
- **AND** the `MedicationStatement` has a `basedOn` reference to the `MedicationRequest`
- **AND** the link is visualized in the UI as a green chain icon

### Requirement: Operation link-emp
The system SHALL provide a `POST /api/medications/link-emp` operation to explicitly link an existing `MedicationStatement` to an existing `MedicationRequest`.

#### Scenario: Linking entries
- **WHEN** client sends a link request with `medicationStatementId` and `medicationRequestId`
- **AND** the request includes a valid `X-Requesting-Organization` header
- **THEN** the system adds the `basedOn` reference to the `MedicationStatement`
- **AND** synchronizes the `medicationPlanIdentifier`
- **AND** records an `EPAActivityProvenance` and `EMPChronologyProvenance`

### Requirement: Operation unlink-emp
The system SHALL provide a `POST /api/medications/unlink-emp` operation to remove a link.

#### Scenario: Unlinking entries
- **WHEN** client sends an unlink request
- **AND** the request includes a valid `X-Requesting-Organization` header
- **THEN** the system removes the `basedOn` reference and `isEMP` extension from the `MedicationStatement`
- **AND** records an `EPAActivityProvenance` and `EMPChronologyProvenance`

### Requirement: Operation add-emp-entry
The system SHALL provide a `POST /api/medications/add-emp-entry` operation to create a new `MedicationRequest` (optionally from a `MedicationStatement`).

#### Scenario: Adding entry
- **WHEN** client sends an add request
- **AND** the request includes a valid `X-Requesting-Organization` header
- **THEN** the system creates a new `MedicationRequest` with a new `medicationPlanIdentifier`
- **AND** links the `MedicationStatement` to it (if provided)
- **AND** records an `EPAActivityProvenance` and `EMPChronologyProvenance`

### Requirement: Operation update-emp-entry
The system SHALL provide a `POST /api/medications/update-emp-entry` operation to update clinical data of an existing `MedicationRequest` and optionally link a `MedicationStatement`.

#### Scenario: Updating entry successfully
- **WHEN** client sends an update request
- **AND** the request includes a valid `X-Requesting-Organization` header
- **AND** the `acknowledgedChronologyId` matches the current `EMPChronologyProvenance` ID
- **AND** the dosage is strictly valid (renderedDosageInstruction matches structure)
- **THEN** the system updates the `MedicationRequest`
- **AND** updates linking if a `MedicationStatement` is provided in the context
- **AND** records an `EPAActivityProvenance` and `EMPChronologyProvenance`

#### Scenario: Version Conflict (Optimistic Locking)
- **WHEN** the `acknowledgedChronologyId` does NOT match the current `EMPChronologyProvenance` ID
- **THEN** the system returns HTTP 409 Conflict with code `MEDSVC_EMP_CHRONOLOGY_ID_MISMATCH`

#### Scenario: Dosage Validation Failure
- **WHEN** the request contains a structured dosage BUT the provided text in `renderedDosageInstruction` does not match the rules of the rendering algorithm
- **THEN** the system returns HTTP 400 Bad Request with code `MEDSVC_DOSAGE_INVALID`

#### Scenario: Header Validation Failure
- **WHEN** the `X-Requesting-Organization` header is missing, malformed, or does not match the TIOrganization profile
- **THEN** the system returns HTTP 422 Unprocessable Entity with code `SVC_ORG_HEADER_PROFILE_MISMATCH`

### Requirement: Provenance Generation
The system SHALL automatically generate `EPAActivityProvenance` and `EMPChronologyProvenance` for all write operations (`link-emp`, `unlink-emp`, `add-emp-entry`, `update-emp-entry`).

#### Scenario: Provenance creation
- **WHEN** a write operation is performed
- **THEN** `EPAActivityProvenance` records the agent from `X-Requesting-Organization` and references the updated/created resource
- **AND** `EMPChronologyProvenance` records the new version of the overall plan by capturing a snapshot of all currently `active` or `on-hold` plan entries
