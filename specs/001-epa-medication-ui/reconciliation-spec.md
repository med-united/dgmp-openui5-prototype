# Enhanced Reconciliation View Specification

## Overview

This document extends User Story 4 in [spec.md](file:///home/dennis/git/fhir-prototype/specs/001-epa-medication-ui/spec.md#L93-L106) with detailed requirements based on the **ePA Medication Service Implementation Guide** for medication therapy harmonization.

## Goal

The Reconciliation View must support **closing documentation gaps** and **avoiding duplicates** by harmonizing the historical medication list (eML) with the current medication plan (eMP).

## Key Design Principle

> **Split-View Mental Model**: The UI should enable a "drag and drop" mental model where the pharmacist/physician visually compares eML (source) with eMP (target) and transfers/links medications as appropriate.

## Layout Requirements

### FR-REC-001: Split-View Layout

The reconciliation tab MUST display two side-by-side panels:

- **Left Panel (Source)**: eML entries (historical prescriptions/dispensements)
- **Right Panel (Target)**: eMP entries (current active therapy plan)

**Rationale**: Side-by-side comparison is cognitively easier than single-list views, especially when comparing 10+ medications.

### FR-REC-002: Toolbar

The reconciliation view MUST include a toolbar above the split panels with:

- **Time filter dropdown**: "Last 3 months" | "Last 6 months" | "Last year" | "Custom range"
- **Show linked checkbox**: Toggle to include/exclude already-linked entries
- **Refresh button**: Reload reconciliation data
- **Statistics display**: "X unmatched medications | Y probable matches"

## Data Display Requirements

### FR-REC-003: eML Panel (Left) - Required Fields

Each eML entry MUST display:

| Field | Purpose | Critical? |
|-------|---------|-----------|
| **Medication Name** (trade name) | Identify specific product dispensed | ✅ Yes |
| **PZN** | Unique product identifier | ✅ Yes |
| **Active Ingredient (ATC/ASK)** | Match across generics | ✅ Yes |
| **Strength** | Dosage matching (e.g., 5mg vs 10mg) | ✅ Yes |
| **Dosage Form** | Tablet vs injection distinction | No |
| **Dispensement/Prescription Date** | Temporal filtering | ✅ Yes |
| **Entry Type** | Prescription vs Dispensement | No |

**Rationale**: Active ingredient (ATC) is CRITICAL because rabattvertr (discount contracts) mean the dispensed PZN often differs from the prescribed PZN. The UI must show: *"eML has 'Ibuprofen Hexal', eMP has 'Ibuprofen Ratiopharm' → same active ingredient (Ibuprofen 400mg)"*.

### FR-REC-004: eMP Panel (Right) - Required Fields

Each eMP entry MUST display:

| Field | Purpose |
|-------|---------|
| **Medication Name** | Current therapy medication |
| **Status** | Active / Paused / Planned |
| **Dosage Schema** | e.g., "1-0-1-0" |
| **ATC Code** | Matching basis |
| **Linked History Count** | "3 dispensements" (number of linked eML entries) |

### FR-REC-005: Responsive Width

Each panel MUST occupy approximately 48% width with 4% gap/divider, ensuring both panels visible simultaneously on screens ≥1280px.

## Visual Indicators Requirements

### FR-REC-006: Color-Coded Match Status

Each eML entry MUST be visually coded based on match status:

| Status | Color | Meaning | Action |
|--------|-------|---------|--------|
| **Exact Match** | Green border | Manually linked via POST /api/medications/link | None (already reconciled) |
| **Probable Match** | Yellow/Orange border | Same ATC (L5) + same strength, but NOT linked | Suggest: "Link to existing entry?" |
| **Orphan (No Match)** | Red border | No matching active ingredient in eMP | "Add to Plan" button |

**Pseudo-code for match status**:
```
if (emlEntry.linkedToPlanId exists in eMP entries):
    status = EXACT_MATCH  // green
else if (eMP has entry with same atcCode[0:5] AND same strength):
    status = PROBABLE_MATCH  // yellow
else:
    status = ORPHAN  // red
```

### FR-REC-007: Match Badge

Exact match entries SHOULD display a checkmark badge with text "Linked to Plan".

### FR-REC-008: Highlighting Linked eMP Entries

When an eMP entry has linked eML entries:
- Display count badge: "History (3)"
- Clicking opens dialog showing linked eML entries (already implemented)

## Matching Logic Requirements

### FR-REC-009: Match Priority Algorithm

The backend `/api/medications/reconciliation/{kvnr}` MUST determine match status using this priority:

1. **Manual link first**: If `emlEntry.id` exists in `linkMap` → EXACT_MATCH
2. **PZN exact match**: If `emlEntry.pzn == empEntry.pzn` → PROBABLE_MATCH
3. **ATC + Strength match**: If `emlEntry.atcCode[0:5] == empEntry.atcCode[0:5]` AND `emlEntry.strength == empEntry.strength` → PROBABLE_MATCH
4. **No match** → ORPHAN

**Note**: Step 1 (manual link) is already implemented in `MedicationService.java:315-367`.

### FR-REC-010: Strength Comparison

Strength matching MUST normalize units:
- "500mg" == "0.5g"
- "1ml" == "1000µl"

*(For prototype: simple string equality is acceptable, note as future enhancement)*

## Time Filtering Requirements

### FR-REC-011: Temporal Filter

The reconciliation view MUST support filtering eML entries by `authoredDate`:

- **Last 3 months**: `authoredDate >= (today - 90 days)`
- **Last 6 months**: `authoredDate >= (today - 180 days)`
- **Last year**: `authoredDate >= (today - 365 days)`
- **Custom**: User specifies start/end date

**Rationale**: A dispensement from 3 years ago is rarely relevant for current therapy reconciliation.

### FR-REC-012: Default Filter

Default time filter SHOULD be "Last 3 months".

### FR-REC-013: "Show Linked" Toggle

Checkbox to include/exclude already-linked entries:
- **Unchecked** (default): Hide EXACT_MATCH entries (green)
- **Checked**: Show all entries including linked ones

**Rationale**: Once an entry is linked, it's "done". Hiding it de-clutters the view.

## Action Requirements

### FR-REC-014: "Add to Plan" Action

**Given** an orphan (red) eML entry, **When** user clicks "Add to Plan", **Then**:

1. Open `AddMedicationDialog` fragment
2. Pre-fill fields:
   - Medication name: `emlEntry.medicationName`
   - PZN: `emlEntry.pzn`
   - Active ingredient: `emlEntry.activeIngredient`
   - Strength: `emlEntry.strength`
3. Leave **empty** (user must fill):
   - **Dosage schema** (e.g., "1-0-1-0") - REQUIRED
   - **Indication** - REQUIRED
   - **Status**: Default to "active"
4. On save: POST /api/medications/plan/{kvnr}/entries
5. Refresh both eML and eMP panels

**Rationale**: eML (dispensements) contains what was given, but not HOW to take it. Dosage schema is missing and must be added.

### FR-REC-015: "Link" Action

**Given** a probable match (yellow) eML entry, **When** user clicks link icon, **Then**:

1. Show dropdown/action sheet with eMP entries filtered by same ATC code
2. Display: eMP medication name + dosage schema
3. User selects one
4. Call: POST /api/medications/link?emlId={emlId}&empId={empId}
5. Update eML entry: `emlEntry.linkedToPlanId = empId`
6. Change visual status from yellow → green

### FR-REC-016: "Unlink" Action

**Given** an exact match (green) eML entry, **When** user clicks unlink icon, **Then**:

1. Call: DELETE /api/medications/link?emlId={emlId}
2. Clear `emlEntry.linkedToPlanId`
3. Re-run match algorithm to determine new status (yellow or red)

### FR-REC-017: "Mark as Error" Action

**Given** any eML entry, **When** user clicks "Mark as Error" icon, **Then**:

1. Set `emlEntry.markedAsError = true` (local state, prototype only not persisted)
2.Remove from reconciliation view
3. Show toast: "Entry marked as error (hidden from reconciliation)"

**Rationale**: Sometimes dispensement data is incorrect (wrong patient, data entry error). This allows filtering it out without deleting.

## API Contract Requirements

### FR-REC-018: Enhanced Reconciliation Endpoint

**MODIFY** `GET /api/medications/reconciliation/{kvnr}`

**Query Parameters**:
- `timeRange` (optional): `3_MONTHS` | `6_MONTHS` | `1_YEAR` | `CUSTOM`
- `startDate` (optional): ISO date (required if timeRange=CUSTOM)
- `endDate` (optional): ISO date (required if timeRange=CUSTOM)
- `showLinked` (optional): boolean, default `false`

**Response** (enhanced `ReconciliationItem`):
```json
{
  "pzn": "12345678",
  "medicationName": "Ibuprofen Hexal 400mg",
  "atcCode": "M01AE01",
  "matchType": "PROBABLE_MATCH",  // EXACT_MATCH | PROBABLE_MATCH | ORPHAN
  "matchConfidence": 85,  // 0-100
  "matchReason": "Same ATC (M01AE) + same strength (400mg), but not linked",
  "emlEntry": { ...full eML entry... },
  "empEntry": { ...matching eMP entry or null... },
  "missingFields": ["dosageSchema", "indication"]  // fields needed to transfer
}
```

## Edge Cases

### EC-REC-001: Multiple Probable Matches

**Given** one eML entry matches multiple eMP entries (same ATC, different brands), **When** displaying, **Then**:
- Mark as PROBABLE_MATCH (yellow)
- Link action shows dropdown with all matching eMP entries
- User selects the correct one

### EC-REC-002: No eMP Entries

**Given** patient has empty eMP, **When** opening reconciliation, **Then**:
- All eML entries are ORPHAN (red)
- Right panel shows: "No current medication plan. Add medications from history."

### EC-REC-003No eML Entries

**Given** patient has empty eML, **When** opening reconciliation, **Then**:
- Left panel shows: "No historical medications found"
- Right panel shows eMP normally
- Message: "No reconciliation needed"

## Acceptance Criteria Updates

These scenarios SUPERSEDE the original User Story 4 scenarios in spec.md:

1. ✅ **Layout**: Split-view with eML left, eMP right
2. ✅ **Visual Indicators**: Red/yellow/green color coding
3. ✅ **Time Filter**: Dropdown filter working, only shows filtered dates
4. ✅ **Add to Plan**: Opens dialog with pre-filled data, requires dosage/indication
5. ✅ **Link**: Shows menu, creates link, changes color green
6. ✅ **Unlink**: Removes link, recalculates status
7. ✅ **Mark as Error**: Hides entry from view
8. ✅ **Statistics**: Toolbar shows counts

## Implementation Notes

- **Current implementation** uses single-table view (eML-centric)
- **New implementation** requires significant UI refactoring
- **Backend** matching logic partially exists, needs enhancement for matchType/matchReason
- **Priority**: P3 (can increment UI incrementally)

## References

- Original spec: [spec.md User Story 4](file:///home/dennis/git/fhir-prototype/specs/001-epa-medication-ui/spec.md#L93-L106)
- Implementation plan: [implementation_plan.md](file:///home/dennis/.gemini/antigravity/brain/885818a5-2e68-43db-a44a-63b9bb6934c0/implementation_plan.md)
- Current implementation: [index.html lines 1015-1079](file:///home/dennis/git/fhir-prototype/src/main/resources/META-INF/resources/webapp/index.html#L1015-L1079)
