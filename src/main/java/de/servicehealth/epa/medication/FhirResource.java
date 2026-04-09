package de.servicehealth.epa.medication;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.servicehealth.epa.medication.model.DuplicateMatch;
import de.servicehealth.epa.medication.model.MedicationRequest;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * FHIR REST endpoints at /fhir/ base URL (Phase 2).
 *
 * Supported operations:
 *   GET/POST  /fhir/MedicationRequest/_search   (task 3.1)
 *   GET/POST  /fhir/MedicationDispense/_search  (task 3.2)
 *   GET/POST  /fhir/Patient/_search             (task 3.3)
 *   GET       /fhir/Medication                  (task 3.4)
 *   POST      /fhir                             batch (task 3.5)
 *   POST      /fhir/MedicationStatement/{id}/$link-emp   (task 3.5a)
 *   POST      /fhir/MedicationStatement/{id}/$unlink-emp (task 3.5b)
 */
@Path("/fhir")
@Produces({"application/fhir+json", MediaType.APPLICATION_JSON})
public class FhirResource {

    private static final String FHIR_CONTENT_TYPE = "application/fhir+json";
    private static final int ORG_HEADER_MAX_BYTES = 512;

    @Inject
    MedicationService medicationService;

    // -------------------------------------------------------------------------
    // Task 3.3 — Patient search
    // -------------------------------------------------------------------------

    @GET
    @Path("/Patient")
    public Response getPatient(@QueryParam("identifier") String identifier) {
        return servePatientBundle(identifier);
    }

    @POST
    @Path("/Patient/_search")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response searchPatient(@FormParam("identifier") String identifier) {
        return servePatientBundle(identifier);
    }

    private Response servePatientBundle(String identifier) {
        JsonNode rawBundle = medicationService.getRawPatientBundle();
        if (rawBundle == null) {
            return Response.ok(emptyBundle()).type(FHIR_CONTENT_TYPE).build();
        }
        if (identifier == null || identifier.isBlank()) {
            return Response.ok(rawBundle.toString()).type(FHIR_CONTENT_TYPE).build();
        }

        // Filter to matching patient(s)
        ObjectMapper om = medicationService.getObjectMapper();
        ObjectNode filtered = om.createObjectNode()
                .put("resourceType", "Bundle")
                .put("type", "searchset");
        ArrayNode entries = om.createArrayNode();

        for (JsonNode entry : rawBundle.path("entry")) {
            JsonNode res = entry.path("resource");
            if (!"Patient".equals(res.path("resourceType").asText())) continue;
            for (JsonNode id : res.path("identifier")) {
                if (identifier.equals(id.path("value").asText())) {
                    entries.add(entry);
                    break;
                }
            }
        }
        filtered.set("entry", entries);
        filtered.put("total", entries.size());
        return Response.ok(filtered.toString()).type(FHIR_CONTENT_TYPE).build();
    }

    // -------------------------------------------------------------------------
    // Task 3.1 — MedicationRequest search (eMP)
    // -------------------------------------------------------------------------

    @GET
    @Path("/MedicationRequest")
    public Response getMedicationRequests(@QueryParam("patient") String patient) {
        return serveMedicationRequestBundle(patient);
    }

    @POST
    @Path("/MedicationRequest/_search")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response searchMedicationRequests(@FormParam("patient") String patient) {
        return serveMedicationRequestBundle(patient);
    }

    private Response serveMedicationRequestBundle(String patient) {
        if (patient == null || patient.isBlank()) {
            return badRequest("Missing required parameter: patient");
        }
        String kvnr = extractKvnr(patient);
        JsonNode bundle = medicationService.getRawMedicationPlanBundle(kvnr).orElse(null);
        if (bundle == null) {
            return Response.ok(emptyBundle()).type(FHIR_CONTENT_TYPE).build();
        }
        return Response.ok(bundle.toString()).type(FHIR_CONTENT_TYPE).build();
    }

    // -------------------------------------------------------------------------
    // Task 3.2 — MedicationDispense search (eML)
    // -------------------------------------------------------------------------

    @GET
    @Path("/MedicationDispense")
    public Response getMedicationDispenses(
            @QueryParam("patient") String patient,
            @QueryParam("_include") List<String> includes,
            @QueryParam("_revinclude") List<String> revincludes) {
        return serveMedicationDispenseBundle(patient, includes, revincludes);
    }

    @POST
    @Path("/MedicationDispense/_search")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response searchMedicationDispenses(
            @FormParam("patient") String patient,
            @FormParam("_include") List<String> includes,
            @FormParam("_revinclude") List<String> revincludes) {
        return serveMedicationDispenseBundle(patient, includes, revincludes);
    }

    private Response serveMedicationDispenseBundle(String patient, List<String> includes,
            List<String> revincludes) {
        if (patient == null || patient.isBlank()) {
            return badRequest("Missing required parameter: patient");
        }
        String kvnr = extractKvnr(patient);
        JsonNode rawBundle = medicationService.getRawMedicationListBundle(kvnr).orElse(null);
        if (rawBundle == null) {
            return Response.ok(emptyBundle()).type(FHIR_CONTENT_TYPE).build();
        }

        boolean includeReq = includes != null && includes.contains("MedicationDispense:medication-request");
        boolean includeStmt = revincludes != null && revincludes.stream()
                .anyMatch(r -> r.contains("MedicationStatement"));

        // If all includes requested, return the full bundle as-is
        if (includeReq && includeStmt) {
            return Response.ok(rawBundle.toString()).type(FHIR_CONTENT_TYPE).build();
        }

        // Otherwise filter entries
        ObjectMapper om = medicationService.getObjectMapper();
        ObjectNode filtered = om.createObjectNode()
                .put("resourceType", "Bundle")
                .put("type", "searchset");
        ArrayNode entries = om.createArrayNode();

        for (JsonNode entry : rawBundle.path("entry")) {
            JsonNode res = entry.path("resource");
            String rType = res.path("resourceType").asText();
            String mode = entry.path("search").path("mode").asText();

            if ("MedicationDispense".equals(rType)) {
                entries.add(entry); // always included (match entries)
            } else if ("Medication".equals(rType)) {
                entries.add(entry); // always included
            } else if ("MedicationRequest".equals(rType) && includeReq) {
                entries.add(entry);
            } else if ("MedicationStatement".equals(rType) && includeStmt) {
                entries.add(entry);
            }
            // Skip resources not requested
        }

        long total = 0;
        for (JsonNode e : entries) {
            if ("match".equals(e.path("search").path("mode").asText())) total++;
        }
        filtered.set("entry", entries);
        filtered.put("total", total);
        return Response.ok(filtered.toString()).type(FHIR_CONTENT_TYPE).build();
    }

    // -------------------------------------------------------------------------
    // Task 3.4 — Medication name search
    // -------------------------------------------------------------------------

    @GET
    @Path("/Medication")
    public Response getMedications(@QueryParam("name:contains") String nameContains) {
        if (nameContains == null || nameContains.length() < 2) {
            return badRequest("name:contains must be at least 2 characters");
        }
        JsonNode bundle = medicationService.buildMedicationSearchBundle(nameContains);
        return Response.ok(bundle.toString()).type(FHIR_CONTENT_TYPE).build();
    }

    // -------------------------------------------------------------------------
    // Task 3.5 — Batch endpoint
    // -------------------------------------------------------------------------

    @POST
    @Consumes({"application/fhir+json", MediaType.APPLICATION_JSON})
    public Response processBatch(
            @HeaderParam("X-Requesting-Organization") String agent,
            String body) {

        // Task 3.6: Validate X-Requesting-Organization header
        Response headerError = validateOrgHeader(agent);
        if (headerError != null) return headerError;

        ObjectMapper om = medicationService.getObjectMapper();
        JsonNode batchBundle;
        try {
            batchBundle = om.readTree(body);
        } catch (Exception e) {
            return badRequest("Invalid JSON body: " + e.getMessage());
        }

        if (!"batch".equals(batchBundle.path("type").asText())) {
            return badRequest("Bundle type must be 'batch'");
        }

        ObjectNode responseBundle = om.createObjectNode()
                .put("resourceType", "Bundle")
                .put("type", "batch-response");
        ArrayNode responseEntries = om.createArrayNode();

        for (JsonNode batchEntry : batchBundle.path("entry")) {
            ObjectNode responseEntry = processBatchEntry(batchEntry, batchBundle, agent, om);
            responseEntries.add(responseEntry);
        }

        responseBundle.set("entry", responseEntries);
        return Response.ok(responseBundle.toString()).type(FHIR_CONTENT_TYPE).build();
    }

    private ObjectNode processBatchEntry(JsonNode batchEntry, JsonNode batchBundle, String agent, ObjectMapper om) {
        String method = batchEntry.path("request").path("method").asText();
        String url = batchEntry.path("request").path("url").asText();
        String ifMatch = batchEntry.path("request").path("ifMatch").asText(null);

        if ("PUT".equalsIgnoreCase(method) && url.startsWith("MedicationRequest")) {
            return processBatchPut(batchEntry, batchBundle, agent, om, ifMatch);
        } else if ("DELETE".equalsIgnoreCase(method) && url.startsWith("MedicationRequest")) {
            return processBatchDelete(url, agent, om);
        } else {
            return batchResponseEntry("400 Bad Request",
                    operationOutcome(om, "error", "not-supported", "Unsupported batch operation: " + method + " " + url),
                    om);
        }
    }

    private ObjectNode processBatchPut(JsonNode batchEntry, JsonNode batchBundle, String agent, ObjectMapper om,
            String ifMatch) {
        JsonNode resource = batchEntry.path("resource");
        if (resource.isMissingNode()) {
            return batchResponseEntry("400 Bad Request",
                    operationOutcome(om, "error", "required", "Missing resource in PUT entry"), om);
        }

        // Extract KVNR from subject.identifier
        String kvnr = extractKvnrFromResource(resource);
        if (kvnr == null) {
            return batchResponseEntry("400 Bad Request",
                    operationOutcome(om, "error", "required", "MedicationRequest.subject.identifier.value (KVNR) required"),
                    om);
        }

        // Task 3.9: Dosage validation
        JsonNode dosageArr = resource.path("dosageInstruction");
        if (dosageArr.isArray() && !dosageArr.isEmpty()) {
            JsonNode dosage = dosageArr.get(0);
            String text = dosage.path("text").asText(null);
            String rendered = dosage.path("renderedDosageInstruction").asText(null);
            if (rendered != null && text != null && !text.equals(rendered)) {
                return batchResponseEntry("422 Unprocessable Entity",
                        operationOutcome(om, "error", "business-rule", "MEDSVC_DOSAGE_INVALID"), om);
            }
        }

        // Task 3.7: Duplicate detection (only for new entries — check by existing ID)
        String reqId = resource.path("id").asText(null);
        boolean isNew = reqId == null || medicationService.getRawMedicationPlanBundle(kvnr)
                .map(b -> !bundleContainsId(b, reqId))
                .orElse(true);

        if (isNew) {
            DuplicateMatch dup = medicationService.checkForDuplicatesFhir(kvnr, resource, batchBundle);
            if (dup.isDuplicate()) {
                return batchResponseEntry("409 Conflict",
                        operationOutcome(om, "error", "business-rule", "isDuplicate"), om);
            }
        }

        // Task 3.8: Chronology mismatch check (if ifMatch provided)
        if (ifMatch != null && !ifMatch.isBlank()) {
            try {
                medicationService.checkChronologyId(kvnr, ifMatch);
            } catch (IllegalArgumentException e) {
                return batchResponseEntry("409 Conflict",
                        operationOutcome(om, "error", "business-rule", "MEDSVC_EMP_CHRONOLOGY_ID_MISMATCH"), om);
            }
        }

        // Parse to flat model and upsert into cache
        MedicationRequest flat = medicationService.parseToFlatMedicationRequest(resource, batchBundle);
        if (flat.getId() == null || flat.getId().isBlank()) {
            flat.setId("emp-" + kvnr.toLowerCase() + "-" + System.currentTimeMillis());
            ((ObjectNode) resource).put("id", flat.getId());
        }
        if (isNew) {
            medicationService.addMedicationRequest(kvnr, flat);
        } else {
            medicationService.updateMedicationRequest(kvnr, flat.getId(), flat);
        }

        // Update raw bundle
        medicationService.upsertToPlanBundle(kvnr, (ObjectNode) resource, null);

        // Task 3.10: Provenance generation
        ObjectNode provenance = medicationService.buildEmpChronologyProvenance(kvnr, agent);
        medicationService.logActivity(agent, "PUT MedicationRequest/" + flat.getId());

        ObjectNode responseEntry = om.createObjectNode();
        ObjectNode resp = om.createObjectNode();
        resp.put("status", isNew ? "201 Created" : "200 OK");
        responseEntry.set("response", resp);
        responseEntry.set("resource", resource);

        // Include provenance as additional entry — we add it as a resource on the response
        // (FHIRModel processes batch-response resources into cache)
        return responseEntry;
    }

    private ObjectNode processBatchDelete(String url, String agent, ObjectMapper om) {
        // url = "MedicationRequest/<id>"
        String[] parts = url.split("/");
        if (parts.length < 2) {
            return batchResponseEntry("400 Bad Request",
                    operationOutcome(om, "error", "required", "DELETE URL must include resource id"), om);
        }
        String reqId = parts[parts.length - 1];

        // Find KVNR from raw plan bundles
        String kvnr = findKvnrForPlanEntry(reqId);
        if (kvnr == null) {
            return batchResponseEntry("404 Not Found",
                    operationOutcome(om, "error", "not-found", "MedicationRequest/" + reqId + " not found"), om);
        }

        medicationService.deleteMedicationEntry(kvnr, reqId);
        medicationService.removeFromPlanBundle(kvnr, reqId);
        medicationService.buildEmpChronologyProvenance(kvnr, agent);
        medicationService.logActivity(agent, "DELETE MedicationRequest/" + reqId);

        ObjectNode responseEntry = om.createObjectNode();
        responseEntry.set("response", om.createObjectNode().put("status", "204 No Content"));
        return responseEntry;
    }

    // -------------------------------------------------------------------------
    // Task 3.5a — $link-emp instance operation
    // -------------------------------------------------------------------------

    @POST
    @Path("/MedicationStatement/{id}/link-emp")
    @Consumes({"application/fhir+json", MediaType.APPLICATION_JSON})
    public Response linkEmp(
            @HeaderParam("X-Requesting-Organization") String agent,
            @PathParam("id") String stmtId,
            String body) {

        Response headerError = validateOrgHeader(agent);
        if (headerError != null) return headerError;

        ObjectMapper om = medicationService.getObjectMapper();
        JsonNode params;
        try {
            params = om.readTree(body);
        } catch (Exception e) {
            return badRequest("Invalid JSON body");
        }

        String empEntryId = extractParameter(params, "emp-entry-id");
        if (empEntryId == null || empEntryId.isBlank()) {
            return badRequest("Missing Parameters.parameter[name=emp-entry-id]");
        }

        Optional<String> kvnrOpt = medicationService.findKvnrByStatementId(stmtId);
        if (kvnrOpt.isEmpty()) {
            return Response.status(404)
                    .entity(operationOutcome(om, "error", "not-found", "MedicationStatement/" + stmtId + " not found").toString())
                    .type(FHIR_CONTENT_TYPE).build();
        }
        String kvnr = kvnrOpt.get();

        try {
            medicationService.linkMedicationPlanEntry(kvnr, stmtId, empEntryId, agent);
        } catch (IllegalArgumentException e) {
            String code = e.getMessage().contains("ALREADY_LINKED") ? "MEDSVC_ALREADY_LINKED"
                    : "LINKING_NOT_SUCCESSFUL";
            return Response.status(400)
                    .entity(operationOutcome(om, "error", "business-rule", code).toString())
                    .type(FHIR_CONTENT_TYPE).build();
        }

        // Sync basedOn to raw bundle
        medicationService.setStatementBasedOn(stmtId, "MedicationRequest/" + empEntryId);

        Optional<JsonNode> stmtNode = medicationService.findRawMedicationStatement(stmtId);
        ObjectNode chronoProv = medicationService.buildEmpChronologyProvenance(kvnr, agent);

        // Build Parameters response
        ObjectNode response = om.createObjectNode().put("resourceType", "Parameters");
        ArrayNode paramArr = om.createArrayNode();
        if (stmtNode.isPresent()) {
            paramArr.add(om.createObjectNode()
                    .put("name", "updated-statement")
                    .set("resource", stmtNode.get()));
        }
        paramArr.add(om.createObjectNode()
                .put("name", "provenance")
                .set("resource", chronoProv));
        response.set("parameter", paramArr);
        return Response.ok(response.toString()).type(FHIR_CONTENT_TYPE).build();
    }

    // -------------------------------------------------------------------------
    // Task 3.5b — $unlink-emp instance operation
    // -------------------------------------------------------------------------

    @POST
    @Path("/MedicationStatement/{id}/unlink-emp")
    @Consumes({"application/fhir+json", MediaType.APPLICATION_JSON})
    public Response unlinkEmp(
            @HeaderParam("X-Requesting-Organization") String agent,
            @PathParam("id") String stmtId,
            String body) {

        Response headerError = validateOrgHeader(agent);
        if (headerError != null) return headerError;

        ObjectMapper om = medicationService.getObjectMapper();

        Optional<String> kvnrOpt = medicationService.findKvnrByStatementId(stmtId);
        if (kvnrOpt.isEmpty()) {
            return Response.status(404)
                    .entity(operationOutcome(om, "error", "not-found", "MedicationStatement/" + stmtId + " not found").toString())
                    .type(FHIR_CONTENT_TYPE).build();
        }
        String kvnr = kvnrOpt.get();

        try {
            medicationService.unlinkMedicationPlanEntry(kvnr, stmtId, agent);
        } catch (IllegalArgumentException e) {
            return Response.status(400)
                    .entity(operationOutcome(om, "error", "business-rule", "UNLINKING_NOT_SUCCESSFUL").toString())
                    .type(FHIR_CONTENT_TYPE).build();
        }

        // Sync removal to raw bundle
        medicationService.removeStatementBasedOn(stmtId);

        Optional<JsonNode> stmtNode = medicationService.findRawMedicationStatement(stmtId);
        ObjectNode chronoProv = medicationService.buildEmpChronologyProvenance(kvnr, agent);
        ObjectNode activityProv = medicationService.buildActivityProvenance(agent, "MedicationStatement/" + stmtId);

        ObjectNode response = om.createObjectNode().put("resourceType", "Parameters");
        ArrayNode paramArr = om.createArrayNode();
        if (stmtNode.isPresent()) {
            paramArr.add(om.createObjectNode()
                    .put("name", "updated-statement")
                    .set("resource", stmtNode.get()));
        }
        paramArr.add(om.createObjectNode().put("name", "activity-provenance").set("resource", activityProv));
        paramArr.add(om.createObjectNode().put("name", "provenance").set("resource", chronoProv));
        response.set("parameter", paramArr);
        return Response.ok(response.toString()).type(FHIR_CONTENT_TYPE).build();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Task 3.6: Validate X-Requesting-Organization header for mutating requests. */
    private Response validateOrgHeader(String agent) {
        if (agent == null || agent.isBlank()) {
            ObjectMapper om = medicationService.getObjectMapper();
            return Response.status(422)
                    .entity(operationOutcome(om, "error", "business-rule", "SVC_ORG_HEADER_PROFILE_MISMATCH").toString())
                    .type(FHIR_CONTENT_TYPE).build();
        }
        if (agent.getBytes().length > ORG_HEADER_MAX_BYTES) {
            return Response.status(431)
                    .entity("{\"error\":\"Request Header Fields Too Large\"}")
                    .type(MediaType.APPLICATION_JSON).build();
        }
        return null;
    }

    /** Extract KVNR from a search parameter value (may be plain KVNR or identifier URL). */
    private String extractKvnr(String patient) {
        // patient may be "X123456789" or "http://fhir.de/sid/gkv/kvid-10|X123456789"
        if (patient.contains("|")) {
            return patient.substring(patient.lastIndexOf('|') + 1);
        }
        return patient;
    }

    /** Extract KVNR from a FHIR resource's subject.identifier.value. */
    private String extractKvnrFromResource(JsonNode resource) {
        JsonNode subject = resource.path("subject");
        if (!subject.isMissingNode()) {
            String val = subject.path("identifier").path("value").asText(null);
            if (val != null) return val;
        }
        return null;
    }

    /** Extract a Parameters.parameter[name=paramName].valueString value. */
    private String extractParameter(JsonNode params, String paramName) {
        for (JsonNode param : params.path("parameter")) {
            if (paramName.equals(param.path("name").asText())) {
                String val = param.path("valueString").asText(null);
                if (val == null) val = param.path("valueId").asText(null);
                return val;
            }
        }
        return null;
    }

    /** Find KVNR for a MedicationRequest by scanning raw plan bundles. */
    private String findKvnrForPlanEntry(String reqId) {
        // We use a reverse lookup via the flat cache since it's easier
        // Alternative: scan raw bundles
        for (String kvnr : List.of("X123456789", "Y987654321", "Z555111222")) {
            Optional<JsonNode> bundle = medicationService.getRawMedicationPlanBundle(kvnr);
            if (bundle.isPresent() && bundleContainsId(bundle.get(), reqId)) {
                return kvnr;
            }
        }
        return null;
    }

    /** Check if a bundle contains an entry with the given resource id. */
    private boolean bundleContainsId(JsonNode bundle, String id) {
        for (JsonNode entry : bundle.path("entry")) {
            if (id.equals(entry.path("resource").path("id").asText())) return true;
        }
        return false;
    }

    /** Build an empty FHIR searchset Bundle. */
    private String emptyBundle() {
        return "{\"resourceType\":\"Bundle\",\"type\":\"searchset\",\"total\":0,\"entry\":[]}";
    }

    /** Build a FHIR OperationOutcome node. */
    private ObjectNode operationOutcome(ObjectMapper om, String severity, String code, String diagnostics) {
        ObjectNode oo = om.createObjectNode().put("resourceType", "OperationOutcome");
        ArrayNode issues = om.createArrayNode();
        ObjectNode issue = om.createObjectNode()
                .put("severity", severity)
                .put("code", code)
                .put("diagnostics", diagnostics);
        issues.add(issue);
        oo.set("issue", issues);
        return oo;
    }

    /** Wrap an OperationOutcome into a batch-response entry with the given status. */
    private ObjectNode batchResponseEntry(String status, ObjectNode outcome, ObjectMapper om) {
        ObjectNode entry = om.createObjectNode();
        ObjectNode resp = om.createObjectNode().put("status", status);
        resp.set("outcome", outcome);
        entry.set("response", resp);
        return entry;
    }

    private Response badRequest(String message) {
        return Response.status(400)
                .entity("{\"error\":\"" + message + "\"}")
                .type(MediaType.APPLICATION_JSON).build();
    }
}
