# 001-epa-medication-ui

## Purpose

UI prototype for the German ePA Medication Service (dgMP). Implements the eML/eMP display, medication matching, CRUD operations, linking, reconciliation, and safety check per Implementation Guide ePA Medication Service v1.3.0.

## Use Case Coverage

| US | Title | Status |
|----|-------|--------|
| US0 | Patient Selection (KVNR search, recent patients) | ✅ Implemented |
| US1 | View eML (historical prescriptions & dispensements) | ✅ Implemented |
| US2 | View eMP (current medication plan) | ✅ Implemented |
| US3 | Duplicate Detection (PZN/ATC/ASK match on add) | ✅ Implemented |
| US4 | Reconciliation View (eML vs eMP discrepancy) | ✅ Implemented |
| US5 | Create Manual eMP Entry (PZN search + form) | ✅ Implemented |
| US6 | Edit Existing eMP Entry | ✅ Implemented |
| US7 | Manage Medication Status (pause/reactivate/delete) | ✅ Implemented |
| US8 | Link/Unlink eML ↔ eMP Entry | ✅ Implemented |
| US9 | Prescription Workflow Integration | ❌ Deferred (out of scope) |
| US10 | Dispensing Workflow Integration | ❌ Deferred (out of scope) |
| US11 | PDF Export (Bundesmedikationsplan format) | ⚠️ Placeholder (toast only) |
| US12 | AMTS Safety Check (simulated interactions) | ✅ Implemented |

### Additional Implemented Behaviours (not in original US list)

- **Therapy History Popup**: eMP entry shows linked eML events (prescriptions + dispensements) in chronological order
- **Medication Name Autocomplete**: Name-suggest search in Add/Edit dialog (`/api/medications/search`)
- **Reconciliation Time Range Filter**: Filter reconciliation view by 1M/3M/6M/1Y/All
- **Show/Hide Linked Toggle**: Toggle to show/hide already-linked items in reconciliation
- **Mark as Error**: Soft-delete orphan eML items from reconciliation view (client-side)
- **Chronology/Concurrency Check**: Optimistic concurrency via `chronologyId` on eMP updates (409 CHRONOLOGY response)

---

## Requirements

### Requirement: Soft-Match Logic
The system SHALL provide a "Soft-Match" suggestion logic in the Reconciliation View to identify potential matches between `MedicationStatement` (eML) and `MedicationRequest` (eMP) entries that are not yet hard-linked.

#### Scenario: ATC Match
- **WHEN** a `MedicationStatement` and `MedicationRequest` have the same ATC code (first 7 digits)
- **THEN** they are displayed as a "Possible Match" (`UNLINKED_UPDATE`, yellow/warning)
- **AND** no technical link is established yet

#### Scenario: PZN Match
- **WHEN** a `MedicationStatement` and `MedicationRequest` have the same PZN but no hard-link identifier
- **THEN** they are displayed as a "Proposal Match" (`PROPOSAL_MATCH`, blue/information, secondary heuristic)

### Requirement: Dispensation Date Display
The Medication Plan View SHALL display the date of the latest dispensed medication (`MedicationStatement`) linked to a plan item.

#### Scenario: Showing last dispensation
- **WHEN** a `MedicationRequest` is linked to one or more `MedicationStatement` items
- **THEN** the UI displays the date of the most recent `MedicationStatement` as "Last Dispensed"

### Requirement: Hard-Link via MedicationPlanIdentifier
The system SHALL use a shared UUID (`medicationPlanIdentifier`) as the canonical hard-link between `MedicationStatement` (eML) and `MedicationRequest` (eMP).

#### Scenario: Creating a link
- **WHEN** the user links an eML entry to an eMP entry
- **THEN** the eMP entry receives a `medicationPlanIdentifier` UUID (if not already set)
- **AND** the eML entry's `medicationPlanIdentifier` is set to the same UUID
- **AND** the eML entry's `basedOn` is set to `MedicationRequest/<empId>`

#### Scenario: Removing a link
- **WHEN** the user unlinks an eML entry
- **THEN** `medicationPlanIdentifier` and `basedOn` are cleared on the eML entry only

### Requirement: Reconciliation Status Taxonomy
Items in the Reconciliation View SHALL be classified with the following statuses:

| Status | Colour | Meaning |
|--------|--------|---------|
| `ORPHAN_NEW` | Red/Error | eML entry has no match in eMP |
| `UNLINKED_UPDATE` | Yellow/Warning | Same active ingredient (ATC) in eMP but not hard-linked |
| `PROPOSAL_MATCH` | Blue/Information | Same PZN in eMP but not hard-linked |
| `LINKED` | Green/Success | Hard-linked via `medicationPlanIdentifier` |

### Requirement: Duplicate Detection on eMP Add/Edit
The system SHALL check for duplicates before persisting a new or updated eMP entry.

#### Match criteria (in priority order)
1. Same PZN
2. Same ATC code (first 5 chars)
3. Same ASK code

#### Scenario: Duplicate found
- **WHEN** a duplicate is detected on save
- **THEN** the backend returns HTTP 409 with `{ isDuplicate: true, ... }`
- **AND** the UI shows the `DuplicateDialog` allowing the user to cancel or proceed

### Requirement: Optimistic Concurrency (Chronology Check)
The system SHALL protect concurrent eMP edits using a `chronologyId`.

#### Scenario: Stale edit
- **WHEN** a PUT request includes a `chronologyId` that no longer matches the server state
- **THEN** the backend returns HTTP 409 with `{ error: "MEDSVC_EMP_CHRONOLOGY_ID_MISMATCH" }`
- **AND** the UI displays an error asking the user to refresh before retrying

### Requirement: Organization Header
All mutating API calls SHALL include the `X-Requesting-Organization` header identifying the healthcare organization performing the action. Requests without this header SHALL be rejected.
