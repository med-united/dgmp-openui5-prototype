## ADDED Requirements

### Requirement: Soft-Match Logic
The system SHALL provide a "Soft-Match" suggestion logic in the Reconciliation View to identify potential matches between `MedicationStatement` (eML) and `MedicationRequest` (eMP) entries that are not yet hard-linked.

#### Scenario: ATC Match
- **WHEN** a `MedicationStatement` and `MedicationRequest` have the same ATC code (first 7 digits)
- **THEN** they are displayed as a "Possible Match" (yellow)
- **AND** no technical link is established yet

#### Scenario: PZN Match
- **WHEN** a `MedicationStatement` and `MedicationRequest` have the same PZN but different ATC
- **THEN** they are displayed as a "Possible Match" (secondary heuristic)

### Requirement: Dispensation Date Display
The Medication Plan View SHALL display the date of the latest dispensed medication (`MedicationStatement`) linked to a plan item.

#### Scenario: Showing last dispensation
- **WHEN** a `MedicationRequest` is linked to one or more `MedicationStatement` items
- **THEN** the UI displays the date of the most recent `MedicationStatement` as "Last Dispensed"
