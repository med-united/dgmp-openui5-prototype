## Context
The current implementation of bridging the Medication List (eML) and Medication Plan (eMP) relies on an in-memory map which is not persistent or standard-compliant. This change introduces a robust, identifier-based linking mechanism using `MedicationPlanIdentifier` and standard FHIR provenance patterns for audit and versioning.

## Goals / Non-Goals
**Goals:**
- Replace in-memory `linkMap` with permanent `medicationPlanIdentifier` in DTOs.
- Implement correct FHIR-like operations for linking, adding, and updating entries.
- Ensure data consistency via optimistic locking using `EMPChronologyProvenance`.
- Implement compliant Provenance generation (Activity & Chronology).
- Improve frontend reconciliation with "Soft-Match" logic.

**Non-Goals:**
- Full FHIR resource validation (we stick to DTOs for the prototype).
- Cryptographic verification of `X-Requesting-Organization` (presence check only).

## Decisions

### 1. Data Model Extension
We will extend existing DTOs instead of introducing full FHIR resources.
- `MedicationStatement` (was `MedicationListEntry`): Add `medicationPlanIdentifier` (String/UUID) AND `basedOn` (String/Reference).
- `MedicationRequest` (was `MedicationPlanEntry`): Add `medicationPlanIdentifier` (String/UUID).
This minimizes refactoring overhead while enabling the core logic.

### 2. API Design
We define 4 distinct operations in `MedicationResource`:
- `link-emp`: Sets identifier on `MedicationStatement`.
- `unlink-emp`: Clears identifier.
- `add-emp-entry`: Creates `MedicationRequest` with new ID and links `MedicationStatement`.
- `update-emp-entry`: Updates `MedicationRequest` and links `MedicationStatement`.

All operations MUST:
- Validated `X-Requesting-Organization` header (Structure/Profile) -> 422 if invalid.
- Validate Dosage (`renderedDosageInstruction` Check) -> 400 if invalid.
- Generate `EPAActivityProvenance`.
- Generate `EMPChronologyProvenance`.

### 3. Concurrency Control (Chronology)
For `update-emp-entry`, we use Optimistic Locking.
- Client runs `$provide-prescription-data` to get `MedicationPlan`.
- Response includes `EMPChronologyProvenance` with an ID.
- Client defines this ID as `acknowledgedChronologyId` in the update request.
- Backend loads current `EMPChronologyProvenance`.
- If IDs mismatch -> `409 Conflict`.
- If match -> Proceed and generate NEW `EMPChronologyProvenance`.

### 4. Logic Distribution
- **Backend (Strict)**: Only "Hard Links" via `medicationPlanIdentifier` AND `basedOn` reference. `MedicationStatement` DTO MUST include a `basedOn` field (mapped to `MedicationRequest` reference).
- **Validation**: Strict validation of `X-Requesting-Organization` (format) and Dosage (structure vs text).
- **Frontend (Heuristic)**: "Soft-Match" visualization in Reconciliation View.
  - Primary Match: ATC Code (first 7 digits) - covers aut-idem / generic substitution.
  - Secondary Match: PZN.

## Risks / Trade-offs
- **Risk**: "Soft-Match" might be ambiguous.
- **Mitigation**: It is purely visual (yellow warning) and requires user confirmation to become a "Hard Link" (green).
