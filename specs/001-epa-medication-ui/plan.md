# Implementation Plan: ePA Medication Service UI Prototype

**Branch**: `001-epa-medication-ui` | **Date**: 2026-02-14 | **Spec**: [spec.md](file:///home/dennis/git/dgmp-openui5-prototype/specs/001-epa-medication-ui/spec.md)

**Input**: Feature specification from `/specs/001-epa-medication-ui/spec.md`

## Summary

Implement an OpenUI5 UX prototype for ePA Medication Service that enables healthcare providers to view, manage, and reconcile patient medications through three core views: electronic Medication List (eML - historical data), electronic Medication Plan (eMP - current therapy plan), and a Reconciliation view for gap analysis. The prototype uses JSON fixtures for patient and medication data demonstration. Core features include patient selection, tab-based navigation, CRUD operations for medication plans, duplicate detection, linking between historical and planned medications, PDF export, and simulated AMTS safety checks.

**Technical approach**: Web-based single-page application using OpenUI5 for the frontend with minimal Quarkus REST backend to serve mock data from JSON fixtures.

## Technical Context

**Language/Version**: Java 17+, JavaScript (OpenUI5/SAPUI5)  
**Primary Dependencies**: Quarkus 3.x, OpenUI5, HAPI FHIR (Jakarta-aligned), smartcard-playground library (for eGK reading)  
**Storage**: JSON fixture files for medication demo data (no database required for prototype)  
**Testing**: JUnit 5 (backend), QUnit/OPA5 (OpenUI5 frontend), RestAssured (API integration)  
**Target Platform**: Web browser (modern browsers: Chrome, Firefox, Edge) + PC/SC card reader for eGK  
**Project Type**: Web application (frontend + minimal backend)  
**Performance Goals**: UI responsiveness <200ms for view transitions, eGK read within 5 seconds  
**Constraints**: UI-centric prototype - no real TI backend integration, no persistence beyond session storage  
**Scale/Scope**: 3 main views (eML/eMP/Reconciliation), ~15 UI screens/dialogs, 2-3 sample patients in fixtures

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

### ✅ I. FHIR TI Compliance

**Status**: PASS (with prototype scope clarification)

- eML/eMP data models will conform to ePA Medication Service IG v1.3.0 structures
- JSON fixtures will use valid FHIR MedicationStatement and MedicationRequest resource formats
- **Prototype Exception**: No actual TI backend calls - validation is structural only, not runtime
- **Justification**: This is explicitly a UI-centric prototype per spec. Real TI integration deferred to later phase.

### ✅ II. dgMP Function Completeness

**Status**: PASS

- All P1-P2 user stories have complete UI implementations (no stubs)
- eGK card reading uses real smartcard-playground implementation (not mocked)
- P3 features (reconciliation, linking, PDF export, AMTS) are fully functional simulations
- P3 workflow integrations (prescription/dispensing - User Stories 10-11) explicitly out of scope per clarifications

### ✅ III. Comprehensive Test Coverage

**Status**: PASS

- Backend services (card reading, API endpoints) will have JUnit tests
- Frontend components will have QUnit unit tests
- Integration tests will verify API contracts using RestAssured
- UI workflows will have OPA5 journey tests for critical paths (patient selection → eMP CRUD → reconciliation)

### ✅ IV. Code Readability and Structure

**Status**: PASS

- Package structure: `de.servicehealth.epa.medication` (backend), `epa/medication` (OpenUI5 modules)
- Frontend follows OpenUI5 MVC pattern with clear separation (views/, controllers/, models/)
- Backend follows Quarkus Resource → Service → Repository pattern (minimal for fixtures)

### ✅ V. Domain-Driven Design

**Status**: PASS

- Domain concepts: Patient, MedicationList (eML), MedicationPlan (eMP), MedicationEntry, Reconciliation
- Packages organized by domain concern: `patient`, `medication`, `cardreading`
- Uses ubiquitous language from ePA spec (eML, eMP, KVNR, PZN, Wirkstoff, etc.)

### ✅ VI. Security and Data Privacy

**Status**: PASS

- No hardcoded patient data - all data in external JSON fixtures
- eGK reading follows PC/SC standard security (PIN verification for private keys handled by card)
- Session storage only - no persistent PHI storage in prototype
- Audit logging for eMP modifications (console logging for prototype, extensible to real audit trail)

**Overall Gate Status**: ✅ PASS - No constitution violations. Prototype scope exceptions properly justified.

## Project Structure

### Documentation (this feature)

```text
specs/001-epa-medication-ui/
├── spec.md              # Feature specification (input - already exists)
├── plan.md              # This file (/speckit.plan command output)
├── research.md          # Phase 0 output (technical decisions)
├── data-model.md        # Phase 1 output (entities and relationships)
├── quickstart.md        # Phase 1 output (setup and run instructions)
└── contracts/           # Phase 1 output (API contracts)
    ├── openapi.yaml     # REST API specification
    └── mock-data.json   # Sample fixture structure
```

### Source Code (repository root)

```text
# Web application structure (OpenUI5 frontend + Quarkus backend)

webapp/                           # OpenUI5 application
├── index.html                    # Application entry point
├── manifest.json                 # OpenUI5 app descriptor
├── Component.js                  # Root UI component
├── controller/
│   ├── App.controller.js         # Main app controller
│   ├── PatientSelection.controller.js    # Home screen / patient search
│   ├── PatientView.controller.js         # Patient container with tabs
│   ├── MedicationList.controller.js      # eML tab
│   ├── MedicationPlan.controller.js      # eMP tab (with toolbar)
│   ├── Reconciliation.controller.js      # Reconciliation tab
│   ├── AddMedication.controller.js       # Add/Edit medication dialog
│   └── DuplicateDialog.controller.js     # Duplicate detection modal
├── view/
│   ├── App.view.xml              # Main view
│   ├── PatientSelection.view.xml
│   ├── PatientView.view.xml      # Tab container
│   ├── MedicationList.view.xml
│   ├── MedicationPlan.view.xml
│   ├── Reconciliation.view.xml
│   ├── AddMedicationDialog.fragment.xml
│   └── DuplicateDialog.fragment.xml
├── model/
│   ├── formatter.js              # Value formatters
│   └── models.js                 # Model initialization
└── test/
    ├── unit/                     # QUnit tests
    └── integration/              # OPA5 journey tests

src/main/java/de/servicehealth/epa/
├── patient/
│   ├── PatientResource.java      # REST endpoints for patient operations
│   ├── PatientService.java       # Business logic (fixture loading, KVNR search)
│   └── model/
│       └── Patient.java          # Patient domain model
├── medication/
│   ├── MedicationResource.java   # REST endpoints for eML/eMP
│   ├── MedicationService.java    # Business logic (CRUD, duplicate detection)
│   ├── FixtureLoader.java        # JSON fixture data loader
│   └── model/
│       ├── MedicationEntry.java  # Base medication entry
│       ├── MedicationList.java   # eML aggregate
│       └── MedicationPlan.java   # eMP aggregate
├── cardreading/
│   ├── CardReaderResource.java   # REST endpoint for eGK read
│   ├── CardReaderService.java    # Integration with smartcard-playground
│   └── model/
│       └── PatientDemographics.java  # Patient data from eGK
└── fixtures/                     # JSON fixture files
    ├── patients.json             # 2-3 sample patients
    ├── medication-list-[kvnr].json    # eML for each patient
    └── medication-plan-[kvnr].json    # eMP for each patient

src/test/java/de/servicehealth/epa/
├── patient/
│   ├── PatientResourceTest.java
│   └── PatientServiceTest.java
├── medication/
│   ├── MedicationResourceTest.java
│   ├── MedicationServiceTest.java
│   └── DuplicateDetectionTest.java
└── cardreading/
    └── CardReaderServiceTest.java
```

**Structure Decision**: Chosen **Web application** structure (Option 2 from template) due to clear separation between OpenUI5 frontend (`webapp/`) and Quarkus backend (`src/main/java/`). This aligns with:
- Frontend-focused prototype (bulk of complexity in UI/UX)
- Minimal backend for fixture serving and eGK integration
- Standard OpenUI5 project layout in `webapp/`
- Standard Quarkus/Maven Java project in `src/`

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

*No violations - this section is empty.*

---

**📋 Phase 0 (Research) begins below. See `research.md` for details.**  
**📋 Phase 1 (Design & Contracts) follows. See `data-model.md`, `quickstart.md`, and `contracts/` for details.**  
**📋 Phase 2 (Tasks) is NOT generated by this command - run `/speckit.tasks` after plan approval.**
