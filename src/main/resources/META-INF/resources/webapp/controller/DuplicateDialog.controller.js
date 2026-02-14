sap.ui.define([
    "sap/ui/core/mvc/Controller",
    "sap/m/MessageToast",
    "sap/ui/model/json/JSONModel"
], function (Controller, MessageToast, JSONModel) {
    "use strict";

    return Controller.extend("epa.controller.DuplicateDialog", {

        onInit: function () {
            // Initialization logic if needed
        },

        /**
         * Confirm action: Proceed with adding the new entry (creating a duplicate)
         */
        onConfirmDuplicate: function () {
            // Get the dialog and its custom data (deferred promise)
            var oDialog = this.getView().getContent()[0].getParent(); // Get dialog from view content
            var resolve = oDialog.data("resolve");
            var oModel = this.getView().getModel("duplicate");

            // Resolve with "create" action
            if (resolve) {
                resolve("create");
            }

            oDialog.close();
        },

        /**
         * Cancel action: Do nothing, abort add operation
         */
        onCancelDuplicate: function () {
            var oDialog = this.getView().getContent()[0].getParent();
            var resolve = oDialog.data("resolve");

            // Resolve with "cancel" action
            if (resolve) {
                resolve("cancel");
            }

            oDialog.close();
        }
    });
});
