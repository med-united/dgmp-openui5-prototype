package de.servicehealth.epa.medication;

import de.servicehealth.epa.medication.model.DuplicateMatch;

import de.servicehealth.epa.medication.model.MedicationPlan;
import de.servicehealth.epa.medication.model.MedicationRequest;

import de.servicehealth.epa.medication.model.PrescriptionGroup;
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
     * @param entry            MedicationRequest to add
     * @return 201 Created with updated plan, or 409 Conflict if duplicate detected
     */
    @POST
    @Path("/plan/{kvnr}/entries")
    public Response addMedicationRequest(
            @PathParam("kvnr") String kvnr,
            @QueryParam("ignoreDuplicates") @DefaultValue("false") boolean ignoreDuplicates,
            MedicationRequest entry) {
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
                    .addMedicationRequest(kvnr, entry);
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
     * @param entry   Updated MedicationRequest data
     * @return 200 OK with updated plan, or 404 Not Found if entry doesn't exist
     */
    @PUT
    @Path("/plan/{kvnr}/entries/{entryId}")
    public Response updateMedicationRequest(
            @PathParam("kvnr") String kvnr,
            @PathParam("entryId") String entryId,
            MedicationRequest entry) {
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
            return medicationService.updateMedicationRequest(kvnr, entryId, entry)
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
     * Get reconciliation tree for a patient (dgMP compliant).
     * 
     * @param kvnr Patient KVNR
     * @return List of PrescriptionGroup
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
            List<PrescriptionGroup> groups = medicationService.getReconciliationTree(kvnr);
            return Response.ok(groups).build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("{\"error\": \"Failed to load reconciliation items: " + e.getMessage() + "\"}")
                    .build();
        }

    }

    @GET
    @Path("/chronology/{kvnr}")
    public Response getChronology(@PathParam("kvnr") String kvnr) {
        try {
            return Response.ok(medicationService.getChronology(kvnr)).build();
        } catch (Exception e) {
            return Response.serverError().entity(e.getMessage()).build();
        }
    }

    @POST
    @Path("/{kvnr}/link-emp")
    public Response linkEmp(
            @HeaderParam("X-Requesting-Organization") String agent,
            @PathParam("kvnr") String kvnr,
            @QueryParam("emlId") String emlId,
            @QueryParam("empId") String empId) {

        try {
            medicationService.linkMedicationPlanEntry(kvnr, emlId, empId, agent);
            return Response.ok().build();
        } catch (IllegalArgumentException e) {
            return Response.status(422).entity("{\"error\": \"" + e.getMessage() + "\"}").build(); // Unprocessable
                                                                                                   // Entity for
                                                                                                   // validation errors
        } catch (Exception e) {
            return Response.serverError().entity("{\"error\": \"" + e.getMessage() + "\"}").build();
        }
    }

    @POST
    @Path("/{kvnr}/unlink-emp")
    public Response unlinkEmp(
            @HeaderParam("X-Requesting-Organization") String agent,
            @PathParam("kvnr") String kvnr,
            @QueryParam("emlId") String emlId) {

        try {
            medicationService.unlinkMedicationPlanEntry(kvnr, emlId, agent);
            return Response.ok().build();
        } catch (IllegalArgumentException e) {
            return Response.status(422).entity("{\"error\": \"" + e.getMessage() + "\"}").build();
        } catch (Exception e) {
            return Response.serverError().entity("{\"error\": \"" + e.getMessage() + "\"}").build();
        }
    }

    @POST
    @Path("/{kvnr}/add-emp-entry")
    public Response addEmpEntry(
            @HeaderParam("X-Requesting-Organization") String agent,
            @PathParam("kvnr") String kvnr,
            @QueryParam("linkedEmlId") String linkedEmlId,
            MedicationRequest entry) {

        try {
            MedicationRequest created = medicationService.addMedicationRequest(kvnr, entry, linkedEmlId, agent);
            return Response.status(Response.Status.CREATED).entity(created).build();
        } catch (IllegalArgumentException e) {
            // Check for specific error codes if mapped
            if (e.getMessage().contains("DOSAGE"))
                return Response.status(400).entity("{\"error\": \"" + e.getMessage() + "\"}").build();
            return Response.status(422).entity("{\"error\": \"" + e.getMessage() + "\"}").build();
        } catch (Exception e) {
            return Response.serverError().entity("{\"error\": \"" + e.getMessage() + "\"}").build();
        }
    }

    @PUT
    @Path("/{kvnr}/update-emp-entry/{entryId}")
    public Response updateEmpEntry(
            @HeaderParam("X-Requesting-Organization") String agent,
            @PathParam("kvnr") String kvnr,
            @PathParam("entryId") String entryId,
            @QueryParam("linkedEmlId") String linkedEmlId,
            @QueryParam("chronologyId") String chronologyId,
            MedicationRequest entry) {

        try {
            MedicationPlan updatedPlan = medicationService.updateMedicationRequest(kvnr, entryId, entry, linkedEmlId,
                    chronologyId, agent);
            return Response.ok(updatedPlan).build();
        } catch (IllegalArgumentException e) {
            if (e.getMessage().contains("CHRONOLOGY"))
                return Response.status(409).entity("{\"error\": \"" + e.getMessage() + "\"}").build();
            if (e.getMessage().contains("DOSAGE"))
                return Response.status(400).entity("{\"error\": \"" + e.getMessage() + "\"}").build();
            return Response.status(422).entity("{\"error\": \"" + e.getMessage() + "\"}").build();
        } catch (Exception e) {
            return Response.serverError().entity("{\"error\": \"" + e.getMessage() + "\"}").build();
        }
    }
}
