package de.servicehealth.epa.patient;

import de.servicehealth.epa.patient.model.Patient;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;

/**
 * JAX-RS REST endpoint for patient operations
 */
@Path("/api/patients")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class PatientResource {

    @Inject
    PatientService patientService;

    /**
     * Search for patient by KVNR
     * GET /api/patients/search?kvnr=X123456789
     */
    @GET
    @Path("/search")
    public Response searchByKvnr(@QueryParam("kvnr") String kvnr) {
        if (kvnr == null || kvnr.trim().isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"error\": \"KVNR parameter required\"}")
                    .build();
        }

        return patientService.findByKvnr(kvnr)
                .map(patient -> Response.ok(patient).build())
                .orElse(Response.status(Response.Status.NOT_FOUND)
                        .entity("{\"error\": \"Patient not found\"}")
                        .build());
    }

    /**
     * Get recent patients list
     * GET /api/patients/recent
     */
    @GET
    @Path("/recent")
    public List<Patient> getRecentPatients() {
        return patientService.getRecentPatients();
    }
}
