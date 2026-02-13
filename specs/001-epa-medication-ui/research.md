# Research & Technical Decisions: ePA Medication Service UI

**Feature**: ePA Medication Service UI Prototype  
**Phase**: 0 (Research & Architecture)  
**Date**: 2026-02-14

## Purpose

This document captures key technical decisions, architectural choices, and research findings that inform the implementation plan for the ePA Medication Service UI prototype.

## Key Technical Decisions

### 1. Frontend Framework: OpenUI5

**Decision**: Use SAPUI5/OpenUI5 for the frontend framework

**Rationale**:
- **Constitution Mandated**: OpenUI5 is explicitly listed in constitution as mandated frontend technology
- **Enterprise UI Patterns**: Provides rich set of enterprise controls (tables, forms, toolbars, tabs) needed for medication management
- **German Healthcare Adoption**: Widely used in German healthcare IT (gematik ecosystem familiarity)
- **MVC Architecture**: Built-in Model-View-Controller pattern supports clean separation of concerns
- **FHIR JSON Binding**: JSON model binding works well with FHIR resource structures
- **Responsive Design**: Automatically adapts to different screen sizes

**Alternatives Considered**:
- React/Vue.js: More modern but not constitution-mandated; would require additional learning curve
- Angular: Heavy framework, similar complexity to OpenUI5 but less healthcare domain adoption in Germany

**Implementation Approach**:
- Use OpenUI5 v1.120+ (LTS version compatible with modern browsers)
- Load from CDN (avoids large webjar issues mentioned in constitution dependency management)
- Structure as single-page application with Component-based architecture

---

### 2. Backend Architecture: Minimal Quarkus REST API

**Decision**: Implement minimal Quarkus backend serving JSON fixtures + eGK card reading integration

**Rationale**:
- **Prototype Scope**: Spec clarifications explicitly state "UI-centric prototype without full backend logic"
- **Constitution Compliance**: Quarkus 3.x is mandated technology; maintains consistency with project stack
- **eGK Integration**: Real card reading requires Java-based smartcard-playground library integration (not browser-based)
- **CORS Support**: Quarkus provides easy CORS configuration for local development
- **Fast Startup**: Quarkus dev mode supports rapid iteration during UI development

**Alternatives Considered**:
- JSON Server (Node.js): Simple mock API but can't integrate with Java smartcard library
- Frontend-only (no backend): Cannot implement real eGK card reading (requires PC/SC access)
- Full Spring Boot: Heavier stack than needed for prototype scope

**Implementation Approach**:
- JAX-RS resources for: `/api/patients`, `/api/medications`, `/api/cardreader`
- Service layer loads JSON fixtures from `src/main/resources/fixtures/`
- CardReaderService wraps smartcard-playground calls for `EFPDReader` integration
- Minimal state management (session-based, no database)

---

### 3. Data Strategy: JSON Fixtures + Real eGK

**Decision**: Pre-loaded JSON fixtures for medication data, real eGK reading for patient demographics

**Rationale**:
- **Clarification Answer 3**: User selected "Option B - Pre-loaded JSON fixtures with 2-3 sample patients" with note that "eGK reading is real"
- **UI Testing**: Fixtures allow comprehensive UI scenario testing without backend complexity
- **Demo Realism**: Real eGK reading demonstrates actual TI integration capability
- **Separation of Concerns**: Patient data (from card) vs medication data (demo fixtures) clearly separated

**Alternatives Considered**:
- Hardcoded mock data: Less flexible for testing different scenarios
- Full TI backend: Out of scope for UI-centric prototype
- Session-based only: Requires eGK card for every demo (inconvenient)

**Implementation Approach**:
- Create `fixtures/patients.json` with 2-3 diverse patients:
  - Patient A: Many medications (10+) to test scrolling, pagination
  - Patient B: Few medications (2-3) to test normal case
  - Patient C: Empty eML/eMP to test empty states
- Each fixture file uses valid FHIR MedicationStatement/MedicationRequest JSON structure
- eGK read populates patient fields (KVNR, name, DOB) even if not in fixtures
- Medication data for newly read patients shows empty state (fixtures don't exist yet)

---

### 4. UI Navigation Pattern: Three-Tab Design

**Decision**: Patient view uses tab-based navigation with three tabs: eML | eMP | Reconciliation

**Rationale**:
- **Clarification Answers 2 & 5**: User selected tab-based navigation and dedicated Reconciliation tab
- **Context Preservation**: Tabs keep user in patient context without page reloads
- **Progressive Disclosure**: Separates read-only history (eML) from active management (eMP) from analysis (Reconciliation)
- **Enterprise Pattern**: Familiar tabbed interface pattern in healthcare applications

**Alternatives Considered**:
- Single page with sections: Requires scrolling, harder to focus on one aspect
- Separate routes: Loses patient context, more navigation overhead
- Side-by-side split view: Cluttered on smaller screens

**Implementation Approach**:
- `sap.m.IconTabBar` in `PatientView.view.xml`
- Three tab items: `eML (History)`, `eMP (Current Plan)`, `Reconciliation`
- Default selection: eML tab  (FR-001j from spec)
- Each tab loads corresponding controller and view

---

### 5. Medication CRUD UI: Toolbar + Row Actions

**Decision**: eMP tab displays toolbar with action buttons + row-level action icons

**Rationale**:
- **Clarification Answer 4**: User selected "Option A - Toolbar above table + row-level action icons"
- **Discoverability**: Toolbar "Add Medication" button is always visible
- **Efficiency**: Row-level icons (edit/pause/delete) provide one-click access to common actions
- **Fiori Guidelines**: Aligns with SAP Fiori design patterns for action placement

**Alternatives Considered**:
- FAB (Floating Action Button): Less discoverable, unusual in Enterprise UI
- Toolbar only with selection: Extra click to select row before acting
- Context menu only: Hidden actions, poor discoverability

**Implementation Approach**:
- `sap.m.OverflowToolbar` in eMP view with buttons:
  - "Add Medication" (primary action)
  - "Export to PDF"
  - "Run AMTS Check"
- `sap.m.Table` rows include `sap.m.Button` icons in column:
  - Edit icon (for all entries)
  - Pause icon (for active) / Reactivate icon (for paused)
  - Delete icon (with confirmation dialog)

---

### 6. Duplicate Detection: Modal Dialog

**Decision**: Show duplicate matches in blocking modal dialog with side-by-side comparison

**Rationale**:
- **Clarification Answer 6**: User selected "Modal dialog with match results"
- **Safety Critical**: Medication duplicates pose safety risk; modal forces conscious decision
- **Clear Comparison**: Dedicated modal space allows side-by-side view of both entries
- **Workflow Blocking**: Prevents accidental creation of duplicate without review

**Alternatives Considered**:
- Inline banner: Easy to dismiss or ignore
- Side panel: Can be hidden or overlooked
- Wizard step: Extra navigation overhead for common case (no duplicates)

**Implementation Approach**:
- `DuplicateDialog.fragment.xml` with `sap.m.Dialog`
- Triggered on PZN selection or active ingredient match during Add/Edit
- Dialog content: Two-column comparison table (new vs existing)
- Radio button group: "Update existing entry" / "Create new entry"
- Continue button proceeds based on selection

---

### 7. Link/Unlink Mechanism: Row Icon + Context Menu

**Decision**: eML rows have link icon that opens context menu to select target eMP entry

**Rationale**:
- **Clarification Answer 7**: User selected "Row-level action icon in eML with context menu"
- **Discoverability**: Icon visible on every eML row
- **Context-Aware**: Icon shows different state when linked vs unlinked
- **Intuitive Flow**: Link action starts from historical entry (eML) and points to plan (eMP)

**Alternatives Considered**:
- Multi-select with toolbar: Requires two selections, more clicks
- Drag-and-drop only: Not accessible, hard to discover
- Dedicated linking mode: Extra mode switching overhead

**Implementation Approach**:
- eML table includes link/chain icon column
- Unlinked entries: Click opens `sap.m.ActionSheet` with list of eMP entries to link to
- Linked entries: Icon shows "linked" badge + click shows "Unlink" action
- Link data stored in session (MedicationStatement.id → MedicationRequest.id mapping)

---

### 8. eGK Card Reading Integration

**Decision**: Backend CardReaderService wraps smartcard-playground library for PC/SC access

**Rationale**:
- **Existing Implementation**: smartcard-playground project already has working `EFPDReader` implementation
- **Browser Limitation**: Web browsers cannot directly access PC/SC smart card readers (security restriction)
- **Real Hardware**: User Story 3 and clarifications specify real eGK reading, not simulation
- **Proven Code**: Reuse existing tested implementation from `EFPDReaderImpl.java`

**Alternatives Considered**:
- Browser WebUSB API: Does not support PC/SC protocol
- Simulated card data: Not aligned with spec requirement for real eGK reading
- Separate card reading service: Adds deployment complexity

**Implementation Approach**:
- Backend endpoint: `POST /api/cardreader/read-patient`
- CardReaderService dependencies: `javax.smartcardio`, `com.payneteasy.tlv` (from smartcard-playground)
- Response: PatientDemographics POJO with KVNR, name, DOB extracted from EF.PD
- Frontend: Button "Read from eGK Card" triggers API call, shows loading indicator
- Error handling: Card reader errors (disconnected, removed card, corrupted data) from spec edge cases

---

### 9. PDF Export: Bundesmedikationsplan Format

**Decision**: Generate PDF on backend using FHIR data → Bundesmedikationsplan template

**Rationale**:
- **User Story 12**: Export to PDF in Bundesmedikationsplan format
- **Server-Side Generation**: PDF generation libraries (iText, Apache PDFBox) are Java-based
- **Format Compliance**: Bundesmedikationsplan has specific layout requirements (logo, header, medication table)
- **Print Ready**: Server-generated PDF ensures consistent formatting

**Alternatives Considered**:
- Browser print CSS: Less control over exact format, browser-dependent
- Client-side PDF.js: Limited formatting capabilities for complex layouts

**Implementation Approach**:
- Backend endpoint: `GET /api/medications/export-pdf?kvnr={kvnr}`
- Use Apache PDFBox for PDF generation
- Template: Bundesmedikationsplan layout (header, patient demographics, medication table)
- Returns `application/pdf` with proper Content-Disposition header for download

---

### 10. AMTS Safety Check: Simulated Warnings

**Decision**: Frontend-based simulated AMTS check with hardcoded interaction rules

**Rationale**:
- **User Story 13**: Explicitly marked as "simulation/demonstration feature" (P4 priority)
- **No Real Service**: Actual AMTS services (gematik) not in scope for UI prototype
- **Demo Capability**: Showcases UI for displaying safety warnings without backend complexity

**Alternatives Considered**:
- Mock backend service: Adds unnecessary backend code for simulation
- No AMTS at all: Misses opportunity to demonstrate safety warning UI

**Implementation Approach**:
- Frontend `AMTSSimulator.js` module with simple rule engine:
  - Example rules: "Ibuprofen + Aspirin" → interaction warning
  - Check active medications in eMP for known rule matches
- "Run AMTS Check" button triggers simulation
- Display results in `sap.m.MessageBox` or dedicated results dialog
- Green "No issues" or yellow/red warnings with interaction details

---

## Technology Stack Summary

| Layer | Technology | Version | Purpose |
|-------|-----------|---------|---------|
| **Frontend** | OpenUI5 | 1.120+ | UI framework (CDN-loaded) |
| **Backend** | Quarkus | 3.11+ | REST API server |
| **Runtime** | Java | 17+ | Backend execution |
| **FHIR** | HAPI FHIR | 7.x (Jakarta) | FHIR resource parsing/validation |
| **Card Reading** | smartcard-playground | Current | eGK PC/SC integration |
| **Testing (Backend)** | JUnit 5, RestAssured | Latest | Unit and API tests |
| **Testing (Frontend)** | QUnit, OPA5 | OpenUI5 bundled | Unit and journey tests |
| **Build** | Maven | 3.8+ | Build and dependency management |
| **PDF Generation** | Apache PDFBox | 3.x | PDF export |

---

## Open Questions / Risks

### Low Risk
- **Bundesmedikationsplan Layout Details**: Exact specification for PDF format may need refinement during implementation
- **OpenUI5 CDN Version**: Specific 1.120.x patch version to be determined (use latest stable)
- **eGK Card Availability**: Demo requires physical eGK card + reader for full testing

### Mitigations
- Bundesmedikationsplan: Use simplified layout for prototype; can be enhanced later with exact specifications
- OpenUI5 Version: Pin to specific known-stable version after initial testing
- eGK Testing: Maintain JSON fixtures as primary testing data; eGK is additional validation

---

## Next Steps (Phase 1)

1. **Data Model Design** (`data-model.md`): Define entities, relationships, validation rules
2. **API Contracts** (`contracts/openapi.yaml`): Specify REST endpoints and request/response formats
3. **Quickstart Guide** (`quickstart.md`): Document setup, build, and run steps for development
4. **Agent Context Update**: Run `.specify/scripts/bash/update-agent-context.sh` to record technology choices

---

**Research Complete**: All technical unknowns resolved. Ready for Phase 1 (Design & Contracts).
