# Tasks: ePA Medication Service UI Prototype

**Input**: Design documents from `/specs/001-epa-medication-ui/`  
**Prerequisites**: [plan.md](file:///home/dennis/git/fhir-prototype/specs/001-epa-medication-ui/plan.md), [spec.md](file:///home/dennis/git/fhir-prototype/specs/001-epa-medication-ui/spec.md), [research.md](file:///home/dennis/git/fhir-prototype/specs/001-epa-medication-ui/research.md), [data-model.md](file:///home/dennis/git/fhir-prototype/specs/001-epa-medication-ui/data-model.md), [contracts/](file:///home/dennis/git/fhir-prototype/specs/001-epa-medication-ui/contracts/)

**Organization**: Tasks grouped by user story (US0-US13) for independent implementation and testing

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies on incomplete tasks)
- **[Story]**: User story this task belongs to (US0, US1, US2, etc.)
- Exact file paths included in descriptions

## Path Conventions

Per plan.md:
- **Frontend**: `webapp/` (OpenUI5 application)
- **Backend**: `src/main/java/de/servicehealth/epa/` (Quarkus)
- **Fixtures**: `src/main/resources/fixtures/`
- **Tests**: `src/test/java/` (backend), `webapp/test/` (frontend)

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project initialization and basic application structure

- [x] T001 Create webapp/ directory structure (controller/, view/, model/, test/)
- [x] T002 Create backend package structure under src/main/java/de/servicehealth/epa/ (patient/, medication/, cardreading/)
- [x] T003 [P] Add OpenUI5 dependency configuration in webapp/manifest.json with CDN URL for v1.120+ LTS
- [x] T004 [P] Configure Quarkus pom.xml dependencies (smartcard-playground, HAPI FHIR Jakarta 7.x, Apache PDFBox 3.x)
- [x] T005 [P] Create webapp/index.html entry point
- [x] T006 [P] Create webapp/Component.js root UI component
- [x] T007 [P] Configure CORS in Quarkus application.properties for localhost:8080
- [x] T008 [P] Setup logging configuration in application.properties

**Checkpoint**: Project structure ready, dependencies configured

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core infrastructure required before ANY user story implementation

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [x] T009 Create Patient domain model in src/main/java/de/servicehealth/epa/patient/model/Patient.java
- [x] T010 Create PatientService in src/main/java/de/servicehealth/epa/patient/PatientService.java
- [x] T011 Create PatientResource JAX-RS endpoint in src/main/java/de/servicehealth/epa/patient/PatientResource.java
- [x] T012 [P] Create FixtureLoader utility (integrated into PatientService.java - fixture loading logic included)
- [x] T013 [P] Create JSON fixtures directory src/main/resources/fixtures/
- [x] T014 [P] Create sample patients.json fixture with 3 patients (X123456789, Y987654321, Z555111222)
- [x] T015 [P] Create OpenUI5 base formatter util in webapp/model/formatter.js
- [x] T016 [P] Create OpenUI5 model initialization in webapp/model/models.js
- [x] T017 Create main App.view.xml and App.controller.js

**Checkpoint**: Foundation ready - user stories can now be implemented in parallel

---

## Phase 3: User Story 0 - Patient Selection and Navigation (Priority: P1) 🎯

**Goal**: Implement home screen with KVNR search, "Read from eGK" button, recent patients sidebar

**Independent Test**: Navigate to http://localhost:8080, verify home screen displays search field, eGK button, and recent patients sidebar. Search for KVNR "X123456789", verify patient view loads with three tabs.

### Implementation for User Story 0

- [x] T018 [P] [US0] Create PatientSelection.view.xml with search input, eGK button, recent patients list
- [x] T019 [P] [US0] Create PatientSelection.controller.js with search and navigation logic
- [x] T020 [P] [US0] Implement GET /api/patients/search?kvnr={kvnr} endpoint in PatientResource.java
- [x] T021 [P] [US0] Implement GET /api/patients/recent endpoint in PatientResource.java
- [x] T022 [US0] Add session storage logic for recent patients in PatientService.java
- [x] T023 [US0] Create PatientView.view.xml container with IconTabBar (3 tabs: eML, eMP, Reconciliation)
- [x] T024 [US0] Create PatientView.controller.js with tab switching and "Switch Patient" button logic
- [x] T025 [P] [US0] Add FR-001a validation: KVNR must be 10 alphanumeric characters
- [x] T026 [P] [US0] Add FR-001c: Search via Enter key support
- [x] T027 [P] [US0] Add FR-001g: "Switch Patient" button in PatientView header
- [x] T028 [US0] Update PatientService to maintain recent patients list (max 10, localStorage persistence per FR-001h)

**Checkpoint**: Patient selection and navigation fully functional. Can search, view recent patients, navigate to patient view with tabs.

---

## Phase 4: User Story 1 - Display eML (Priority: P1)

**Goal**: Display electronic Medication List (eML) showing historical prescriptions/dispensements in chronological order

**Independent Test**: Search for patient X123456789, verify eML tab is selected by default and displays medication list ordered by authoredDate (newest first), distinguishing prescriptions from dispensements.

### Implementation forUser Story 1

- [x] T029 [P] [US1] Create MedicationEntry base model in src/main/java/de/servicehealth/epa/medication/model/MedicationEntry.java
- [x] T030 [P] [US1] Create MedicationListEntry model (extends MedicationEntry) in src/main/java/de/servicehealth/epa/medication/model/MedicationListEntry.java
- [x] T031 [P] [US1] Create MedicationList aggregate in src/main/java/de/servicehealth/epa/medication/model/MedicationList.java
- [x] T032 [P] [US1] Create medication-list-X123456789.json fixture with 5+ entries (mix of prescription/dispensement)
- [x] T033 [P] [US1] Create medication-list-Y987654321.json fixture with 2-3 entries
- [x] T034 [P] [US1] Create medication-list-Z555111222.json fixture (empty array)
- [x] T035 [US1] Create MedicationService in src/main/java/de/servicehealth/epa/medication/MedicationService.java with loadMedicationList method
- [x] T036 [US1] Create MedicationResource JAX-RS endpoint in src/main/java/de/servicehealth/epa/medication/MedicationResource.java
- [x] T037 [US1] Implement GET /api/medications/list/{kvnr} endpoint in MedicationResource.java
- [x] T038 [P] [US1] Create MedicationList.view.xml with table displaying eML entries
- [x] T039 [US1] Create MedicationList.controller.js with data loading and sorting logic
- [x] T040 [P] [US1] Add FR-001: Display in chronological order (newest first)
- [x] T041 [P] [US1] Add FR-002: Visual distinction for prescription vs dispensement (icon or badge)
- [x] T042 [P] [US1] Add empty state handling: display guidance when eML is empty (patient Z555111222)

**Checkpoint**: eML view fully functional. Displays historical medications sorted chronologically with clear prescription/dispensement distinction.

---

## Phase 5: User Story 2 - Display eMP (Priority: P1)

**Goal**: Display electronic Medication Plan (eMP) with toolbar (Add, Export, AMTS) and medication table with row-level action icons

**Independent Test**: Click "eMP (Current Plan)" tab, verify toolbar displays 3 buttons (Add Medication, Export to PDF, Run AMTS Check) and table shows active medications grouped by status with row-level icons (edit, pause, delete).

### Implementation for User Story 2

- [ ] T043 [P] [US2] Create MedicationPlanEntry model in src/main/java/de/servicehealth/epa/medication/model/MedicationPlanEntry.java
- [ ] T044 [P] [US2] Create MedicationPlan aggregate in src/main/java/de/servicehealth/epa/medication/model/MedicationPlan.java
- [ ] T045 [P] [US2] Create medication-plan-X123456789.json fixture with 10+ entries (active, paused, planned)
- [ ] T046 [P] [US2] Create medication-plan-Y987654321.json fixture with 2-3 active entries
- [ ] T047 [P] [US2] Create medication-plan-Z555111222.json fixture (empty array)
- [ ] T048 [US2] Implement loadMedicationPlan method in MedicationService.java
- [ ] T049 [US2] Implement GET /api/medications/plan/{kvnr} endpoint in MedicationResource.java
- [ ] T050 [P] [US2] Create MedicationPlan.view.xml with OverflowToolbar (3 buttons) and table
- [ ] T051 [US2] Create MedicationPlan.controller.js with data loading and button event handlers
- [ ] T052 [P] [US2] Add FR-003: Group medications by status (active, paused, planned)
- [ ] T053 [P] [US2] Add FR-003a: Toolbar with Add Medication, Export to PDF, Run AMTS Check buttons
- [ ] T054 [P] [US2] Add FR-003b: Row-level action icons (edit, pause/reactivate, delete) in table column
- [ ] T055 [P] [US2] Add FR-003c: Status-appropriate icons (pause for active, reactivate for paused)
- [ ] T056 [P] [US2] Add FR-004: Display dosage in both structured (1-0-1-0) and free text formats
- [ ] T057 [P] [US2] Add empty state handling for eMP

**Checkpoint**: eMP view fully functional. Toolbar and row actions visible, medications grouped by status.

---

## Phase 6: User Story 3 - Read Patient Data from eGK Card (Priority: P1)

**Goal**: Integrate eGK card reading via smartcard-playground library to populate patient demographics

**Independent Test**: Click "Read from eGK Card" button on home screen with eGK card in reader, verify patient data (KVNR, name, DOB) populates and patient view loads within 5 seconds.

### Implementation for User Story 3

- [ ] T058 [P] [US3] Create PatientDemographics model in src/main/java/de/servicehealth/epa/cardreading/model/PatientDemographics.java
- [ ] T059 [P] [US3] Create CardReaderService in src/main/java/de/servicehealth/epa/cardreading/CardReaderService.java
- [ ] T060 [US3] Integrate smartcard-playground EFPDReader in CardReaderService.java
- [ ] T061 [US3] Create CardReaderResource JAX-RS endpoint in src/main/java/de/servicehealth/epa/cardreading/CardReaderResource.java
- [ ] T062 [US3] Implement POST /api/cardreader/read-patient endpoint in CardReaderResource.java
- [ ] T063 [P] [US3] Add FR-021: PC/SC reader detection and HCA application selection
- [ ] T064 [P] [US3] Add FR-022: Read EF.PD file from eGK
- [ ] T065 [P] [US3] Add FR-023: Decompress BER-TLV/GZIP data
- [ ] T066 [P] [US3] Add FR-024: Parse XML and extract KVNR, name, DOB
- [ ] T067 [P] [US3] Add FR-025: Fallback to manual KVNR entry on card read failure
- [ ] T068 [P] [US3] Add edge case handling: CARD_READER_DISCONNECTED error
- [ ] T069 [P] [US3] Add edge case handling: CARD_REMOVED error during read
- [ ] T070 [P] [US3] Add edge case handling: CORRUPTED_DATA error
- [ ] T071 [US3] Wire "Read from eGK Card" button in PatientSelection.controller.js to call API endpoint
- [ ] T072 [P] [US3] Add loading indicator during card read operation
- [ ] T073 [P] [US3] Add success criterion SC-001: Complete read within 5 seconds

**Checkpoint**: eGK card reading functional. Patient data from card populates UI, errors handled gracefully.

---

## Phase 7: User Story 4 - Duplicate Detection and Matching (Priority: P2)

**Goal**: Detect duplicates when adding medication and show modal dialog with side-by-side comparison

**Independent Test**: Click "Add Medication", enter PZN for existing medication (e.g., matching X123456789's first eMP entry), verify modal dialog appears showing side-by-side comparison with radio buttons "Update existing" / "Create new".

### Implementation for User Story 4

- [ ] T074 [P] [US4] Create DuplicateMatch model in src/main/java/de/servicehealth/epa/medication/model/DuplicateMatch.java
- [ ] T075 [US4] Implement detectDuplicates method in MedicationService.java (check PZN, ATC, ASK)
- [ ] T076 [P] [US4] Add FR-006: Duplicate detection by PZN, ATC code (first 5 chars), or ASK code
- [ ] T077 [P] [US4] Add FR-006a: Modal dialog blocks action until user decides
- [ ] T078 [P] [US4] Add FR-006b: Side-by-side comparison table in modal
- [ ] T079 [P] [US4] Add FR-006c: Radio buttons "Update existing entry" / "Create new entry" + Continue button
- [ ] T080 [P] [US4] Create DuplicateDialog.fragment.xml with comparison table and radio group
- [ ] T081 [US4] Create DuplicateDialog.controller.js with modal logic and decision handling
- [ ] T082 [US4] Update POST /api/medications/plan/{kvnr}/entries to return 409 Conflict with DuplicateMatch on duplicate
- [ ] T083 [US4] Wire duplicate detection into Add Medication flow in MedicationPlan.controller.js

**Checkpoint**: Duplicate detection works. Modal shows on PZN/ATC/ASK match, user can choose action.

---

## Phase 8: User Story 5 - Reconciliation View (Priority: P3)

**Goal**: Dedicated "Reconciliation" tab with split-view showing eML/eMP discrepancies and "Add to eMP" buttons

**Independent Test**: Click "Reconciliation" tab, verify split-view layout (eML left, eMP right), eML entries without corresponding eMP entries are highlighted, clicking "Add to eMP" on highlighted entry pre-fills add medication form.

### Implementation for User Story 5

- [ ] T084 [P] [US5] Create ReconciliationItem model in src/main/java/de/servicehealth/epa/medication/model/ReconciliationItem.java
- [ ] T085 [US5] Implement reconciliation logic in MedicationService.java (compare eML vs eMP by PZN/ATC)
- [ ] T086 [US5] Implement GET /api/medications/reconciliation/{kvnr} endpoint in MedicationResource.java
- [ ] T087 [P] [US5] Add FR-001l: Reconciliation tab with split-view layout
- [ ] T088 [P] [US5] Add FR-001m: Automatically highlight eML entries missing in eMP
- [ ] T089 [P] [US5] Add FR-014: Identify discrepancies between eML and eMP
- [ ] T090 [P] [US5] Add FR-015: Reconciliation view visualizes gaps
- [ ] T091 [P] [US5] Add FR-016: Transfer eML entries to eMP with pre-filled form
- [ ] T092 [P] [US5] Create Reconciliation.view.xml with split-view (two tables side-by-side)
- [ ] T093 [US5] Create Reconciliation.controller.js with discrepancy highlighting and "Add to eMP" logic
- [ ] T094 [P] [US5] Update AddMedicationDialog to accept pre-filled data from reconciliation

**Checkpoint**: Reconciliation view functional. Shows discrepancies, allows transfer from eML to eMP.

---

## Phase 9: User Story 6 - Create Manual eMP Entry (Priority: P2)

**Goal**: "Add Medication" button opens form for manual entry with PZN search, dosage, instructions

**Independent Test**: Click "Add Medication" in eMP toolbar, verify dialog opens with PZN search field, dosage input, intake instructions, indication, entry type selection. Add new medication, verify it appears in eMP table.

### Implementation for User Story 6

- [ ] T095 [P] [US6] Create AddMedicationDialog.fragment.xml with form fields (PZN search, dosage, instructions, indication, entry type)
- [ ] T096 [US6] Create AddMedicationDialog.controller.js with form validation and submit logic
- [ ] T097 [US6] Implement PZN search/autocomplete in AddMedicationDialog (mock or fixture-based)
- [ ] T098 [US6] Implement POST /api/medications/plan/{kvnr}/entries endpoint in MedicationResource.java
- [ ] T099 [US6] Add createEntry method in MedicationService.java
- [ ] T100 [P] [US6] Add FR-008: Required fields (PZN, dosage, instructions, indication, entry type)
- [ ] T101 [P] [US6] Add PZN auto-fill logic: populate medication name, active ingredient, strength from PZN
- [ ] T102 [P] [US6] Add dosage validation: structured (1-0-1-0) or free text required
- [ ] T103 [US6] Wire "Add Medication" toolbar button to open AddMedicationDialog
- [ ] T104 [US6] Update eMP table to refresh after successful add

**Checkpoint**: Manual medication entry works. Form validates, new entries appear in eMP table.

---

## Phase 10: User Story 7 - Edit Existing eMP Entry (Priority: P2)

**Goal**: Row-level edit icon opens pre-filled form, saving creates new plan version

**Independent Test**: Click edit icon on active eMP entry, verify form opens with current values, change dosage from "1-0-1-0" to "2-0-2-0", save, verify table updates and plan version increments.

### Implementation for User Story 7

- [ ] T105 [US7] Implement PUT /api/medications/plan/{kvnr}/entries/{entryId} endpoint in MedicationResource.java
- [ ] T106 [US7] Add updateEntry method in MedicationService.java
- [ ] T107 [P] [US7] Add FR-009: Allow editing dosage and intake instructions
- [ ] T108 [P] [US7] Add plan versioning: increment MedicationPlan.version on edit
- [ ] T109 [P] [US7] Add lastModified timestamp update on edit
- [ ] T110 [US7] Wire edit icon click to open AddMedicationDialog with pre-filled data
- [ ] T111 [US7] Update AddMedicationDialog to support edit mode (PUT vs POST)
- [ ] T112 [US7] Refresh eMP table after successful edit

**Checkpoint**: Edit functionality works. Dosage/instructions editable, plan version tracks changes.

---

## Phase 11: User Story 8 - Manage Medication Status (Priority: P2)

**Goal**: Row-level pause/reactivate/delete icons change entry status, update UI accordingly

**Independent Test**: Click pause icon on active medication, verify status changes to "paused" and entry appears grayed out. Click reactivate icon, verify entry returns to active with normal styling. Click delete icon, verify confirmation dialog and entry removal.

### Implementation for User Story 8

- [ ] T113 [US8] Implement PATCH /api/medications/plan/{kvnr}/entries/{entryId}/status endpoint in MedicationResource.java
- [ ] T114 [US8] Implement DELETE /api/medications/plan/{kvnr}/entries/{entryId} endpoint in MedicationResource.java
- [ ] T115 [US8] Add changeStatus method in MedicationService.java with state machine validation
- [ ] T116 [P] [US8] Add FR-010: Pause/reactivate/complete status transitions
- [ ] T117 [P] [US8] Add FR-011: Status change validation (active→paused, paused→active, active/paused→completed)
- [ ] T118 [P] [US8] Add FR-012: Visual distinction for paused medications (grayed out styling)
- [ ] T119 [P] [US8] Add status transition validation: completed is terminal state
- [ ] T120 [US8] Wire pause icon to PATCH status=paused
- [ ] T121 [US8] Wire reactivate icon to PATCH status=active
- [ ] T122 [US8] Wire delete icon to show confirmation dialog then DELETE
- [ ] T123 [P] [US8] Add CSS styling for paused entries
- [ ] T124 [US8] Refresh eMP table after status change

**Checkpoint**: Status management works. Pause/reactivate/delete functional with proper UI feedback.

---

## Phase 12: User Story 9 - Link eML Entry to eMP Entry (Priority: P3)

**Goal**: Link icon in eML rows opens context menu to select eMP entry, linked entries show badge

**Independent Test**: In eML tab, click link icon on unlinked entry, verify context menu shows available eMP entries, select one, verify "linked" badge appears and clicking icon again shows "Unlink" option.

### Implementation for User Story 9

- [ ] T125 [US9] Implement POST /api/medications/link endpoint in MedicationResource.java
- [ ] T126 [US9] Implement DELETE /api/medications/link endpoint in MedicationResource.java
- [ ] T127 [US9] Add createLink and removeLink methods in MedicationService.java
- [ ] T128 [P] [US9] Add FR-013: Logical linking between eML (MedicationStatement.id) and eMP (MedicationRequest.id)
- [ ] T129 [P] [US9] Add FR-013a: Link action icon in each eML row
- [ ] T130 [P] [US9] Add FR-013b: Context menu on unlinked entry shows available eMP entries
- [ ] T131 [P] [US9] Add FR-013c: Linked entries display badge or icon
- [ ] T132 [P] [US9] Add FR-013d: Context menu on linked entry shows "Unlink" option
- [ ] T133 [P] [US9] Update MedicationListEntry to include linkedToPlanId field
- [ ] T134 [US9] Add link icon column to eML table in MedicationList.view.xml
- [ ] T135 [US9] Create ActionSheet/Menu component for link selection in MedicationList.controller.js
- [ ] T136 [US9] Store link associations in session (persist linkedToPlanId)
- [ ] T137 [US9] Update eML table to show linked badge when linkedToPlanId is set

**Checkpoint**: Linking works. eML entries can link to eMP entries, associations visible in UI.

---

## Phase 13: User Story 10 - Prescription Workflow Integration (Priority: P3) ⚠️ OUT OF SCOPE

**Goal**: N/A - Explicitly out of scope per clarifications

**Note**: User Story 10 (prescription workflow integration) is deferred until prescription workflow infrastructure exists. No tasks generated for this story in initial UI prototype.

---

## Phase 14: User Story 11 - Dispensing Workflow Integration (Priority: P3) ⚠️ OUT OF SCOPE

**Goal**: N/A - Explicitly out of scope per clarifications

**Note**: User Story 11 (dispensing workflow integration) is deferred until dispensing workflow infrastructure exists. No tasks generated for this story in initial UI prototype.

---

## Phase 15: User Story 12 - Export Medication Plan (PDF) (Priority: P3)

**Goal**: "Export to PDF" button generates Bundesmedikationsplan PDF for download

**Independent Test**: Click "Export to PDF" in eMP toolbar, verify PDF downloads with correct Bundesmedikationsplan layout showing all active medications with name, dosage, instructions, indication.

### Implementation for User Story 12

- [ ] T138 [US12] Implement GET /api/medications/plan/{kvnr}/export-pdf endpoint in MedicationResource.java
- [ ] T139 [US12] Create PDFGenerator service in src/main/java/de/servicehealth/epa/medication/PDFGenerator.java
- [ ] T140 [US12] Add Apache PDFBox dependency to pom.xml (if not already added in T004)
- [ ] T141 [P] [US12] Implement Bundesmedikationsplan template (header, patient demographics, medication table)
- [ ] T142 [P] [US12] Add PDF generation logic: map MedicationPlan to PDF document
- [ ] T143 [P] [US12] Set Content-Disposition header for file download
- [ ] T144 [US12] Wire "Export to PDF" toolbar button to trigger download
- [ ] T145 [P] [US12] Add error handling for PDF generation failures

**Checkpoint**: PDF export works. Clicking button downloads Bundesmedikationsplan-formatted PDF.

---

## Phase 16: User Story 13 - AMTS Safety Check (Simulation) (Priority: P4)

**Goal**: "Run AMTS Check" button triggers simulated safety analysis showing interaction warnings

**Independent Test**: In eMP with multiple active medications, click "Run AMTS Check", verify dialog/message box shows either "No interactions detected" or warnings like "Interaction between Ibuprofen and Aspirin".

### Implementation for User Story 13

- [ ] T146 [P] [US13] Create AMTSSimulator.js frontend module in webapp/model/AMTSSimulator.js
- [ ] T147 [P] [US13] Define hardcoded interaction rules (e.g., Ibuprofen + Aspirin → warning)
- [ ] T148 [P] [US13] Add checkInteractions method that returns array of warnings
- [ ] T149 [US13] Wire "Run AMTS Check" toolbar button to call AMTSSimulator.checkInteractions
- [ ] T150 [US13] Display results in MessageBox or Dialog (green for no issues, yellow/red for warnings)
- [ ] T151 [P] [US13] Add at least 3 sample interaction rules for demo purposes

**Checkpoint**: AMTS simulation works. Shows either success message or warnings based on active medications.

---

## Phase 17: Polish & Cross-Cutting Concerns

**Purpose**: Final touches and quality improvements

- [ ] T152 [P] Create empty state components for all views (empty eML, empty eMP, no recent patients)
- [ ] T153 [P] Add loading spinners/indicators for all async operations
- [ ] T154 [P] Add error message display for API failures (network errors, 404, 500)
- [ ] T155 [P] Add success toast notifications for CRUD operations
- [ ] T156 [P] Implement responsive design adjustments for smaller screens
- [ ] T157 [P] Add keyboard navigation support (Ent key for search, tab navigation)
- [ ] T158 [P] Add ARIA labels for accessibility (screen reader support)
- [ ] T159 [P] Review and fix any console warnings/errors
- [ ] T160 [P] Update README.md with setup and run instructions (or link to quickstart.md)

**Checkpoint**: Application polished, error handling complete, accessibility improved.

---

## Dependencies & Execution Order

### Story Dependency Graph

```
Setup (T001-T008) → Foundational (T009-T017)
                            ↓
        ┌───────────────────┴──────────────────────┐
        ↓                                          ↓
    US0 (T018-T028)                           US1 (T029-T042)
        ↓                                          ↓
    US3 (T058-T073)                           US2 (T043-T057)
                                                   ↓
                                ┌──────────────────┴──────────┬─────────────┬──────────────────┐
                                ↓                             ↓             ↓                  ↓
                           US6 (T095-T104)              US7 (T105-T112) US8 (T113-T124) US4 (T074-T083)
                                                             ↓
                                                        US5 (T084-T094)
                                                             ↓
                                                        US9 (T125-T137)
                                                             ↓
                                                    ┌────────┴────────┐
                                                    ↓                 ↓
                                              US12 (T138-T145)  US13 (T146-T151)
                                                    ↓
                                              Polish (T152-T160)
```

**Critical Path**: Setup → Foundational → US0 → US2 → US6 → US5 → Polish

**Parallelizable Stories** (after Foundational):
- US0 (Patient Selection) can run parallel with US1 (eML Display)
- US3 (eGK Reading) depends on US0 (patient selection UI)
- US6, US7, US8, US4 can run in parallel (all depend on US2)
- US12, US13 can run in parallel (both depend on US2)

### Parallel Execution Examples

**After Foundational phase complete**, teams can work on:

**Team A**: US0 (Patient Selection) - Tasks T018-T028  
**Team B**: US1 (eML Display) - Tasks T029-T042

**After US2 (eMP Display) complete**:

**Team A**: US6 (Add Medication) - Tasks T095-T104  
**Team B**: US7 (Edit Entry) - Tasks T105-T112  
**Team C**: US8 (Status Management) - Tasks T113-T124  
**Team D**: US4 (Duplicate Detection) - Tasks T074-T083

---

## MVP Recommendation 🎯

**Suggested MVP Scope**: Complete through User Story 3 (P1 stories only)

**MVP Tasks**: T001-T073 (73 tasks)

**MVP Delivers**:
- Patient selection via KVNR search and eGK card reading
- Display eML (medication history)
- Display eMP (current medication plan) with toolbar
- Basic navigation and UI structure

**Rationale**: Completes all P1 features. Provides functional prototype demonstrating core ePA medication UI capabilities and real eGK integration. All subsequent stories build on this foundation.

---

## Task Summary

**Total Tasks**: 160  
**Setup**: 8 tasks  
**Foundational**: 9 tasks  
**User Stories**: 138 tasks across 11 stories (US0-US9, US12-US13)  
**Polish**: 5 tasks  

**Out of Scope**: US10, US11 (explicitly deferred per spec clarifications)

**Parallelizable Tasks**: 98 tasks marked [P] (61%)

**Independent Test Criteria**: Defined for each user story phase

---

## Implementation Strategy

1. **Start with MVP** (T001-T073): Deliver P1 features first
2. **Incremental delivery**: Complete one user story at a time, test independently
3. **Parallel teams**: After Foundational, split work across parallelizable stories
4. **Test as you go**: Each story has independent test criteria - verify before moving on
5. **Polish last**: Cross-cutting concerns in final phase after all features work

**Next Step**: Begin with T001 (Create webapp/ directory structure)
