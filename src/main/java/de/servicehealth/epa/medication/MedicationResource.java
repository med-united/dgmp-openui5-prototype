package de.servicehealth.epa.medication;

import de.servicehealth.epa.medication.model.DuplicateMatch;

import de.servicehealth.epa.medication.model.MedicationPlan;
import de.servicehealth.epa.medication.model.MedicationPlanEntry;
import de.servicehealth.epa.medication.model.ReconciliationItem;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;

/**
 * REST endpoint for medication data.
 * Provides access to eML (medication list) and eMP (medication plan) data.
 */
@Path("/api/medications")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class MedicationResource {

    @Inject
    MedicationService medicationService;

    @Inject
    MedicationDatabase medicationDatabase;

    @GET
    @Path("/search")
    public Response searchMedications(@QueryParam("query") String query) {
        if (query == null || query.length() < 2) {
            return Response.ok(List.of()).build();
        }
        return Response.ok(medicationDatabase.search(query)).build();
    }

    /**
     * Get electronic Medication List (eML) for a patient.
     * 
     * @param kvnr Patient KVNR (10 alphanumeric characters)
     * @return MedicationList with historical prescriptions/dispensements
     */
    @GET
    @Path("/list/{kvnr}")
    public Response getMedicationList(@PathParam("kvnr") String kvnr) {
        if (kvnr == null || kvnr.trim().isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"error\": \"KVNR parameter is required\"}")
                    .build();
        }

        return medicationService.loadMedicationList(kvnr)
                .map(medicationList -> Response.ok(medicationList).build())
                .orElse(Response.status(Response.Status.NOT_FOUND)
                        .entity("{\"error\": \"Medication list not found for KVNR: " + kvnr + "\"}")
                        .build());
    }

    /**
     * Get electronic Medication Plan (eMP) for a patient.
     * 
     * @param kvnr Patient KVNR
     * @return MedicationPlan with current therapy entries
     */
    @GET
    @Path("/plan/{kvnr}")
    public Response getMedicationPlan(@PathParam("kvnr") String kvnr) {
        if (kvnr == null || kvnr.trim().isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"error\": \"KVNR parameter is required\"}")
                    .build();
        }

        return medicationService.loadMedicationPlan(kvnr)
                .map(medicationPlan -> Response.ok(medicationPlan).build())
                .orElse(Response.status(Response.Status.NOT_FOUND)
                        .entity("{\"error\": \"Medication plan not found for KVNR: " + kvnr + "\"}")
                        .build());
    }

    /**
     * Add a new entry to the patient's medication plan.
     * 
     * @param kvnr             Patient KVNR
     * @param ignoreDuplicates If true, skips duplicate check
     * @param entry            MedicationPlanEntry to add
     * @return 201 Created with updated plan, or 409 Conflict if duplicate detected
     */
    @POST
    @Path("/plan/{kvnr}/entries")
    public Response addMedicationPlanEntry(
            @PathParam("kvnr") String kvnr,
            @QueryParam("ignoreDuplicates") @DefaultValue("false") boolean ignoreDuplicates,
            MedicationPlanEntry entry) {
        if (kvnr == null || kvnr.trim().isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"error\": \"KVNR parameter is required\"}")
                    .build();
        }

        if (entry == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"error\": \"Entry data is required\"}")
                    .build();
        }

        try {
            if (!ignoreDuplicates) {
                DuplicateMatch match = medicationService.checkForDuplicates(kvnr,
                        entry);
                if (match.isDuplicate()) {
                    return Response.status(Response.Status.CONFLICT).entity(match).build();
                }
            }

            MedicationPlan updatedPlan = medicationService
                    .addMedicationPlanEntry(kvnr, entry);
            return Response.status(Response.Status.CREATED).entity(updatedPlan).build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("{\"error\": \"Failed to add medication entry: " + e.getMessage() + "\"}")
                    .build();
        }
    }

    /**
     * Update an existing entry in the patient's medication plan.
     * 
     * @param kvnr    Patient KVNR
     * @param entryId ID of the entry to update
     * @param entry   Updated MedicationPlanEntry data
     * @return 200 OK with updated plan, or 404 Not Found if entry doesn't exist
     */
    @PUT
    @Path("/plan/{kvnr}/entries/{entryId}")
    public Response updateMedicationPlanEntry(
            @PathParam("kvnr") String kvnr,
            @PathParam("entryId") String entryId,
            MedicationPlanEntry entry) {
        if (kvnr == null || kvnr.trim().isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"error\": \"KVNR parameter is required\"}")
                    .build();
        }

        if (entryId == null || entryId.trim().isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"error\": \"Entry ID parameter is required\"}")
                    .build();
        }

        if (entry == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"error\": \"Entry data is required\"}")
                    .build();
        }

        try {
            return medicationService.updateMedicationPlanEntry(kvnr, entryId, entry)
                    .map(updatedPlan -> Response.ok(updatedPlan).build())
                    .orElse(Response.status(Response.Status.NOT_FOUND)
                            .entity("{\"error\": \"Medication entry not found for ID: " + entryId + "\"}")
                            .build());
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("{\"error\": \"Failed to update medication entry: " + e.getMessage() + "\"}")
                    .build();
        }
    }

    /**
     * Change status of a medication entry.
     * 
     * @param kvnr      Patient KVNR
     * @param entryId   ID of the entry to update
     * @param newStatus New status (active, paused, completed)
     * @return 200 OK with updated plan, or 404 Not Found
     */
    @PATCH
    @Path("/plan/{kvnr}/entries/{entryId}/status")
    public Response changeMedicationStatus(
            @PathParam("kvnr") String kvnr,
            @PathParam("entryId") String entryId,
            @QueryParam("status") String newStatus) {

        if (kvnr == null || kvnr.trim().isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST).entity("{\"error\": \"KVNR is required\"}").build();
        }
        if (entryId == null || entryId.trim().isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST).entity("{\"error\": \"Entry ID is required\"}").build();
        }
        if (newStatus == null || newStatus.trim().isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST).entity("{\"error\": \"Status is required\"}").build();
        }

        try {
            return medicationService.changeMedicationStatus(kvnr, entryId, newStatus)
                    .map(updatedPlan -> Response.ok(updatedPlan).build())
                    .orElse(Response.status(Response.Status.NOT_FOUND)
                            .entity("{\"error\": \"Entry not found\"}")
                            .build());
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("{\"error\": \"Failed to update status: " + e.getMessage() + "\"}")
                    .build();
        }
    }

    /**
     * Delete a medication entry.
     * 
     * @param kvnr    Patient KVNR
     * @param entryId ID of the entry to delete
     * @return 204 No Content on success, or 404 Not Found
     */
    @DELETE
    @Path("/plan/{kvnr}/entries/{entryId}")
    public Response deleteMedicationEntry(
            @PathParam("kvnr") String kvnr,
            @PathParam("entryId") String entryId) {

        if (kvnr == null || kvnr.trim().isEmpty() || entryId == null || entryId.trim().isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        try {
            boolean deleted = medicationService.deleteMedicationEntry(kvnr, entryId);
            if (deleted) {
                return Response.noContent().build();
            } else {
                return Response.status(Response.Status.NOT_FOUND).build();
            }
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("{\"error\": \"Failed to delete entry: " + e.getMessage() + "\"}")
                    .build();
        }
    }

    /**
     * Create a link between an eML entry and an eMP entry.
     */
    @POST
    @Path("/link")
    public Response createLink(
            @PathParam("kvnr") String kvnr, // Not used in path but consistent with others
            @QueryParam("emlId") String emlId,
            @QueryParam("empId") String empId) {

        if (emlId == null || emlId.isEmpty() || empId == null || empId.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"error\": \"emlId and empId are required\"}")
                    .build();
        }

        try {
            // In a real app we'd validate KVNR too, but for prototype the service handles
            // the map
            medicationService.createLink("dummy", emlId, empId);
            return Response.ok().build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("{\"error\": \"Failed to create link: " + e.getMessage() + "\"}")
                    .build();
        }
    }

    /**
     * Remove a link for an eML entry.
     */
    @DELETE
    @Path("/link")
    public Response removeLink(
            @QueryParam("emlId") String emlId) {

        if (emlId == null || emlId.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"error\": \"emlId is required\"}")
                    .build();
        }

        try {
            boolean removed = medicationService.removeLink("dummy", emlId);
            if (removed) {
                return Response.noContent().build();
            } else {
                return Response.status(Response.Status.NOT_FOUND).build();
            }
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("{\"error\": \"Failed to remove link: " + e.getMessage() + "\"}")
                    .build();
        }
    }

    /**
     * Get reconciliation items for a patient.
     * 
     * @param kvnr Patient KVNR
     * @return List of ReconciliationItem
     */
    @GET
    @Path("/reconciliation/{kvnr}")
    public Response getReconciliation(@PathParam("kvnr") String kvnr) {
        if (kvnr == null || kvnr.trim().isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"error\": \"KVNR parameter is required\"}")
                    .build();
        }

        try {
            List<ReconciliationItem> items = medicationService.getReconciliation(kvnr);
            return Response.ok(items).build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("{\"error\": \"Failed to load reconciliation items: " + e.getMessage() + "\"}")
                    .build();
        }
    }
}
