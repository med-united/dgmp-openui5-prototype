## Problem

The prototype models eMP and eML data in a bespoke flat JSON format that mimics FHIR concepts (field names like `medicationPlanIdentifier`, `basedOn`) but is not actual FHIR R4. This means the UI, mock server, and fixtures diverge structurally from the real ePA Medication Service — a native FHIR Data Service — making the prototype an unreliable reference for frontend development and clinical validation.

## Options Considered

1. **Document the mapping only** — keep the custom format, add a FHIR-to-flat mapping guide. Rejected: doesn't fix binding path drift and doesn't enable FHIR-aware tooling.
2. **Partial FHIR — fixtures only** — rewrite fixtures as FHIR Bundles but keep the custom REST API and JSONModel. Rejected: controllers still bind to flat paths; the client never exercises real FHIR structures.
3. **Full FHIR R4 end-to-end with `openui5-fhir`** ← chosen. Fixtures, mock server endpoints, and client model all migrate to genuine FHIR R4 in three phases.

## Decision

Migrate the prototype end-to-end to FHIR R4 resources and `openui5-fhir` `FHIRModel`:

- **Fixtures**: `medication-plan-*.json`, `medication-list-*.json`, `patients.json` rewritten as FHIR R4 `Bundle` resources conforming to the ePA Medication Service IG profiles
- **Mock server**: FHIR REST endpoints at `/fhir/` base URL; mutations via FHIR Batch Bundles
- **Client**: `JSONModel` replaced by `openui5-fhir` `FHIRModel`; controller bindings use FHIR slicing paths (no positional array indices)

## Scope

**In scope**: `MedicationRequest` (eMP), `MedicationStatement` (eML prescriptions), `MedicationDispense` (eML dispensements, `GEM_ERP_PR_MedicationDispense`), `Patient`, `EPAActivityProvenance`, `EMPChronologyProvenance`, `Medication`

**Out of scope**: SMART on FHIR auth, `CapabilityStatement`, `AMTSSimulator.js` refactoring, PDF export

## High-Level Impact

- All use cases US0–US12 preserved without behaviour change
- `patients.json` migrated to FHIR `Patient` in this change (required for reverse chaining)
- `AddMedicationDialog` name search moves to `GET /fhir/Medication?name:contains=<term>`
- `formatter.js` updated for FHIR `CodeableConcept` / `Dosage` inputs
- `openui5-fhir` added as a project dependency; OpenUI5 CDN URL pinned

→ **All technical decisions, endpoint contracts, phase plans, derived-array contracts, test strategy, and developer workflow are in [`design.md`](design.md).**
