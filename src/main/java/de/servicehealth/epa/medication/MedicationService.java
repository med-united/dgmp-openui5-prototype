package de.servicehealth.epa.medication;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import de.servicehealth.epa.medication.model.DuplicateMatch;
import de.servicehealth.epa.medication.model.EMPChronologyProvenance;
import de.servicehealth.epa.medication.model.EPAActivityProvenance;
import jakarta.enterprise.context.ApplicationScoped;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages raw FHIR Bundle state for the mock ePA Medication Service.
 * All data is stored and served as FHIR R4 JSON; no flat projection is maintained.
 */
@ApplicationScoped
public class MedicationService {

    private final ConcurrentHashMap<String, JsonNode> rawMedicationListBundles = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, JsonNode> rawMedicationPlanBundles = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, EMPChronologyProvenance> chronologyCache = new ConcurrentHashMap<>();
    private final List<EPAActivityProvenance> activityLog = new ArrayList<>();
    private volatile JsonNode rawPatientBundle;

    private final ObjectMapper objectMapper;

    public MedicationService() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        loadAllFixtures();
    }

    // -------------------------------------------------------------------------
    // Fixture loading
    // -------------------------------------------------------------------------

    private void loadAllFixtures() {
        for (String kvnr : List.of("X123456789", "Y987654321", "Z555111222")) {
            loadListFixture(kvnr);
            loadPlanFixture(kvnr);
        }
        loadPatientBundle();
    }

    private void loadListFixture(String kvnr) {
        String filename = "/fixtures/medication-list-" + kvnr + ".json";
        try (InputStream is = getClass().getResourceAsStream(filename)) {
            if (is != null) {
                rawMedicationListBundles.put(kvnr, objectMapper.readTree(is));
                System.out.println("Loaded medication list bundle for KVNR: " + kvnr);
            } else {
                System.err.println("Medication list fixture not found: " + filename);
            }
        } catch (Exception e) {
            System.err.println("Failed to load medication list fixture: " + filename);
            e.printStackTrace();
        }
    }

    private void loadPlanFixture(String kvnr) {
        String filename = "/fixtures/medication-plan-" + kvnr + ".json";
        try (InputStream is = getClass().getResourceAsStream(filename)) {
            if (is != null) {
                rawMedicationPlanBundles.put(kvnr, objectMapper.readTree(is));
                System.out.println("Loaded medication plan bundle for KVNR: " + kvnr);
            } else {
                System.err.println("Medication plan fixture not found: " + filename);
            }
        } catch (IOException e) {
            System.err.println("Failed to load medication plan fixture: " + filename);
            e.printStackTrace();
        }
    }

    private void loadPatientBundle() {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("fixtures/patients.json")) {
            if (is != null) {
                rawPatientBundle = objectMapper.readTree(is);
            }
        } catch (Exception e) {
            System.err.println("Failed to load patient bundle: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Raw bundle accessors
    // -------------------------------------------------------------------------

    public ObjectMapper getObjectMapper() {
        return objectMapper;
    }

    public Optional<JsonNode> getRawMedicationPlanBundle(String kvnr) {
        return Optional.ofNullable(rawMedicationPlanBundles.get(kvnr));
    }

    public Optional<JsonNode> getRawMedicationListBundle(String kvnr) {
        return Optional.ofNullable(rawMedicationListBundles.get(kvnr));
    }

    public JsonNode getRawPatientBundle() {
        return rawPatientBundle;
    }

    // -------------------------------------------------------------------------
    // MedicationStatement raw bundle operations
    // -------------------------------------------------------------------------

    /** Find which KVNR owns a MedicationStatement by scanning raw list bundles. */
    public Optional<String> findKvnrByStatementId(String stmtId) {
        for (Map.Entry<String, JsonNode> e : rawMedicationListBundles.entrySet()) {
            for (JsonNode entry : e.getValue().path("entry")) {
                JsonNode res = entry.path("resource");
                if ("MedicationStatement".equals(res.path("resourceType").asText())
                        && stmtId.equals(res.path("id").asText())) {
                    return Optional.of(e.getKey());
                }
            }
        }
        return Optional.empty();
    }

    /** Find the raw MedicationStatement JsonNode by id across all list bundles. */
    public Optional<JsonNode> findRawMedicationStatement(String stmtId) {
        for (JsonNode bundle : rawMedicationListBundles.values()) {
            for (JsonNode entry : bundle.path("entry")) {
                JsonNode res = entry.path("resource");
                if ("MedicationStatement".equals(res.path("resourceType").asText())
                        && stmtId.equals(res.path("id").asText())) {
                    return Optional.of(res);
                }
            }
        }
        return Optional.empty();
    }

    /** Set or replace MedicationStatement.basedOn in the raw list bundle. */
    public void setStatementBasedOn(String stmtId, String reference) {
        for (JsonNode bundle : rawMedicationListBundles.values()) {
            for (JsonNode entry : bundle.path("entry")) {
                JsonNode res = entry.path("resource");
                if ("MedicationStatement".equals(res.path("resourceType").asText())
                        && stmtId.equals(res.path("id").asText())) {
                    ArrayNode basedOn = objectMapper.createArrayNode();
                    basedOn.addObject().put("reference", reference);
                    ((ObjectNode) res).set("basedOn", basedOn);
                    return;
                }
            }
        }
    }

    /** Remove MedicationStatement.basedOn from the raw list bundle. */
    public void removeStatementBasedOn(String stmtId) {
        for (JsonNode bundle : rawMedicationListBundles.values()) {
            for (JsonNode entry : bundle.path("entry")) {
                JsonNode res = entry.path("resource");
                if ("MedicationStatement".equals(res.path("resourceType").asText())
                        && stmtId.equals(res.path("id").asText())) {
                    ((ObjectNode) res).remove("basedOn");
                    return;
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // MedicationRequest (plan) raw bundle mutations
    // -------------------------------------------------------------------------

    /**
     * Upsert a MedicationRequest (and optional Medication) into the raw plan bundle.
     * Creates the bundle if it doesn't exist yet.
     */
    public void upsertToPlanBundle(String kvnr, JsonNode medicationRequestNode, JsonNode medicationNode) {
        rawMedicationPlanBundles.compute(kvnr, (k, existing) -> {
            ObjectNode bundle = existing != null ? (ObjectNode) existing : objectMapper.createObjectNode()
                    .put("resourceType", "Bundle")
                    .put("type", "searchset")
                    .put("total", 0);
            if (!bundle.has("entry")) {
                bundle.set("entry", objectMapper.createArrayNode());
            }
            ArrayNode entries = (ArrayNode) bundle.get("entry");

            // Upsert Medication (include mode) — skip if already present
            if (medicationNode != null) {
                String medId = medicationNode.path("id").asText();
                boolean exists = false;
                for (JsonNode e : entries) {
                    if (medId.equals(e.path("resource").path("id").asText())) {
                        exists = true;
                        break;
                    }
                }
                if (!exists) {
                    ObjectNode medEntry = objectMapper.createObjectNode();
                    medEntry.put("fullUrl", "https://example.org/fhir/Medication/" + medId);
                    medEntry.set("search", objectMapper.createObjectNode().put("mode", "include"));
                    medEntry.set("resource", medicationNode);
                    entries.add(medEntry);
                }
            }

            // Replace existing MedicationRequest entry (match mode)
            String reqId = medicationRequestNode.path("id").asText();
            ArrayNode updated = objectMapper.createArrayNode();
            for (JsonNode e : entries) {
                if (!reqId.equals(e.path("resource").path("id").asText())) {
                    updated.add(e);
                }
            }
            ObjectNode reqEntry = objectMapper.createObjectNode();
            reqEntry.put("fullUrl", "https://example.org/fhir/MedicationRequest/" + reqId);
            reqEntry.set("search", objectMapper.createObjectNode().put("mode", "match"));
            reqEntry.set("resource", medicationRequestNode);
            updated.add(reqEntry);
            bundle.set("entry", updated);

            long total = 0;
            for (JsonNode e : updated) {
                if ("match".equals(e.path("search").path("mode").asText())) total++;
            }
            bundle.put("total", total);
            return bundle;
        });
    }

    /** Remove a MedicationRequest from the raw plan bundle by id. */
    public void removeFromPlanBundle(String kvnr, String reqId) {
        JsonNode bundle = rawMedicationPlanBundles.get(kvnr);
        if (bundle == null) return;
        ArrayNode entries = (ArrayNode) bundle.path("entry");
        if (entries.isMissingNode()) return;
        ArrayNode updated = objectMapper.createArrayNode();
        for (JsonNode e : entries) {
            if (!reqId.equals(e.path("resource").path("id").asText())) updated.add(e);
        }
        ((ObjectNode) bundle).set("entry", updated);
        long total = 0;
        for (JsonNode e : updated) {
            if ("match".equals(e.path("search").path("mode").asText())) total++;
        }
        ((ObjectNode) bundle).put("total", total);
    }

    // -------------------------------------------------------------------------
    // Medication name search
    // -------------------------------------------------------------------------

    /**
     * Build a FHIR Bundle (searchset) of Medication resources whose code.text
     * contains the search term. Searches across all cached plan and list bundles.
     */
    public JsonNode buildMedicationSearchBundle(String nameContains) {
        ObjectNode bundle = objectMapper.createObjectNode()
                .put("resourceType", "Bundle")
                .put("type", "searchset");
        ArrayNode entries = objectMapper.createArrayNode();
        String lowerTerm = nameContains != null ? nameContains.toLowerCase().trim() : "";
        Set<String> seen = new HashSet<>();

        for (JsonNode source : rawMedicationPlanBundles.values()) {
            collectMatchingMedications(source, lowerTerm, seen, entries);
        }
        for (JsonNode source : rawMedicationListBundles.values()) {
            collectMatchingMedications(source, lowerTerm, seen, entries);
        }
        bundle.set("entry", entries);
        bundle.put("total", entries.size());
        return bundle;
    }

    private void collectMatchingMedications(JsonNode source, String lowerTerm, Set<String> seen, ArrayNode out) {
        for (JsonNode e : source.path("entry")) {
            JsonNode res = e.path("resource");
            if (!"Medication".equals(res.path("resourceType").asText())) continue;
            String id = res.path("id").asText();
            if (seen.contains(id)) continue;
            String text = res.path("code").path("text").asText("").toLowerCase();
            if (text.contains(lowerTerm)) {
                seen.add(id);
                ObjectNode matchEntry = objectMapper.createObjectNode();
                matchEntry.set("search", objectMapper.createObjectNode().put("mode", "match"));
                matchEntry.set("resource", res);
                out.add(matchEntry);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Duplicate detection (operates on raw FHIR bundles)
    // -------------------------------------------------------------------------

    /**
     * Check for PZN/ATC duplicates against the raw plan bundle for a given KVNR.
     * The incoming resource and its Medication are resolved from the batch bundle.
     */
    public DuplicateMatch checkForDuplicatesFhir(String kvnr, JsonNode incomingReq, JsonNode batchBundle) {
        Map<String, JsonNode> incomingMeds = collectMedications(batchBundle);
        String incomingPzn = extractPzn(incomingReq, incomingMeds);
        String incomingAtc = extractAtc(incomingReq, incomingMeds);

        JsonNode planBundle = rawMedicationPlanBundles.get(kvnr);
        if (planBundle == null) return new DuplicateMatch(false, null);

        Map<String, JsonNode> planMeds = collectMedications(planBundle);
        for (JsonNode entry : planBundle.path("entry")) {
            JsonNode res = entry.path("resource");
            if (!"MedicationRequest".equals(res.path("resourceType").asText())) continue;

            String existingPzn = extractPzn(res, planMeds);
            String existingAtc = extractAtc(res, planMeds);

            if (incomingPzn != null && incomingPzn.equals(existingPzn)) {
                return new DuplicateMatch(true, "PZN");
            }
            if (incomingAtc != null && existingAtc != null
                    && incomingAtc.length() >= 5 && existingAtc.length() >= 5
                    && incomingAtc.substring(0, 5).equals(existingAtc.substring(0, 5))) {
                return new DuplicateMatch(true, "ATC");
            }
        }
        return new DuplicateMatch(false, null);
    }

    private String extractPzn(JsonNode resource, Map<String, JsonNode> medications) {
        JsonNode med = resolveMedication(resource, medications);
        if (med != null) return extractCoding(med.path("code"), "http://fhir.de/CodeSystem/ifa/pzn");
        return extractCoding(resource.path("medicationCodeableConcept"), "http://fhir.de/CodeSystem/ifa/pzn");
    }

    private String extractAtc(JsonNode resource, Map<String, JsonNode> medications) {
        JsonNode med = resolveMedication(resource, medications);
        if (med != null) return extractCoding(med.path("code"), "http://www.whocc.no/atc");
        return extractCoding(resource.path("medicationCodeableConcept"), "http://www.whocc.no/atc");
    }

    // -------------------------------------------------------------------------
    // Chronology (optimistic locking)
    // -------------------------------------------------------------------------

    public EMPChronologyProvenance getChronology(String kvnr) {
        return chronologyCache.computeIfAbsent(kvnr, k -> new EMPChronologyProvenance(java.util.UUID.randomUUID().toString()));
    }

    public EMPChronologyProvenance updateChronology(String kvnr) {
        EMPChronologyProvenance prov = new EMPChronologyProvenance(java.util.UUID.randomUUID().toString());
        chronologyCache.put(kvnr, prov);
        return prov;
    }

    /**
     * Check if the provided chronology ID matches the current one.
     * Throws IllegalArgumentException on mismatch.
     */
    public void checkChronologyId(String kvnr, String acknowledgedId) {
        EMPChronologyProvenance current = getChronology(kvnr);
        if (current != null && !current.getId().equals(acknowledgedId)) {
            throw new IllegalArgumentException("MEDSVC_EMP_CHRONOLOGY_ID_MISMATCH");
        }
    }

    // -------------------------------------------------------------------------
    // Provenance builders
    // -------------------------------------------------------------------------

    public void logActivity(String agent, String reference) {
        EPAActivityProvenance prov = new EPAActivityProvenance(java.util.UUID.randomUUID().toString(), agent, reference);
        activityLog.add(prov);
        System.out.println("AUDIT: " + agent + " modified " + reference);
    }

    public ObjectNode buildEmpChronologyProvenance(String kvnr, String agent) {
        EMPChronologyProvenance chrono = updateChronology(kvnr);
        ObjectNode prov = objectMapper.createObjectNode();
        prov.put("resourceType", "Provenance");
        prov.put("id", chrono.getId());
        ObjectNode meta = objectMapper.createObjectNode();
        meta.set("profile", objectMapper.createArrayNode()
                .add("https://gematik.de/fhir/epa-medication/StructureDefinition/epa-emp-chronology-provenance|1.3.0"));
        prov.set("meta", meta);
        prov.put("recorded", java.time.Instant.now().toString());
        ObjectNode agentNode = objectMapper.createObjectNode();
        agentNode.set("who", objectMapper.createObjectNode()
                .set("identifier", objectMapper.createObjectNode().put("value", agent != null ? agent : "unknown")));
        prov.set("agent", objectMapper.createArrayNode().add(agentNode));
        return prov;
    }

    public ObjectNode buildActivityProvenance(String agent, String reference) {
        logActivity(agent, reference);
        ObjectNode prov = objectMapper.createObjectNode();
        prov.put("resourceType", "Provenance");
        prov.put("id", java.util.UUID.randomUUID().toString());
        ObjectNode meta = objectMapper.createObjectNode();
        meta.set("profile", objectMapper.createArrayNode()
                .add("https://gematik.de/fhir/epa-medication/StructureDefinition/epa-activity-provenance|1.3.0"));
        prov.set("meta", meta);
        prov.put("recorded", java.time.Instant.now().toString());
        ObjectNode agentNode = objectMapper.createObjectNode();
        agentNode.set("who", objectMapper.createObjectNode()
                .set("identifier", objectMapper.createObjectNode().put("value", agent != null ? agent : "unknown")));
        prov.set("agent", objectMapper.createArrayNode().add(agentNode));
        prov.set("target", objectMapper.createArrayNode()
                .add(objectMapper.createObjectNode().put("reference", reference)));
        return prov;
    }

    // -------------------------------------------------------------------------
    // FHIR JSON helpers
    // -------------------------------------------------------------------------

    private Map<String, JsonNode> collectMedications(JsonNode bundle) {
        Map<String, JsonNode> result = new HashMap<>();
        for (JsonNode entry : bundle.path("entry")) {
            JsonNode resource = entry.path("resource");
            if ("Medication".equals(resource.path("resourceType").asText())) {
                String id = resource.path("id").asText("");
                if (!id.isEmpty()) result.put(id, resource);
            }
        }
        return result;
    }

    private JsonNode resolveMedication(JsonNode resource, Map<String, JsonNode> medications) {
        JsonNode ref = resource.path("medicationReference");
        if (!ref.isMissingNode()) {
            String refStr = ref.path("reference").asText("");
            if (!refStr.isEmpty()) {
                String medId = refStr.contains("/") ? refStr.substring(refStr.lastIndexOf('/') + 1) : refStr;
                return medications.get(medId);
            }
        }
        return null;
    }

    private String extractCoding(JsonNode concept, String system) {
        for (JsonNode coding : concept.path("coding")) {
            if (system.equals(coding.path("system").asText())) {
                return coding.path("code").asText(null);
            }
        }
        return null;
    }
}
