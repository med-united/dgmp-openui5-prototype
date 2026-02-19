## ADDED Requirements

### Requirement: Application Overview Section in README
The `README.md` SHALL contain a concise "What is this?" section describing the current state of the application: what it simulates, who uses it, and the underlying ePA/dgMP context.

#### Scenario: Reader understands the app at a glance
- **WHEN** a developer or reviewer opens `README.md`
- **THEN** the file SHALL contain a short paragraph (3–5 sentences) covering: the ePA Medication Service context, the prototype's role, and the main actors (physician, pharmacist)

### Requirement: dgMP Use Case Coverage Table in README
The `README.md` SHALL contain a table listing the implemented dgMP use cases (US0–US12 from `specs/001-epa-medication-ui/spec.md`) with an implementation status for each.

#### Scenario: Use case is fully implemented
- **WHEN** a use case is fully implemented end-to-end (frontend + backend)
- **THEN** its row in the table SHALL be marked ✅ with a short description

#### Scenario: Use case is a placeholder
- **WHEN** a use case has UI surface but no real backend logic (e.g., PDF export shows a toast)
- **THEN** its row SHALL be marked ⚠️ with a "(placeholder)" note

#### Scenario: Use case is deferred
- **WHEN** a use case is explicitly out of scope (US9, US10)
- **THEN** its row SHALL be marked ❌ with a "(deferred)" note

### Requirement: README Must Not List Generic Features
The current generic bullet list in the Features section SHALL be replaced by the dgMP use case table.

#### Scenario: Old feature list removed
- **WHEN** the README is updated
- **THEN** the previous unstructured bullet list (Patient Search, Medication Plan, etc.) SHALL be removed and replaced by the structured use case table
