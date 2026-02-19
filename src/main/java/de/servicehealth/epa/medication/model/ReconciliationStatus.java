package de.servicehealth.epa.medication.model;

/**
 * Status of a prescription in the reconciliation process.
 * Determines which action buttons are shown to the user.
 */
public enum ReconciliationStatus {
    /**
     * Fall C: Prescription is technically linked to an eMP entry via
     * basedOnReference.
     * Action: Show "Im Plan enthalten" link (green, chainlink icon)
     */
    LINKED,

    /**
     * Fall B: No basedOnReference, BUT same active ingredient (ATC L5) exists in
     * eMP.
     * Action: Show "Zuordnen" button to manually link
     */
    UNLINKED_UPDATE,

    /**
     * Prescription matches a plan entry by PZN (Soft Match).
     * Action: Show "Bestätigen" button to create Hard Link
     */
    PROPOSAL_MATCH,

    /**
     * Fall A: No basedOnReference AND no matching active ingredient in eMP.
     * Action: Show "In eMP aufnehmen" button to add new entry (requires dosage
     * dialog)
     */
    ORPHAN_NEW,

    /**
     * Prescription was cancelled/storniert.
     * Action: Display greyed out or hide by default
     */
    CANCELLED
}
