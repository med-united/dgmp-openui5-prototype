# Data Model: ePA Medication Service UI

**Feature**: ePA Medication Service UI Prototype  
**Phase**: 1 (Design & Contracts)  
**Date**: 2026-02-14

## Purpose

This document defines the core entities, relationships, attributes, validation rules, and state transitions for the ePA Medication Service UI prototype. All entities align with ePA Medication Service IG v1.3.0 and FHIR R4 specifications.

---

## Core Entities

### 1. Patient

**Description**: Represents a patient whose medication data is being viewed/managed.

**Source**: EF.PD file from eGK (for demographics) or JSON fixtures

**Attributes**:

| Attribute | Type | Required | Validation | Description |
|-----------|------|----------|------------|-------------|
| `kvnr` | String | Yes | 10 chars, alphanumeric | Krankenversichertennummer (patient ID) |
| `firstName` | String | Yes | 1-100 chars | Patient given name |
| `lastName` | String | Yes | 1-100 chars | Patient family name |
| `dateOfBirth` | Date | Yes | ISO 8601 (YYYY-MM-DD) | Patient birth date |
| `insuranceType` | String | No | Enum: `GKV`, `PKV`, `other` | Insurance type (for display) |

**Identity**: Unique by `kvnr`

**Lifecycle**: Read-only in prototype (demographics from eGK or fixtures)

**Sample JSON**:
```json
{
  "kvnr": "X123456789",
  "firstName": "Erika",
  "lastName": "Mustermann",
  "dateOfBirth": "1985-04-12",
  "insuranceType": "GKV"
}
```

---

### 2. MedicationEntry (Base)

**Description**: Base entity for medication entries in either eML or eMP. Not instantiated directly.

**Source**: FHIR MedicationStatement (eML) or MedicationRequest (eMP)

**Common Attributes**:

| Attribute | Type | Required | Validation | Description |
|-----------|------|----------|------------|-------------|
| `id` | String | Yes | UUID | Unique entry identifier |
| `pzn` | String | Yes | 8 digits | Pharmazentralnummer (medication code) |
| `medicationName` | String | Yes | 1-200 chars | Trade name (e.g., "Ramipril 5mg") |
| `activeIngredient` | String | Yes | 1-200 chars | Wirkstoff (e.g., "Ramipril") |
| `strength` | String | No | e.g., "5mg", "10mg/ml" | Medication strength |
| `dosageText` | String | No | 1-500 chars | Free-text dosage instructions |
| `dosageStructured` | String | No | Pattern: `\d-\d-\d-\d` | Structured dosage (morning-noon-evening-night) |
| `intakeInstructions` | String | No | 1-500 chars | e.g., "vor dem Essen", "mit Wasser" |
| `indication` | String | No | 1-200 chars | Behandlungsgrund (reason for therapy) |
| `atcCode` | String | No | WHO ATC format | Anatomical Therapeutic Chemical code |
| `askCode` | String | No | ASK format | German medication classification code |

**Validation Rules**:
- At least one of `dosageText` OR `dosageStructured` must be provided
- If `dosageStructured` is used, must match pattern `\d-\d-\d-\d` (e.g., "1-0-1-0")
- `pzn` must be 8 digits (German PZN format)

---

### 3. MedicationListEntry (extends MedicationEntry)

**Description**: Entry in the eML (electronic Medication List) - historical prescription or dispensement record.

**Source**: FHIR MedicationStatement (profile: ePA Medication Service IG v1.3.0)

**Additional Attributes**:

| Attribute | Type | Required | Validation | Description |
|-----------|------|----------|------------|-------------|
| `entryType` | String | Yes | Enum: `prescription`, `dispensement` | Verordnung vs Abgabe |
| `authoredDate` | DateTime | Yes | ISO 8601 | When prescription/dispensement occurred |
| `prescriber` | String | No | 1-200 chars | Prescribing physician name |
| `pharmacy` | String | No | 1-200 chars | Dispensing pharmacy name (for dispensements) |
| `linkedToPlanId` | String | No | UUID | Reference to linked eMP entry (if linked) |

**Lifecycle**: Read-only (historical data)

**Sample JSON**:
```json
{
  "id": "eml-001",
  "entryType": "dispensement",
  "pzn": "12345678",
  "medicationName": "Ibuprofen 400mg",
  "activeIngredient": "Ibuprofen",
  "strength": "400mg",
  "dosageText": "Bei Bedarf 1 Tablette",
  "indication": "Schmerz",
  "authoredDate": "2026-01-15T10:30:00Z",
  "pharmacy": "Rosen-Apotheke",
  "linkedToPlanId": null
}
```

---

### 4. MedicationPlanEntry (extends MedicationEntry)

**Description**: Entry in the eMP (electronic Medication Plan) - current therapy plan.

**Source**: FHIR MedicationRequest (profile: ePA Medication Service IG v1.3.0)

**Additional Attributes**:

| Attribute | Type | Required | Validation | Description |
|-----------|------|----------|------------|-------------|
| `status` | String | Yes | Enum: `active`, `paused`, `planned`, `completed` | Therapy status |
| `entrySource` | String | Yes | Enum: `self_medication`, `prescription` | Self-medication (OTC) vs physician prescription |
| `startDate` | Date | No | ISO 8601 | Therapy start date |
| `endDate` | Date | No | ISO 8601, ≥ startDate | Therapy end date (if applicable) |
| `createdAt` | DateTime | Yes | ISO 8601 | Entry creation timestamp |
| `lastModified` | DateTime | Yes | ISO 8601 | Last edit timestamp |

**State Transitions**:

```
┌─────────┐
│ planned │────────────────────┐
└────┬────┘                    │
     │ activate          cancel│
     ▼                   therapy│
┌────────┐  pause   ┌────────┐ │
│ active ├─────────►│ paused │ │
└───┬────┘          └───┬────┘ │
    │                   │      │
    │ reactivate        │      │
    │◄──────────────────┘      │
    │                          │
    │ end therapy              │
    ▼                          │
┌──────────┐◄─────────────────┘
│completed │
└──────────┘
```

**State Transitions**:
- `planned → active`: Therapy begins
- `planned → completed`: Therapy cancelled before starting (optional)
- `active ↔ paused`: Bidirectional pause/reactivate
- `active → completed`: End active therapy
- `paused → completed`: End paused therapy
- `completed`: Terminal state (no outbound transitions)

**Validation Rules**:
- `paused` → `active`: allowed (reactivation)
- `active` → `paused`: allowed
- `active` OR `paused` → `completed`: allowed (end therapy)
- `planned` → `active`: allowed (start therapy)
- `completed` → any: NOT allowed (terminal state)

**Sample JSON**:
```json
{
  "id": "emp-001",
  "pzn": "87654321",
  "medicationName": "Ramipril 5mg",
  "activeIngredient": "Ramipril",
  "strength": "5mg",
  "dosageStructured": "1-0-1-0",
  "intakeInstructions": "morgens und abends",
  "indication": "Bluthochdruck",
  "atcCode": "C09AA05",
  "status": "active",
  "entrySource": "prescription",
  "startDate": "2026-01-01",
  "createdAt": "2026-01-01T09:00:00Z",
  "lastModified": "2026-01-01T09:00:00Z"
}
```

---

### 5. MedicationList (Aggregate)

**Description**: Collection of eML entries for a patient.

**Attributes**:

| Attribute | Type | Required | Description |
|-----------|------|----------|-------------|
| `kvnr` | String | Yes | Patient identifier |
| `entries` | Array<MedicationListEntry> | Yes (can be empty) | List of historical entries |
| `lastUpdated` | DateTime | Yes | Last modification timestamp |

**Ordering**: Entries ordered by `authoredDate` descending (most recent first)

**Sample JSON**:
```json
{
  "kvnr": "X123456789",
  "entries": [ /* array of MedicationListEntry */ ],
  "lastUpdated": "2026-02-14T10:00:00Z"
}
```

---

### 6. MedicationPlan (Aggregate)

**Description**: Collection of eMP entries for a patient.

**Attributes**:

| Attribute | Type | Required | Description |
|-----------|------|----------|-------------|
| `kvnr` | String | Yes | Patient identifier |
| `entries` | Array<MedicationPlanEntry> | Yes (can be empty) | List of therapy plan entries |
| `version` | Integer | Yes | Plan version number (increments on edit) |
| `lastUpdated` | DateTime | Yes | Last modification timestamp |

**Grouping**: Entries grouped by `status` in UI (active | paused | planned)

**Versioning**: Each edit to any entry creates new version of entire plan (for audit trail)

**Sample JSON**:
```json
{
  "kvnr": "X123456789",
  "entries": [ /* array of MedicationPlanEntry */ ],
  "version": 3,
  "lastUpdated": "2026-02-14T10:00:00Z"
}
```

---

### 7. PatientDemographics

**Description**: Patient data extracted from eGK card (EF.PD file).

**Source**: PC/SC card reader via smartcard-playground library

**Attributes**:

| Attribute | Type | Required | Description |
|-----------|------|----------|-------------|
| `kvnr` | String | Yes | Extracted from PD.PersonenDaten.Versicherten_ID |
| `firstName` | String | Yes | Extracted from PD.PersonenDaten.Vorname |
| `lastName` | String | Yes | Extracted from PD.PersonenDaten.Nachname |
| `dateOfBirth` | Date | Yes | Extracted from PD.PersonenDaten.Geburtsdatum |
| `rawXml` | String | No | Full decompressed XML from EF.PD (for debugging) |

**Lifecycle**: Read-only, ephemeral (not persisted beyond session)

---

### 8. DuplicateMatch

**Description**: Result of duplicate detection when adding/editing medication.

**Attributes**:

| Attribute | Type | Required | Description |
|-----------|------|----------|-------------|
| `newEntry` | MedicationEntry | Yes | Entry being added/edited |
| `existingEntry` | MedicationPlanEntry | Yes | Matching entry already in eMP |
| `matchReason` | String | Yes | Enum: `same_pzn`, `same_atc`, `same_ask` |
| `confidence` | String | Yes | Enum: `high`, `medium` |

**Match Rules**:
- `same_pzn`: Exact PZN match → confidence: `high`
- `same_atc`: Same ATC code (first 5 chars) → confidence: `medium`
- `same_ask`: Same ASK code → confidence: `medium`

---

### 9. ReconciliationItem

**Description**: Discrepancy between eML and eMP identified in reconciliation view.

**Attributes**:

| Attribute | Type | Required | Description |
|-----------|------|----------|-------------|
| `emlEntry` | MedicationListEntry | Yes | Entry from eML not in eMP |
| `matchStatus` | String | Yes | Enum: `missing_in_emp`, `partial_match` |
| `suggestedAction` | String | Yes | Enum: `add_to_emp`, `link_to_existing`, `ignore` |

**Business Logic**:
- `missing_in_emp`: eML entry has no corresponding eMP entry (different PZN/ATC)
- `partial_match`: Similar medication exists in eMP but not linked

---

## Entity Relationships

```
┌─────────┐
│ Patient │
└────┬────┘
     │ 1
     │
     │ has 1
     ├──────────────────┬─────────────────┐
     │                  │                 │
     ▼ 1                ▼ 1               ▼ *
┌────────────────┐ ┌─────────────────┐ ┌───────────────────┐
│MedicationList  │ │ MedicationPlan  │ │PatientDemographics│
│                │ │                 │ │  (from eGK)       │
└───┬────────────┘ └───┬────────────┘ └───────────────────┘
    │ 1                 │ 1
    │                   │
    │ contains *        │ contains *
    ▼                   ▼
┌───────────────────┐ ┌──────────────────┐
│MedicationListEntry│ │MedicationPlanEntry│
└───────┬───────────┘ └───┬──────────────┘
        │                  │
        │ linkedToPlanId   │
        └──────────────────┘
            0..1 ↔ 0..1
```

**Key Relationships**:
- Patient → MedicationList: 1:1 (one list per patient)
- Patient → MedicationPlan: 1:1 (one plan per patient)
- MedicationList → MedicationListEntry: 1:* (one list has many entries)
- MedicationPlan → MedicationPlanEntry: 1:* (one plan has many entries)
- MedicationListEntry ↔ MedicationPlanEntry: 0..1:0..1 (optional linking between history and plan)

---

## Validation Summary

### Cross-Entity Validation

1. **Duplicate Detection** (User Story 4 / FR-006):
   - Before creating new MedicationPlanEntry, check existing entries
   - Match criteria: same PZN OR same ATC (first 5 chars) OR same ASK
   - If match found → trigger DuplicateMatch workflow

2. **Status Transitions** (User Story 8 / FR-011):
   - Validate state machine (see MedicationPlanEntry state transitions above)
   - Only allowed transitions can be executed
   - `completed` is terminal state

3. **Linking Validation** (User Story 9 / FR-013):
   - `linkedToPlanId` in MedicationListEntry must reference valid MedicationPlanEntry.id
   - One eML entry can link to at most one eMP entry
   - One eMP entry can be linked from multiple eML entries (e.g., repeated dispensements)

4. **Reconciliation** (User Story 5 / FR-014-016):
   - ReconciliationItem generated only for eML entries with `linkedToPlanId == null`
   - Suggested actions based on medication matching rules

---

## Design Notes

### FHIR Alignment

- **MedicationListEntry** maps to FHIR `MedicationStatement` resource
- **MedicationPlanEntry** maps to FHIR `MedicationRequest` resource
- Attributes chosen to align with ePA Medication Service IG v1.3.0 profiles
- JSON fixture files will use valid FHIR resource structures (though simplified for prototype)

### Simplifications for Prototype

- No real FHIR validation engine (structural checks only)
- No CodeSystem/ValueSet bindings (codes are strings, not references)
- No Provenance tracking (audit trail simplified to `lastModified` timestamps)
- No real links to external TI resources (Patient, Practitioner references are strings)

### Extension Points for Future

- Add `Practitioner` entity when prescription workflow is integrated
- Add `Organization` entity for pharmacy/hospital references
- Add `Bundle` entity for full FHIR transaction support
- Add persistent storage (database) when moving beyond prototype

---

**Data Model Complete**: All entities defined with attributes, relationships, and validation rules. Ready for API contract generation.
