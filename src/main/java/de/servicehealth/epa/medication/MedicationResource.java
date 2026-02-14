package de.servicehealth.epa.medication;

import de.servicehealth.epa.medication.model.MedicationList;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

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
}
