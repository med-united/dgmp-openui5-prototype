# Feature Specification: ePA Medication Service UI Prototype

**Feature Branch**: `001-epa-medication-ui`  
**Created**: 2026-02-13  
**Status**: Draft  
**Input**: User description: "OpenUI5 UX prototype for ePA Medication Service implementing eML/eMP display, medication matching, CRUD operations, and workflow integration per Implementation Guide ePA Medication Service v1.3.0"

## Clarifications

### Session 2026-02-13

- Q: What is the home screen / patient selection UI navigation pattern? → A: Search-first approach with recent patients sidebar: Home screen shows KVNR search box prominently, plus sidebar with recently viewed patients (last 5-10).
- Q: How should users navigate between eML and eMP within a patient view? → A: Tab-based navigation: Patient view has two tabs "eML (History)" and "eMP (Current Plan)". Clicking tabs switches between views while staying in patient context.
- Q: How should patient and medication data be provided for UI prototype demonstration? → A: Pre-loaded JSON fixtures with 2-3 sample patients (diverse scenarios).
- Q: Where should CRUD action buttons be located in the eMP tab? → A: Toolbar above table + row-level action icons: "Add Medication" button in toolbar. Each medication row has inline action icons (edit, pause, delete).
- Q: How should users access the reconciliation view to compare eML and eMP? → A: Dedicated "Reconciliation" third tab alongside eML and eMP tabs, displaying split-view with highlighted discrepancies.
- Q: When duplicate medications are detected during add/edit, where should the match results appear? → A: Modal dialog with match results showing side-by-side comparison and radio buttons for "Update existing" / "Create new".
- Q: Where should Link/Unlink buttons be located for eML-eMP associations? → A: Row-level action icon in eML with context menu: Each eML row has link icon that opens menu to select target eMP entry. Linked rows show badge + Unlink option.
- Q: Where should the "Export to PDF" / "Print" button be located? → A: Toolbar in eMP tab next to "Add Medication" button.
- Q: Where should the "Run AMTS Check" button be located? → A: Toolbar in eMP tab alongside "Add Medication" and "Export PDF" buttons.
- Q: How should prescription/dispensing workflow integration (User Stories 10 & 11) be accessed in the UI prototype? → A: Out of scope for initial UI prototype. Mark as explicitly deferred until prescription/dispensing workflows exist.

## User Scenarios & Testing *(mandatory)*

### User Story 0 - Patient Selection and Navigation (Priority: P1)

As a healthcare provider, I need a home screen where I can search for a patient by KVNR or select from recently viewed patients so that I can access their medication data quickly.

**Why this priority**: This is the entry point to the entire application. Without patient selection UI, no other user stories are reachable. This must be implemented first as the navigation foundation.

**Independent Test**: Can be fully tested by displaying a home screen with a prominent KVNR search input field, and a sidebar showing recently viewed patients (last 5-10). Clicking a recent patient or searching by KVNR navigates to that patient's medication view.

**Acceptance Scenarios**:

1. **Given** the healthcare provider opens the application, **When** the home screen loads, **Then** the system displays a prominent KVNR search input field
2. **Given** the home screen is displayed, **When** the provider looks at the sidebar, **Then** the system shows a list of recently viewed patients (up to 10) with their name and KVNR
3. **Given** the provider enters a KVNR in the search field, **When** they submit the search, **Then** the system navigates to that patient's medication view (eML/eMP tabs)
4. **Given** the sidebar shows recent patients, **When** the provider clicks on a recent patient entry, **Then** the system navigates to that patient's medication view
5. **Given** a patient is currently loaded, **When** the provider wants to switch patients, **Then** the system provides a "Back to Patient Selection" or "Switch Patient" navigation option to return to the home screen

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 - View Electronic Medication List (eML) (Priority: P1)

As a healthcare provider (physician or pharmacist), I need to view the patient's historical medication records (prescriptions and dispensements) so that I can review their medication history for anamnesis purposes.

**Why this priority**: Viewing historical data is the foundation for understanding patient medication history and is required before any other workflow (matching, editing, etc.) can be performed. This delivers immediate value as a read-only dashboard.

**Independent Test**: Can be fully tested by loading patient data with KVNR and displaying the eML in a chronological list view with medication names, dates, and statuses. Delivers value by providing medication history visualization without requiring any write operations.

**Acceptance Scenarios**:

1. **Given** a patient with historical prescription and dispensement data, **When** the healthcare provider selects the patient (via KVNR), **Then** the system displays the patient view with an "eML (History)" tab active, showing all medication entries in chronological order with medication name, PZN, prescription/dispensement date, and status
2. **Given** the eML contains both prescriptions and dispensements, **When** viewing the eML tab, **Then** the system visually distinguishes between prescription entries (Verordnung) and dispensement entries (Abgabe/Packung)
3. **Given** multiple medication entries exist, **When** viewing the eML tab, **Then** entries are sorted by date (newest first) and include status indicators (e.g., "Abgegeben", "Storniert")

---

### User Story 2 - View Electronic Medication Plan (eMP) (Priority: P1)

As a healthcare provider, I need to view the patient's current active medication therapy plan so that I can understand what medications they are currently taking and their dosage regimens.

**Why this priority**: The eMP is the core document for current therapy management and must be available before any medication plan modifications can occur. This is equally critical to eML as both views form the dashboard foundation.

**Independent Test**: Can be fully tested by loading patient data and displaying the eMP grouped by status (active, paused, planned) with medication details, dosage schema, intake instructions, and treatment rationale.

**Acceptance Scenarios**:

1. **Given** a patient with an existing medication plan, **When** the healthcare provider clicks the "eMP (Current Plan)" tab, **Then** the system displays a toolbar with an "Add Medication" button above a table showing all therapy entries grouped by status ("aktiv", "pausiert", "geplant")
2. **Given** an active medication entry in the eMP tab, **When** viewing the entry row, **Then** the system displays medication name, active ingredient (Wirkstoff), strength, dosage schema (e.g., "1-0-1-0" or free text), intake instructions (e.g., "vor dem Essen"), indication (Behandlungsgrund), and row-level action icons (edit, pause, delete)
3. **Given** paused or planned medications exist, **When** viewing the eMP tab, **Then** paused entries are visually distinguished (e.g., grayed out) and planned entries are marked accordingly

---

### User Story 3 - Match New Medication Against eMP (Priority: P2)

As a physician, when I prescribe a new medication, I need the system to check if the active ingredient already exists in the patient's medication plan so that I can avoid creating duplicate entries and decide whether to update an existing entry or create a new one.

**Why this priority**: Duplicate prevention is critical for patient safety and data quality. This must be implemented before physicians can confidently add new prescriptions through the UI. It builds on P1 viewing capabilities.

**Independent Test**: Can be fully tested by simulating a new prescription input, triggering the matching algorithm that compares against existing eMP entries by ATC/ASK code or PZN, and presenting the user with match results and decision options.

**Acceptance Scenarios**:

1. **Given** a physician is creating a new prescription for "Ramipril 5mg", **When** the system checks the eMP and finds a match with the same active ingredient (ATC/ASK code), **Then** the system displays a modal dialog with side-by-side comparison showing both the new entry and existing entry (medication name, strength, dosage) with radio buttons "Update existing entry" or "Create new entry" and a Continue button
2. **Given** the duplicate match modal is displayed, **When** the physician reviews the comparison, **Then** the modal clearly shows both entries side-by-side and allows selection via radio buttons
3. **Given** no matching medication is found, **When** checking for duplicates, **Then** the system proceeds directly to the add medication form without showing the modal

---

### User Story 4 - Reconcile eML and eMP Discrepancies (Priority: P3)

As a pharmacist or physician, I need to identify medications that appear in the historical list (eML) but not in the current plan (eMP) so that I can reconcile gaps and add missing medications to the active therapy plan.

**Why this priority**: While important for comprehensive medication management, this is a more advanced workflow that can be implemented after basic viewing and matching capabilities are established.

**Independent Test**: Can be fully tested by clicking the "Reconciliation" tab and displaying eML and eMP in a split-view comparison mode that highlights discrepancies, allowing users to visually identify gaps and optionally transfer entries from eML to eMP (e.g., via button action).

**Acceptance Scenarios**:

1. **Given** the eML contains a medication that is not present in the eMP, **When** the healthcare provider clicks the "Reconciliation" tab, **Then** the system displays a split-view with eML on the left and eMP on the right, highlighting the discrepant medication entry in the eML
2. **Given** a highlighted discrepancy exists in the reconciliation view, **When** the user selects the eML entry, **Then** the system provides an "Add to eMP" button
3. **Given** the user clicks "Add to eMP" for an eML entry, **When** the action is initiated, **Then** the system pre-fills a new eMP entry form with data from the eML entry for review and confirmation

---

### User Story 5 - Create Manual eMP Entry (Priority: P2)

As a pharmacist or physician, I need to manually add a medication entry to the eMP (e.g., for OTC medications or missing prescriptions) so that the medication plan accurately reflects all medications the patient is taking.

**Why this priority**: Manual entry capability is essential for completeness of the medication plan, especially for self-medication and OTC products. This is a core CRUD operation that should be available early.

**Independent Test**: Can be fully tested by providing an input form that allows selection of medication (via PZN catalog search), entry of dosage (structured or free text), specification of entry type (self-medication vs. physician prescription), and successful creation of a new eMP entry.

**Acceptance Scenarios**:

1. **Given** a healthcare provider wants to add an OTC medication, **When** they initiate "Add Entry" in the eMP, **Then** the system presents a form with fields for medication selection (PZN search), dosage, intake instructions, indication, and entry type
2. **Given** the user selects a medication via PZN search, **When** the medication is chosen, **Then** the system auto-fills medication name, active ingredient, and strength
3. **Given** all required fields are completed, **When** the user saves the entry, **Then** the system creates a new eMP entry and displays it in the active medication list

---

### User Story 6 - Edit Existing eMP Entry (Priority: P2)

As a healthcare provider, I need to modify dosage instructions or intake notes for an existing medication in the eMP so that the plan reflects current therapy adjustments.

**Why this priority**: Editing capabilities are essential for maintaining an accurate and up-to-date medication plan as patient therapy evolves. This is a fundamental CRUD operation.

**Independent Test**: Can be fully tested by selecting an existing eMP entry, modifying dosage or instructions in an edit form, saving changes, and verifying that a new version of the plan is created while preserving change history.

**Acceptance Scenarios**:

1. **Given** an existing active medication entry in the eMP, **When** the healthcare provider selects "Edit", **Then** the system displays an editable form pre-filled with current values (dosage, instructions, indication)
2. **Given** the user modifies the dosage schema from "1-0-1-0" to "2-0-2-0", **When** they save changes, **Then** the system updates the entry and internally creates a new version of the medication plan to maintain chronology
3. **Given** changes are saved, **When** viewing the eMP, **Then** the updated dosage is displayed and the previous version is accessible in the history/audit trail

---

### User Story 7 - Manage Medication Status (Priority: P2)

As a healthcare provider, I need to pause, reactivate, or end a medication therapy entry in the eMP so that the plan accurately reflects the current treatment status.

**Why this priority**: Status management is critical for maintaining the accuracy of the active medication plan and understanding which therapies are currently in effect.

**Independent Test**: Can be fully tested by selecting an active medication entry and changing its status to "paused" (entry becomes grayed out but remains visible), "completed" (entry moves to history view), or reactivating a paused entry back to "active".

**Acceptance Scenarios**:

1. **Given** an active medication entry, **When** the user selects "Pause Therapy", **Then** the entry status changes to "pausiert", the entry remains visible in the eMP but is visually distinguished (grayed out/marked)
2. **Given** a paused medication entry, **When** the user selects "Reactivate", **Then** the entry status changes back to "active" and is displayed normally in the active medication list
3. **Given** an active or paused medication entry, **When** the user selects "End Therapy", **Then** the entry status changes to "completed", the entry is removed from the default eMP view but remains accessible in the history view

---

### User Story 8 - Link eML Entry to eMP Entry (Priority: P3)

As a healthcare provider, I need to create logical links between historical dispensement records (eML) and medication plan entries (eMP) so that I can track which dispensements correspond to which therapy plan lines.

**Why this priority**: While valuable for traceability and analytics, linking is a more advanced feature that can be implemented after core CRUD and viewing capabilities are established.

**Independent Test**: Can be fully tested by selecting an eML entry (MedicationStatement) and an eMP entry (MedicationRequest), creating a link between them, and verifying that the link is stored and displayed (e.g., eML entry shows "linked to eMP entry XYZ").

**Acceptance Scenarios**:

1. **Given** an eML dispensement entry and an eMP therapy entry for the same medication, **When** the user clicks the link icon on the eML row, **Then** the system opens a context menu/dropdown showing available eMP entries to link to, and selecting one creates a logical association between the MedicationStatement ID and MedicationRequest ID
2. **Given** a link has been created, **When** viewing the eML entry, **Then** the system displays a "linked" badge or icon and clicking the link icon shows an "Unlink" option in the context menu
3. **Given** a linked eML-eMP pair, **When** the user selects "Unlink" from the context menu, **Then** the system removes the association and both entries return to unlinked state

---

### User Story 9 - Integrate eMP Update with Prescription Workflow (Priority: P3)

As a physician, when I create a new e-prescription (eRezept), I need the option to simultaneously update the patient's eMP so that the medication plan stays synchronized with prescribing activity.

**Why this priority**: This is an advanced integration feature that requires prescription workflow functionality. It builds on the foundation of eMP CRUD operations and is valuable for streamlined workflows but not critical for MVP.

**Independent Test**: Can be fully tested by simulating the prescription creation workflow, providing a checkbox "Add to eMP", and verifying that when checked, the prescription data is also used to create or update a corresponding eMP entry.

**Acceptance Scenarios**:

1. **Given** a physician is creating a new e-prescription, **When** the prescription form is displayed, **Then** the system provides a checkbox option "Add to electronic Medication Plan (eMP)"
2. **Given** the "Add to eMP" checkbox is checked, **When** the physician completes the prescription, **Then** the system creates both the prescription record and a corresponding eMP entry (or updates an existing one if a match is found)
3. **Given** the checkbox is unchecked, **When** the prescription is completed, **Then** only the prescription is created without affecting the eMP

---

### User Story 10 - Integrate eMP Update with Dispensing Workflow (Priority: P3)

As a pharmacist, when I dispense a medication and record the dispensement, I need the option to update the eMP if the dispensed product differs from what was prescribed (e.g., generic substitution) so that the plan reflects what the patient actually received.

**Why this priority**: Similar to prescription integration, this is an advanced workflow integration feature that enhances accuracy but is not essential for MVP functionality.

**Independent Test**: Can be fully tested by simulating the dispensement workflow, recording a dispensed PZN that differs from the prescribed PZN, and verifying that the eMP can be updated with the actual dispensed product information.

**Acceptance Scenarios**:

1. **Given** a pharmacist is dispensing a medication and the actual product (PZN) differs from the prescription, **When** recording the dispensement, **Then** the system presents an option "Update eMP with dispensed product"
2. **Given** the pharmacist selects to update the eMP, **When** the dispensement is confirmed, **Then** the system updates the corresponding eMP entry with the PZN and name of the actually dispensed product
3. **Given** the eMP is updated with dispensement data, **When** viewing the eMP entry, **Then** the entry reflects the actual dispensed product rather than the originally prescribed product

---

### User Story 11 - Export Medication Plan (PDF) (Priority: P3)

As a healthcare provider, I need to generate a PDF export of the eMP in the standard Bundesmedikationsplan format so that the patient can receive a printed copy of their medication plan.

**Why this priority**: While valuable for patient handouts, PDF generation is a supplementary feature that can be added after core viewing and editing capabilities are fully functional.

**Independent Test**: Can be fully tested by clicking an "Export to PDF" or "Print View" button and verifying that a PDF file is generated containing all active eMP entries formatted according to Bundesmedikationsplan standards.

**Acceptance Scenarios**:

1. **Given** a patient has an active eMP with multiple entries, **When** the healthcare provider clicks the "Export to PDF" button in the eMP tab toolbar, **Then** the system generates a PDF document formatted according to Bundesmedikationsplan layout specifications
2. **Given** the PDF is generated, **When** reviewing the document, **Then** it contains all active medications with name, dosage, intake instructions, and indication
3. **Given** the PDF export is complete, **When** the user receives the PDF, **Then** it can be printed or saved for patient distribution

---

### User Story 12 - AMTS Safety Check (Simulation) (Priority: P4)

As a healthcare provider, I need to trigger a medication therapy safety check (AMTS) on the current eMP so that potential drug interactions, contraindications, or safety issues are identified and displayed.

**Why this priority**: AMTS checking is a valuable safety feature but represents an advanced integration with external safety check services. This is a simulation/demonstration feature that can be implemented last.

**Independent Test**: Can be fully tested by clicking an "AMTS Check" button and displaying simulated warning messages for potential interactions (e.g., "Interaction between Medication A and Medication B detected") or safety issues.

**Acceptance Scenarios**:

1. **Given** the eMP contains multiple active medications, **When** the healthcare provider clicks the "Run AMTS Check" button in the eMP tab toolbar, **Then** the system triggers a simulated medication safety analysis
2. **Given** the AMTS check identifies potential interactions, **When** the analysis completes, **Then** the system displays warning messages with details about the interaction (e.g., "Warnung: Interaktion zwischen Ibuprofen und Aspirin")
3. **Given** no safety issues are found, **When** the AMTS check completes, **Then** the system displays a confirmation message "No interactions or safety issues detected"

---

### Out of Scope (Initial UI Prototype)

The following features are **explicitly out of scope** for the initial UI-centric prototype and will be implemented in a later phase:

- **User Story 9 - Prescription Workflow Integration**: Integration with e-prescription (eRezept) creation workflow. This requires full prescription workflow infrastructure beyond the medication management UI. The "Add to eMP" checkbox during prescription creation will be deferred until the prescription workflow exists.

- **User Story 110 - Dispensing Workflow Integration**: Integration with dispensement recording workflow. This requires full pharmacy dispensing infrastructure. The "Update eMP with dispensed product" option will be deferred until the dispensing workflow exists.

**Rationale**: These are P3 priority advanced integration features that depend on external workflows not part of the medication management UI scope. All P1-P2 features are fully accessible in the designed UI.

---

### Edge Cases

- What happens when a patient has no existing eML or eMP data? (System should display empty state with guidance)
- How does the system handle medication entries with incomplete data (e.g., missing dosage)? (Display with warning indicator, allow editing)
- What happens when the PZN catalog search returns no results? (Allow free-text entry as fallback)
- How does the system handle concurrent edits by multiple healthcare providers? (Implement optimistic locking or display conflict resolution UI)
- What happens when matching finds multiple potential duplicates? (Display all matches and allow user to select which one to update)
- How does the system handle medications with multiple active ingredients? (Display all ingredients in the matching and viewing logic)

## Requirements *(mandatory)*

### Functional Requirements

#### Patient Selection and Navigation

- **FR-000**: System MUST provide a home screen as the application entry point with patient search and selection capabilities
- **FR-001a**: Home screen MUST display a prominent KVNR search input field
- **FR-001c**: Home screen MUST display a sidebar showing recently viewed patients (up to 10 entries)
- **FR-001d**: Recent patients sidebar MUST show patient name and KVNR for each entry
- **FR-001e**: System MUST navigate to patient medication view when KVNR is entered in search field
- **FR-001f**: System MUST navigate to patient medication view when a recent patient entry is clicked
- **FR-001g**: System MUST provide a "Switch Patient" or "Back to Patient Selection" button within patient view to return to home screen
- **FR-001h**: System MUST maintain the list of recently viewed patients across sessions (localStorage or similar)
- **FR-001i**: Patient view MUST use tab-based navigation with three tabs: "eML (History)", "eMP (Current Plan)", and "Reconciliation"
- **FR-001j**: System MUST display "eML (History)" tab as active/selected by default when a patient is first loaded
- **FR-001k**: Clicking a tab MUST switch the view while maintaining patient context (no page reload)
- **FR-001l**: Reconciliation tab MUST display a split-view layout with eML on the left and eMP on the right
- **FR-001m**: Reconciliation tab MUST automatically highlight eML entries that do not have a corresponding entry in eMP (based on medication matching logic)

#### Data Handling for UI Prototype

- **FR-002a**: System MUST load patient and medication data from pre-configured JSON fixture files containing 2-3 sample patients
- **FR-002b**: JSON fixtures MUST include diverse scenarios: patient with many medications, patient with few medications, patient with empty eML/eMP
- **FR-001q**: For UI prototype purposes, medication data (eML/eMP) beyond patient demographics MUST come from JSON fixtures (no real TI backend calls)

#### Medication Data Display

- **FR-001**: System MUST display the electronic Medication List (eML) showing all historical prescriptions and dispensements in chronological order
- **FR-002**: System MUST visually distinguish between prescription entries (Verordnung) and dispensement entries (Abgabe) in the eML
- **FR-003**: System MUST display the electronic Medication Plan (eMP) grouped by status: active, paused, and planned
- **FR-003a**: eMP tab MUST display a toolbar above the medication table containing "Add Medication", "Export to PDF", and "Run AMTS Check" buttons
- **FR-003b**: Each medication row in the eMP table MUST display inline action icons for edit, pause/reactivate, and delete operations
- **FR-003c**: Row-level action icons MUST be appropriate to the medication status (e.g., "pause" for active medications, "reactivate" for paused medications)
- **FR-004**: System MUST display dosage schema in both structured format (e.g., "1-0-1-0") and free text for each eMP entry
- **FR-005**: System MUST provide a medication search capability using PZN (Pharmazentralnummer) for medication selection
- **FR-006**: System MUST implement duplicate detection when adding new medications by comparing active ingredients (ATC/ASK codes) or PZN
- **FR-006a**: When duplicates are detected, system MUST display a modal dialog blocking further action until user decides
- **FR-006b**: Duplicate detection modal MUST show side-by-side comparison of new entry and existing entry with medication name, strength, and dosage
- **FR-006c**: Duplicate detection modal MUST provide radio buttons for "Update existing entry" and "Create new entry" plus a Continue button
- **FR-007**: System MUST present duplicate match results with side-by-side comparison and options to update existing entry or create new entry
- **FR-008**: System MUST allow creation of new eMP entries with required fields: medication identifier (PZN), dosage, intake instructions, indication, and entry type (self-medication vs. prescription)
- **FR-009**: System MUST allow editing of existing eMP entries including dosage and intake instructions
- **FR-010**: System MUST maintain medication plan version history when entries are edited to preserve chronology
- **FR-011**: System MUST allow status changes for eMP entries: active ↔ paused (bidirectional), active/paused → completed (terminal), planned → active (start therapy)
- **FR-011a**: System MUST prevent any transitions FROM completed status (terminal state)
- **FR-011b**: System SHOULD allow planned → completed transition for cancelled therapies (optional: if therapy cancelled before starting)
- **FR-012**: System MUST visually distinguish paused medications (e.g., grayed out) from active medications in the eMP view
- **FR-013**: System MUST allow logical linking between eML entries (MedicationStatement) and eMP entries (MedicationRequest)
- **FR-013a**: Each eML row MUST display a link action icon
- **FR-013b**: Clicking the link icon on an unlinked eML entry MUST open a context menu/dropdown showing available eMP entries
- **FR-013c**: Linked eML entries MUST display a visual indicator (badge or icon) showing they are linked
- **FR-013d**: Clicking the link icon on a linked eML entry MUST show an "Unlink" option in the context menu
- **FR-014**: System MUST identify and highlight discrepancies between eML and eMP (medications in history but not in plan)
- **FR-015**: System MUST provide a reconciliation view (split-view or comparison mode) to visualize eML/eMP gaps
- **FR-016**: System MUST support transfer of eML entries to eMP with pre-filled entry form
- **FR-017** *(Out of Scope - Deferred)*: System MUST integrate with prescription workflow by providing option to add prescription data to eMP
- **FR-018** *(Out of Scope - Deferred)*: System MUST integrate with dispensing workflow by providing option to update eMP with actually dispensed product (PZN)
- **FR-019**: System MUST generate PDF export of eMP formatted according to Bundesmedikationsplan standards
- **FR-020**: System MUST provide simulated AMTS (medication therapy safety) check functionality that displays warnings for potential interactions
- **FR-027**: System MUST include organization identification (Telematik-ID, Name) for the requesting healthcare organization
- **FR-028**: System MUST handle empty states gracefully when patient has no eML or eMP data
- **FR-029**: System MUST allow free-text medication entry as fallback when PZN search returns no results
- **FR-030**: System MUST display medication entries with incomplete data along with warning indicators

### Data Model Requirements

- **DMR-001**: System model MUST support Patient entity with KVNR attribute
- **DMR-002**: System model MUST support Organization entity with Telematik-ID and Name attributes
- **DMR-003**: System model MUST support MedicationEntry (eMP) with attributes: id, PZN, name, active ingredient, strength, dosage, intake instructions, indication, status (active/paused/completed), date authored
- **DMR-004**: System model MUST support MedicationListEntry (eML) with attributes: authoredDate, PZN, medicationName, entryType (prescription vs. dispensement), prescriber/pharmacy
- **DMR-005**: System model MUST support linking between eML MedicationListEntry and eMP MedicationEntry via identifier references

### Key Entities

- **Patient**: Represents the insured person (Versicherter) whose medication data is being managed. Key attribute: KVNR (Krankenversichertennummer) for unique identification.
- **Organization**: Represents the healthcare organization (hospital, practice, pharmacy) accessing the medication data. Key attributes: Telematik-ID (unique TI identifier), Name.
- **MedicationEntry (eMP)**: Represents a single line in the electronic Medication Plan describing current or planned therapy. Key attributes: medication identifier (PZN), medication name, active ingredient (Wirkstoff), dosage schema, intake instructions, treatment indication, status (active/paused/planned/completed), creation date.
- **MedicationListEntry (eML)**: Represents a single entry in the electronic Medication List showing historical prescription or dispensement event. Key attributes: event date (authoredDate), medication identifier (PZN), medication name, entryType (prescription vs. dispensement), prescriber/pharmacy names.
- **Link/Association**: Represents the logical connection between an eML entry (MedicationStatement) and an eMP entry (MedicationRequest) to show which dispensements belong to which therapy plan lines.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-002**: Healthcare providers can view a patient's complete eML (all historical prescriptions and dispensements) in under 3 seconds after entering the KVNR
- **SC-003**: Healthcare providers can view a patient's current eMP (all active, paused, and planned medications) in under 3 seconds after entering the KVNR
- **SC-004**: The duplicate detection algorithm identifies matches with at least 95% accuracy when comparing by ATC/ASK code or PZN
- **SC-005**: Healthcare providers can successfully create a new manual eMP entry in under 2 minutes including medication search and data entry
- **SC-006**: Healthcare providers can edit an existing eMP entry (dosage or instructions) and save changes in under 1 minute
- **SC-007**: The reconciliation view correctly identifies all discrepancies between eML and eMP in 100% of test cases
- **SC-008**: The UI clearly distinguishes between active, paused, and completed medications such that 90% of users can identify the status without assistance
- **SC-009**: PDF export generates a valid Bundesmedikationsplan-formatted document containing all active medications in under 5 seconds
- **SC-010**: AMTS safety check displays interaction warnings within 3 seconds of being triggered
- **SC-011**: The system handles edge cases (empty data, incomplete entries, concurrent edits) gracefully with appropriate user feedback in 100% of scenarios
