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
         * @param {string} status - Status (active, paused, planned, completed)
         * @returns {string} Status text
         */
        formatStatus: function (status) {
            if (!status) return "";

            var statusMap = {
                "active": "Aktiv",
                "paused": "Pausiert",
                "planned": "Geplant",
                "completed": "Abgeschlossen"
            };

            return statusMap[status] || status;
        },

        /**
         * Get status icon
         * @param {string} status - Status
         * @returns {string} Icon name
         */
        getStatusIcon: function (status) {
            var iconMap = {
                "active": "sap-icon://accept",
                "paused": "sap-icon://pause",
                "planned": "sap-icon://future",
                "completed": "sap-icon://complete"
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
        }
    };
});
