sap.ui.define([], function () {
    "use strict";

    return {
        /**
         * Format date to German date format (DD.MM.YYYY)
         * @param {string|Date} date - Date to format
         * @returns {string} Formatted date
         */
        formatDate: function (date) {
            if (!date) return "";

            var dateObj = (typeof date === "string") ? new Date(date) : date;
            if (isNaN(dateObj.getTime())) return "";

            var day = ("0" + dateObj.getDate()).slice(-2);
            var month = ("0" + (dateObj.getMonth() + 1)).slice(-2);
            var year = dateObj.getFullYear();

            return day + "." + month + "." + year;
        },

        /**
         * Format medication status with icon
         * @param {string} status - FHIR R4 Status
         * @returns {string} Status text
         */
        formatStatus: function (status) {
            if (!status) return "";

            var statusMap = {
                "active": "Aktiv",
                "on-hold": "Pausiert",
                "stopped": "Abgebrochen",
                "completed": "Abgeschlossen",
                "cancelled": "Storniert",
                "entered-in-error": "Fehler",
                "draft": "Entwurf",
                "unknown": "Unbekannt"
            };

            return statusMap[status] || "Unbekannt";
        },

        /**
         * Get status icon
         * @param {string} status - FHIR R4 Status
         * @returns {string} Icon name
         */
        getStatusIcon: function (status) {
            var iconMap = {
                "active": "sap-icon://sys-enter-2",
                "on-hold": "sap-icon://pause",
                "stopped": "sap-icon://stop",
                "completed": "sap-icon://complete",
                "cancelled": "sap-icon://cancel",
                "entered-in-error": "sap-icon://error",
                "draft": "sap-icon://document",
                "unknown": "sap-icon://question-mark"
            };

            return iconMap[status] || "sap-icon://question-mark";
        },

        /**
         * Format dosage for display
         * @param {string} dosageText - Dosage text
         * @returns {string} Formatted dosage
         */
        formatDosage: function (dosageText) {
            return dosageText || "Keine Angabe";
        },

        /**
         * Format medication entry type
         * @param {string} entryType - prescription or dispensement
         * @returns {string} Formatted type
         */
        formatEntryType: function (entryType) {
            return entryType === "prescription" ? "Rezept" : "Abgabe";
        },

        /**
         * Calculate total history events (Prescriptions + Dispensations)
         * @param {string[]} linkedEmlIds - Array of linked eML IDs (Prescriptions)
         * @param {Array} allEmlEntries - Full list of eML entries
         * @returns {string} Formatted count string e.g. "History (3)"
         */
        formatHistoryCount: function (linkedEmlIds, allEmlEntries) {
            if (!linkedEmlIds || linkedEmlIds.length === 0 || !allEmlEntries) {
                return "";
            }

            var count = 0;
            // Filter linked prescriptions
            var linkedPrescriptions = allEmlEntries.filter(function (entry) {
                return linkedEmlIds.includes(entry.id);
            });

            linkedPrescriptions.forEach(function (presc) {
                count++; // The prescription itself
                if (presc.dispensations) {
                    count += presc.dispensations.length; // Its dispensations
                }
            });

            return "History (" + count + ")";
        }
    };
});
