package de.servicehealth.epa.medication.model;

/**
 * Match type for reconciliation items.
 * Indicates the relationship between an eML entry and eMP entries.
 */
public enum MatchType {
    /**
     * Exact match: eML entry is linked to an eMP entry
     * - Manually linked via user action, OR
     * - Same PZN (pharmaceutical product number - unique identifier)
     */
    EXACT_MATCH,

    /**
     * Probable match: Same active ingredient (ATC L5) and strength, but not linked
     * Suggests the user should link them
     */
    PROBABLE_MATCH,

    /**
     * Orphan: No matching active ingredient found in eMP
     * Should be added to the plan
     */
    ORPHAN
}
