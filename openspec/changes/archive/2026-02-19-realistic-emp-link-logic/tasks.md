# Tasks

- [x] [Refactor] Rename DTOs: `MedicationListEntry` -> `MedicationStatement`, `MedicationPlanEntry` -> `MedicationRequest` <!-- id: 9 -->
- [x] [Backend] Add `medicationPlanIdentifier` and `basedOn` reference field to DTOs `MedicationStatement` and `MedicationRequest` <!-- id: 0 -->
- [x] [Backend] Implement `checkChronologyId` and optimistic locking in `MedicationService` <!-- id: 1 -->
- [x] [Backend] Implement Provenance generation (`EPAActivityProvenance`, `EMPChronologyProvenance`) in `MedicationService` <!-- id: 2 -->
- [x] [Backend] Implement `link-emp`, `unlink-emp`, `add-emp-entry`, `update-emp-entry` logic in `MedicationService` with Dosage Check and Header Validation <!-- id: 3 -->
- [x] [Backend] Refactor `getReconciliationTree` to use `medicationPlanIdentifier` (Hard Link) instead of `linkMap`
- [x] [Backend] Remove legacy code (`linkMap`, `unlinkedSet`) from `MedicationService.java`
- [x] [Backend] Expose new endpoints in `MedicationResource` and validate `X-Requesting-Organization` <!-- id: 4 -->
- [x] [Frontend] Update `Reconciliation.controller.js` with Soft-Match logic (ATC 7-digit check) <!-- id: 5 -->
- [x] [Frontend] Update controllers to use new API endpoints and send `X-Requesting-Organization` <!-- id: 6 -->
- [x] [Frontend] Debug and fix frontend regressions (Link/Unlink/Add buttons) <!-- id: 7 -->
- [x] [Tests] detailed unit tests for `MedicationService` (Link logic & Chronology check) <!-- id: 7 -->
- [x] [Fixtures] Update JSON fixtures with `medicationPlanIdentifier` and realistic DB data <!-- id: 8 -->
- [x] [UAT] Ask me to verify the feature manually and update the spec files with the outcomes <!-- id: 9 -->
- [x] [Bugfix] Fixed `chronologyId` missing in `MedicationPlan` DTO causing Edit failures (409 Conflict) <!-- id: 10 -->
- [x] [Bugfix] Fixed `AddMedicationDialog` 409 error handling to prevent false success messages <!-- id: 11 -->
- [x] [Bugfix] Fixed `JSON.parse` error on edit by returning updated plan in `updateEmpEntry` <!-- id: 12 -->
