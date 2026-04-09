sap.ui.define([
    "sap/ui/core/UIComponent",
    "epa/model/models"
], function (UIComponent, models) {
    "use strict";

    return UIComponent.extend("epa.Component", {

        metadata: {
            manifest: "json"
        },

        /**
         * Lifecycle hook called after the component is initialized.
         * Models are declared in manifest.json and instantiated automatically by UI5.
         * This method wires up any runtime state that cannot be declared statically.
         */
        init: function () {
            // Invoke the parent init — this reads manifest.json and creates models
            UIComponent.prototype.init.apply(this, arguments);
        }

    });
});
