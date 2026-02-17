sap.ui.define([], function () {
    "use strict";

    return {
        /**
         * Check for interactions between medications in the plan.
         * @param {array} aMedications - Array of medication objects (from eMP)
         * @returns {array} - Array of warning objects { title, description, severity }
         */
        checkInteractions: function (aMedications) {
            var aWarnings = [];
            if (!aMedications || aMedications.length < 2) {
                return aWarnings;
            }

            // Extract active ingredients and normalize to lower case
            var aIngredients = aMedications.map(function (med) {
                return {
                    name: med.activeIngredient ? med.activeIngredient.toLowerCase() : "",
                    medicationName: med.medicationName
                };
            });

            // Rules
            this._checkIbuprofenAspirin(aIngredients, aWarnings);
            this._checkSimvastatinClarithromycin(aIngredients, aWarnings);
            this._checkRamiprilPotassium(aIngredients, aWarnings);

            return aWarnings;
        },

        _checkIbuprofenAspirin: function (aIngredients, aWarnings) {
            var hasIbuprofen = aIngredients.some(function (i) { return i.name.includes("ibuprofen"); });
            var hasAspirin = aIngredients.some(function (i) { return i.name.includes("acetylsalicyl") || i.name.includes("aspirin"); });

            if (hasIbuprofen && hasAspirin) {
                aWarnings.push({
                    title: "Interaction: Ibuprofen + Aspirin",
                    description: "Ibuprofen may reduce the cardioprotective effect of low-dose Aspirin. Risk of gastrointestinal ulcers increases.",
                    severity: "Warning"
                });
            }
        },

        _checkSimvastatinClarithromycin: function (aIngredients, aWarnings) {
            var hasSimvastatin = aIngredients.some(function (i) { return i.name.includes("simvastatin"); });
            var hasClarithromycin = aIngredients.some(function (i) { return i.name.includes("clarithromycin"); });

            if (hasSimvastatin && hasClarithromycin) {
                aWarnings.push({
                    title: "Critical Interaction: Simvastatin + Clarithromycin",
                    description: "Clarithromycin inhibits the breakdown of Simvastatin, leading to increased risk of muscle damage (rhabdomyolysis).",
                    severity: "Error"
                });
            }
        },

        _checkRamiprilPotassium: function (aIngredients, aWarnings) {
            var hasRamipril = aIngredients.some(function (i) { return i.name.includes("ramipril") || i.name.includes("lisinopril"); });
            var hasPotassium = aIngredients.some(function (i) { return i.name.includes("kalium") || i.name.includes("potassium") || i.name.includes("spironolacton"); });

            if (hasRamipril && hasPotassium) {
                aWarnings.push({
                    title: "Interaction: ACE Inhibitors + Potassium/K-Sparing Diuretics",
                    description: "Risk of hyperkalemia (high blood potassium), which can cause dangerous heart rhythm problems.",
                    severity: "Warning"
                });
            }
        }
    };
});
