&lt;!--
SYNC IMPACT REPORT
==================
Version: 1.0.0 (Initial Constitution)
Rationale: First complete population of constitution template with project-specific principles
  
Changes:
- Populated all placeholder tokens with concrete values for FHIR TI prototype
- Added 6 core principles focused on compliance, testing, and code quality
- Defined technology stack constraints and compliance requirements
- Established testing and quality gates
- Set up governance rules

Principles Defined:
1. FHIR TI Compliance (NON-NEGOTIABLE)
2. dgMP Function Completeness
3. Comprehensive Test Coverage (NON-NEGOTIABLE)
4. Code Readability and Structure
5. Domain-Driven Design
6. Security and Data Privacy

Templates Status:
- plan-template.md: ⚠ Pending review for constitution alignment
- spec-template.md: ⚠ Pending review for constitution alignment
- tasks-template.md: ⚠ Pending review for constitution alignment

Follow-up TODOs:
- Review and update plan/spec/tasks templates to reference new principles
- Establish specific KBV profile validation test patterns
- Define code structure patterns for Quarkus resource organization
--&gt;

# FHIR TI Prototype Constitution

## Core Principles

### I. FHIR TI Compliance (NON-NEGOTIABLE)

All FHIR resources MUST conform to German Telematik Infrastructure (TI) specifications and profiles. This principle is absolute and non-negotiable.

**Requirements:**
- All generated FHIR resources MUST validate against KBV (Kassenärztliche Bundesvereinigung) profiles
- Implementation MUST support gematik specifications for the German healthcare system
- Profile compliance MUST be verified through automated validation in tests
- Breaking changes to TI specifications MUST trigger immediate compatibility reviews

**Rationale:** The prototype exists solely to demonstrate correct FHIR resource generation for the German TI infrastructure. Non-compliant resources render the project purposeless.

### II. dgMP Function Completeness

All digital health application (Digitale Gesundheitsanwendung - dgMP) functions MUST be implemented completely and correctly according to specifications.

**Requirements:**
- Each dgMP function MUST be implemented with full business logic (no stubs or partial implementations)
- dgMP interactions MUST follow gematik API specifications
- Function contracts MUST be testable and tested
- OpenUI5 frontend MUST support all required user workflows

**Rationale:** Partial or incomplete dgMP implementations create misleading prototypes that fail to demonstrate real-world viability.

### III. Comprehensive Test Coverage (NON-NEGOTIABLE)

All non-mocked implementations MUST have unit tests. Test coverage is a quality gate and non-negotiable.

**Requirements:**
- Every business logic class MUST have corresponding unit tests
- Only infrastructure/integration code (e.g., actual TI connectors) may be mocked
- All FHIR resource builders MUST have validation tests
- Tests MUST verify both positive cases and error handling
- Red-Green-Refactor cycle: Write failing test → Implement → Refactor

**Rationale:** Untested code is unverifiable. In a compliance-heavy domain like healthcare, untested implementations pose unacceptable risks.

### IV. Code Readability and Structure

Code MUST be easy to read, understand, and maintain. Clarity takes precedence over cleverness.

**Requirements:**
- Use meaningful, domain-specific names (prefer `MedicationResource` over `MedRes`)
- Follow standard Java naming conventions (PascalCase for classes, camelCase for methods)
- Keep methods focused (single responsibility) and small (prefer &lt;30 lines)
- Document complex business logic with JavaDoc, especially FHIR profile mappings
- Structure packages by domain concern (e.g., `medication`, `patient`, `prescription`) not by layer

**Rationale:** Healthcare domain logic is inherently complex. Code structure must reduce cognitive load, not add to it.

### V. Domain-Driven Design

Organize code around healthcare domain concepts, not generic technical patterns.

**Requirements:**
- Package structure MUST reflect healthcare domains (e.g., `de.servicehealth.fhir.medication`, `de.servicehealth.fhir.patient`)
- Use ubiquitous language from FHIR and gematik specifications
- Model aggregates and entities according to DDD patterns (e.g., `Prescription` as aggregate root)
- Separate domain logic from infrastructure (Quarkus resources are adapters, not domain)

**Rationale:** FHIR is a domain-driven standard. Aligning code structure with domain concepts improves comprehension and reduces translation overhead.

### VI. Security and Data Privacy

Healthcare data requires strict security and privacy controls.

**Requirements:**
- No hardcoded patient data or credentials in code or tests (use fixtures/builders)
- Sensitive operations MUST have audit logging
- Follow GDPR and German data protection regulations (DSGVO)
- TI authentication and authorization MUST be correctly implemented per gematik specs

**Rationale:** Healthcare data is special category personal data under GDPR. Security and privacy are legal requirements, not optional features.

## Technology Stack and Constraints

### Mandated Technologies
- **Backend**: Java 17+, Quarkus 3.x
- **Frontend**: OpenUI5 (SAPUI5)
- **FHIR**: HAPI FHIR library (Jakarta-aligned version for Quarkus 3)
- **Testing**: JUnit 5, RestAssured for API tests
- **Build**: Maven

### FHIR Profile Requirements
- MUST support KBV profiles for eRezept (electronic prescription)
- MUST support MIO (Medizinische Informationsobjekte) specifications
- MUST validate against official German TI ValueSets
- SHOULD use FHIR StructureDefinition validation where possible

### Dependency Management
- Keep dependencies aligned with Quarkus BOM (Bill of Materials)
- Avoid webjars with excessive size (e.g., prefer CDN for OpenUI5 if possible)
- Document any TI-specific library requirements (e.g., gematik client libraries)

## Testing and Quality Gates

### Unit Test Requirements
- All service classes MUST have unit tests
- All FHIR resource builders MUST have tests validating structure
- Test naming: `MethodName_StateUnderTest_ExpectedBehavior` (e.g., `buildMedication_WithValidInput_CreatesCompliantResource`)
- Use test fixtures or builders to create test data (no inline complex JSON)

### Integration Test Requirements
- API endpoints MUST have integration tests using RestAssured
- FHIR validation MUST be tested with actual HAPI validator instances
- Mock external TI services (connectors, IDP) but test integration contracts

### Quality Metrics (Aspirational)
- Unit test coverage target: &gt;80% for business logic packages
- All public API methods must be tested
- Critical paths (e.g., prescription creation) require &gt;90% coverage

### Pre-Commit Checks
- Code MUST compile without warnings
- All tests MUST pass
- Code formatting SHOULD follow Google Java Style Guide (or project-defined style)

## Governance

### Constitution Authority
This constitution supersedes all other development practices and conventions. When in doubt, follow the constitution.

### Amendment Process
1. Constitution changes MUST be proposed in `.specify/memory/constitution.md`
2. Amendment rationale MUST be documented
3. Version MUST be incremented according to semantic rules:
   - **MAJOR**: Breaking changes to core principles (e.g., removing "FHIR TI Compliance")
   - **MINOR**: New principles added or significant expansions
   - **PATCH**: Clarifications, wording improvements, non-semantic fixes
4. Dependent templates MUST be reviewed for consistency

### Compliance Review
- All specifications (`.specify/memory/spec.md`) MUST reference relevant constitution principles
- All implementation plans MUST verify adherence to non-negotiable principles
- Code reviews SHOULD validate compliance with readability and structure principles

### Version Control
Constitution changes MUST be committed separately from feature work with descriptive commit messages (e.g., `docs: amend constitution to v1.1.0 (add observability principle)`).

**Version**: 1.0.0 | **Ratified**: 2026-02-13 | **Last Amended**: 2026-02-13
