package de.servicehealth.epa.medication;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import de.servicehealth.epa.medication.model.DuplicateMatch;
import de.servicehealth.epa.medication.model.MedicationList;
import de.servicehealth.epa.medication.model.MedicationStatement;
import de.servicehealth.epa.medication.model.MedicationPlan;
import de.servicehealth.epa.medication.model.MedicationRequest;
import de.servicehealth.epa.medication.model.PrescriptionGroup;
import de.servicehealth.epa.medication.model.EMPChronologyProvenance;
import de.servicehealth.epa.medication.model.EPAActivityProvenance;
import de.servicehealth.epa.medication.model.ReconciliationStatus;
import jakarta.enterprise.context.ApplicationScoped;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;

import java.util.HashMap;
import java.util.Set;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for loading and managing medication lists.
 * In prototype, loads from JSON fixtures. In production, would interact with
 * ePA TI backend.
 */
@ApplicationScoped
public class MedicationService {

    private final ConcurrentHashMap<String, MedicationList> medicationCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, MedicationPlan> medicationPlanCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, EMPChronologyProvenance> chronologyCache = new ConcurrentHashMap<>(); // Current
                                                                                                                  // chronology
                                                                                                                  // state
                                                                                                                  // per
                                                                                                                  // KVNR
    private final List<EPAActivityProvenance> activityLog = new ArrayList<>(); // Audit log
    private final ObjectMapper objectMapper;

    public MedicationService() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        loadAllFixtures();
    }

    /**
     * Load medication list for a patient by KVNR.
     * Returns empty MedicationList if no data found.
     */
    public Optional<MedicationList> loadMedicationList(String kvnr) {
        MedicationList list = medicationCache.get(kvnr);
        MedicationPlan plan = medicationPlanCache.get(kvnr);

        if (list != null) {
            Map<String, String> pznMap = new HashMap<>();
            if (plan != null && plan.getEntries() != null) {
                for (MedicationRequest entry : plan.getEntries()) {
                    if (entry.getPzn() != null) {
                        pznMap.put(entry.getPzn(), entry.getId());
                    }
                }
            }
            populateLinkStatus(list.getEntries(), pznMap);
        }

        return Optional.ofNullable(list);
    }

    private void populateLinkStatus(List<MedicationStatement> entries, Map<String, String> pznMap) {
        if (entries == null)
            return;

        for (MedicationStatement entry : entries) {
            String empId = null;

            // 1. Check Hard Link (MedicationPlanIdentifier)
            if (entry.getMedicationPlanIdentifier() != null) {
                // Resolve Identifier to an eMP Entry ID.
                // Ideally this should use an Identifier->ID map, but for now we rely on the
                // basedOn
                // reference which is set by the link-emp operation.
                if (entry.getBasedOn() != null && entry.getBasedOn().startsWith("MedicationRequest/")) {
                    empId = entry.getBasedOn().substring("MedicationRequest/".length());
                }
            }

            // 2. Check implicit PZN link (Soft Match)
            // REMOVED: Soft Matches (PZN match without Identifier) are NOT "Linked" in the
            // eML view context.
            // They are only "Proposals" in the Reconciliation view.
            // This allows the user to explicit "Unlink" an item (remove Identifier), and it
            // will stay Unlinked.

            entry.setLinkedToPlanId(empId);

            // Check children (dispensations)
            populateLinkStatus(entry.getDispensations(), pznMap);
        }
    }

    /**
     * Helper to find an entry by ID across all cached medication lists.
     * Needed because link operations might not provide the correct KVNR.
     */
    private Optional<MedicationStatement> findEntry(String emlId) {
        for (MedicationList list : medicationCache.values()) {
            if (list.getEntries() != null) {
                for (MedicationStatement entry : list.getEntries()) {
                    if (entry.getId().equals(emlId)) {
                        return Optional.of(entry);
                    }
                    // Check dispensations
                    if (entry.getDispensations() != null) {
                        for (MedicationStatement disp : entry.getDispensations()) {
                            if (disp.getId().equals(emlId)) {
                                return Optional.of(disp);
                            }
                        }
                    }
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Load medication plan (eMP) for a patient by KVNR.
     * Returns empty Optional if no data found.
     */
    public Optional<MedicationPlan> loadMedicationPlan(String kvnr) {
        MedicationPlan plan = medicationPlanCache.get(kvnr);
        if (plan != null) {
            // Populate linkedEmlIds (manual + implicit)
            if (plan.getEntries() != null) {
                // Get eML for implicit check
                MedicationList eml = medicationCache.get(kvnr);

                for (MedicationRequest entry : plan.getEntries()) {
                    List<String> linkedEmls = new ArrayList<>();
                    String planEntryIdentifier = entry.getMedicationPlanIdentifier();

                    if (eml != null && eml.getEntries() != null) {
                        // We need to iterate all eml entries to find links
                        // This is expensive (O(N*M)) but fine for prototype
                        List<MedicationStatement> allEmlEntries = new ArrayList<>();
                        // Flatten the list (Prescriptions + Dispensations)
                        for (MedicationStatement p : eml.getEntries()) {
                            allEmlEntries.add(p);
                            if (p.getDispensations() != null)
                                allEmlEntries.addAll(p.getDispensations());
                        }

                        for (MedicationStatement emlEntry : allEmlEntries) {
                            boolean isLinked = false;

                            // 1. Hard Link (Identifier Match)
                            if (planEntryIdentifier != null
                                    && planEntryIdentifier.equals(emlEntry.getMedicationPlanIdentifier())) {
                                isLinked = true;
                            }

                            // 2. Soft Link (PZN Match) - Only if NOT checking for hard link elsewhere?
                            // Actually, if it's hard linked to *another* entry, we shouldn't link here.
                            // Logic: If emlEntry has NO identifier, AND PZN matches -> Soft Link
                            else if (emlEntry.getMedicationPlanIdentifier() == null &&
                                    entry.getPzn() != null &&
                                    entry.getPzn().equals(emlEntry.getPzn())) {
                                isLinked = true;
                            }

                            if (isLinked) {
                                // Avoid duplicates
                                if (!linkedEmls.contains(emlEntry.getId())) {
                                    linkedEmls.add(emlEntry.getId());
                                }
                            }
                        }
                    }

                    entry.setLinkedEmlIds(linkedEmls);
                }

                // Populate Chronology ID
                EMPChronologyProvenance chrono = getChronology(kvnr);
                if (chrono != null) {
                    plan.setChronologyId(chrono.getId());
                }
            }
        }
        return Optional.ofNullable(plan);
    }

    /**
     * Load all medication list and plan fixtures on startup
     */
    private void loadAllFixtures() {
        // eML fixtures
        loadFixture("X123456789");
        loadFixture("Y987654321");
        loadFixture("Z555111222");

        // eMP fixtures
        loadPlanFixture("X123456789");
        loadPlanFixture("Y987654321");
        loadPlanFixture("Z555111222");
    }

    /**
     * Load a single medication list fixture from FHIR Bundle JSON file.
     * Parses MedicationStatement (prescriptions) and MedicationDispense (dispensements)
     * and groups dispenses under their parent prescription via partOf reference.
     */
    private void loadFixture(String kvnr) {
        String filename = "/fixtures/medication-list-" + kvnr + ".json";
        try (InputStream is = getClass().getResourceAsStream(filename)) {
            if (is != null) {
                JsonNode bundle = objectMapper.readTree(is);
                MedicationList medicationList = parseFhirMedicationList(bundle, kvnr);
                medicationList.sortByAuthoredDateDesc();
                medicationCache.put(kvnr, medicationList);
                System.out.println("Loaded medication list for KVNR: " + kvnr +
                        " (" + medicationList.getEntryCount() + " entries)");
            } else {
                System.err.println("Medication list fixture not found: " + filename);
            }
        } catch (Exception e) {
            System.err.println("CRITICAL ERROR: Failed to load medication list fixture: " + filename);
            System.err.println("Exception message: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Load a single medication plan fixture from FHIR Bundle JSON file.
     * Parses MedicationRequest resources into the flat MedicationPlan model.
     */
    private void loadPlanFixture(String kvnr) {
        String filename = "/fixtures/medication-plan-" + kvnr + ".json";
        try (InputStream is = getClass().getResourceAsStream(filename)) {
            if (is != null) {
                JsonNode bundle = objectMapper.readTree(is);
                MedicationPlan medicationPlan = parseFhirMedicationPlan(bundle, kvnr);
                medicationPlanCache.put(kvnr, medicationPlan);
                System.out.println("Loaded medication plan for KVNR: " + kvnr +
                        " (" + medicationPlan.getEntryCount() + " entries)");
            } else {
                System.err.println("Medication plan fixture not found: " + filename);
            }
        } catch (IOException e) {
            System.err.println("Failed to load medication plan fixture: " + filename);
            e.printStackTrace();
        }
    }

    // -------------------------------------------------------------------------
    // FHIR Bundle → POJO mapping helpers
    // -------------------------------------------------------------------------

    private MedicationPlan parseFhirMedicationPlan(JsonNode bundle, String kvnr) {
        MedicationPlan plan = new MedicationPlan();
        plan.setKvnr(kvnr);
        plan.setVersion(1);

        JsonNode entries = bundle.path("entry");
        if (entries.isArray()) {
            for (JsonNode entry : entries) {
                JsonNode resource = entry.path("resource");
                if ("MedicationRequest".equals(resource.path("resourceType").asText())) {
                    plan.addEntry(fhirRequestToMedicationRequest(resource));
                }
            }
        }
        return plan;
    }

    private MedicationList parseFhirMedicationList(JsonNode bundle, String kvnr) {
        MedicationList list = new MedicationList(kvnr);

        // First pass: collect prescriptions (MedicationStatement) by id
        // and dispenses (MedicationDispense) keyed by their partOf reference id
        LinkedHashMap<String, MedicationStatement> prescriptions = new LinkedHashMap<>();
        LinkedHashMap<String, MedicationStatement> dispenses = new LinkedHashMap<>();

        JsonNode entries = bundle.path("entry");
        if (entries.isArray()) {
            for (JsonNode entry : entries) {
                JsonNode resource = entry.path("resource");
                String resourceType = resource.path("resourceType").asText();
                if ("MedicationStatement".equals(resourceType)) {
                    MedicationStatement stmt = fhirStatementToMedicationStatement(resource);
                    prescriptions.put(stmt.getId(), stmt);
                } else if ("MedicationDispense".equals(resourceType)) {
                    MedicationStatement disp = fhirDispenseToMedicationStatement(resource);
                    dispenses.put(disp.getId(), disp);
                }
            }
        }

        // Second pass: attach each dispense to its parent prescription via MedicationStatement.derivedFrom
        Set<String> attachedDispenseIds = new HashSet<>();
        if (entries.isArray()) {
            for (JsonNode entry : entries) {
                JsonNode resource = entry.path("resource");
                if ("MedicationStatement".equals(resource.path("resourceType").asText())) {
                    String stmtId = resource.path("id").asText();
                    MedicationStatement parent = prescriptions.get(stmtId);
                    if (parent == null) continue;

                    JsonNode derivedFromArr = resource.path("derivedFrom");
                    if (derivedFromArr.isArray()) {
                        for (JsonNode derivedFrom : derivedFromArr) {
                            String ref = derivedFrom.path("reference").asText();
                            // ref is "MedicationDispense/<id>"
                            String dispId = ref.contains("/") ? ref.substring(ref.lastIndexOf('/') + 1) : ref;
                            MedicationStatement disp = dispenses.get(dispId);
                            if (disp != null) {
                                parent.addDispensation(disp);
                                attachedDispenseIds.add(dispId);
                            }
                        }
                    }
                }
            }
        }

        // Add orphan dispenses (not referenced by any derivedFrom) at top level
        for (Map.Entry<String, MedicationStatement> e : dispenses.entrySet()) {
            if (!attachedDispenseIds.contains(e.getKey())) {
                list.getEntries().add(e.getValue());
            }
        }

        list.getEntries().addAll(prescriptions.values());
        return list;
    }

    private MedicationRequest fhirRequestToMedicationRequest(JsonNode r) {
        MedicationRequest req = new MedicationRequest();
        req.setId(r.path("id").asText(null));

        JsonNode med = r.path("medicationCodeableConcept");
        req.setMedicationName(med.path("text").asText(null));
        req.setPzn(extractCoding(med, "http://fhir.de/CodeSystem/ifa/pzn"));
        req.setAtcCode(extractCoding(med, "http://www.whocc.no/atc"));

        req.setStatus(mapFhirStatus(r.path("status").asText("active")));

        JsonNode dosageArr = r.path("dosageInstruction");
        if (dosageArr.isArray() && dosageArr.size() > 0) {
            JsonNode dosage = dosageArr.get(0);
            String text = dosage.path("text").asText(null);
            req.setDosageText(text);
            req.setDosageStructured(text);
            JsonNode addlArr = dosage.path("additionalInstruction");
            if (addlArr.isArray() && addlArr.size() > 0) {
                req.setIntakeInstructions(addlArr.get(0).path("text").asText(null));
            }
        }

        JsonNode reasonArr = r.path("reasonCode");
        if (reasonArr.isArray() && reasonArr.size() > 0) {
            req.setIndication(reasonArr.get(0).path("text").asText(null));
        }

        JsonNode noteArr = r.path("note");
        if (noteArr.isArray() && noteArr.size() > 0) {
            req.setNote(noteArr.get(0).path("text").asText(null));
        }

        String authoredOn = r.path("authoredOn").asText(null);
        if (authoredOn != null && !authoredOn.isEmpty()) {
            req.setAuthoredDate(Instant.parse(authoredOn));
        }

        req.setMedicationPlanIdentifier(extractIdentifier(r, "https://gematik.de/fhir/sid/emp-identifier"));

        JsonNode statusReason = r.path("statusReason");
        if (!statusReason.isMissingNode()) {
            req.setReasonForPause(statusReason.path("text").asText(null));
        }

        req.setEntrySource(extractExtensionCode(r,
                "https://epa-medication.fhir.gematik.de/StructureDefinition/epa-medication-entry-source-extension"));

        return req;
    }

    private MedicationStatement fhirStatementToMedicationStatement(JsonNode r) {
        MedicationStatement stmt = new MedicationStatement();
        stmt.setId(r.path("id").asText(null));
        stmt.setEntryType("prescription");

        JsonNode med = r.path("medicationCodeableConcept");
        stmt.setMedicationName(med.path("text").asText(null));
        stmt.setPzn(extractCoding(med, "http://fhir.de/CodeSystem/ifa/pzn"));
        stmt.setAtcCode(extractCoding(med, "http://www.whocc.no/atc"));

        String dateAsserted = r.path("dateAsserted").asText(null);
        if (dateAsserted != null && !dateAsserted.isEmpty()) {
            stmt.setAuthoredDate(LocalDateTime.ofInstant(Instant.parse(dateAsserted), ZoneOffset.UTC));
        }

        JsonNode infoSource = r.path("informationSource");
        if (!infoSource.isMissingNode()) {
            stmt.setPrescriberName(infoSource.path("display").asText(null));
        }

        stmt.setMedicationPlanIdentifier(extractIdentifier(r, "https://gematik.de/fhir/sid/emp-identifier"));

        JsonNode basedOnArr = r.path("basedOn");
        if (basedOnArr.isArray() && basedOnArr.size() > 0) {
            stmt.setBasedOn(basedOnArr.get(0).path("reference").asText(null));
        }

        return stmt;
    }

    private MedicationStatement fhirDispenseToMedicationStatement(JsonNode r) {
        MedicationStatement disp = new MedicationStatement();
        disp.setId(r.path("id").asText(null));
        disp.setEntryType("dispensement");

        JsonNode med = r.path("medicationCodeableConcept");
        disp.setMedicationName(med.path("text").asText(null));
        disp.setPzn(extractCoding(med, "http://fhir.de/CodeSystem/ifa/pzn"));
        disp.setAtcCode(extractCoding(med, "http://www.whocc.no/atc"));

        String whenHandedOver = r.path("whenHandedOver").asText(null);
        if (whenHandedOver != null && !whenHandedOver.isEmpty()) {
            disp.setAuthoredDate(LocalDateTime.ofInstant(Instant.parse(whenHandedOver), ZoneOffset.UTC));
        }

        JsonNode performerArr = r.path("performer");
        if (performerArr.isArray() && performerArr.size() > 0) {
            disp.setPharmacyName(performerArr.get(0).path("actor").path("display").asText(null));
        }

        JsonNode authPrescArr = r.path("authorizingPrescription");
        if (authPrescArr.isArray() && authPrescArr.size() > 0) {
            disp.setBasedOnReference(authPrescArr.get(0).path("reference").asText(null));
        }

        JsonNode subst = r.path("substitution");
        if (!subst.isMissingNode()) {
            disp.setSubstituted(subst.path("wasSubstituted").asBoolean(false));
        }

        return disp;
    }

    /** Extract the code for a given coding system from a medicationCodeableConcept node. */
    private String extractCoding(JsonNode concept, String system) {
        JsonNode codingArr = concept.path("coding");
        if (codingArr.isArray()) {
            for (JsonNode coding : codingArr) {
                if (system.equals(coding.path("system").asText())) {
                    return coding.path("code").asText(null);
                }
            }
        }
        return null;
    }

    /** Extract the value for a given identifier system from a FHIR resource node. */
    private String extractIdentifier(JsonNode resource, String system) {
        JsonNode idArr = resource.path("identifier");
        if (idArr.isArray()) {
            for (JsonNode id : idArr) {
                if (system.equals(id.path("system").asText())) {
                    return id.path("value").asText(null);
                }
            }
        }
        return null;
    }

    /** Extract valueCode from a named extension on a FHIR resource. */
    private String extractExtensionCode(JsonNode resource, String url) {
        JsonNode extArr = resource.path("extension");
        if (extArr.isArray()) {
            for (JsonNode ext : extArr) {
                if (url.equals(ext.path("url").asText())) {
                    return ext.path("valueCode").asText(null);
                }
            }
        }
        return null;
    }

    /**
     * Map FHIR MedicationRequest status to the internal application status.
     * FHIR: active | on-hold | cancelled | completed | entered-in-error | stopped | draft | unknown
     * App:  active | paused  | cancelled | active    | (ignored)         | paused  | planned | active
     */
    private String mapFhirStatus(String fhirStatus) {
        switch (fhirStatus) {
            case "on-hold":
            case "stopped":
                return "paused";
            case "draft":
                return "planned";
            case "cancelled":
                return "cancelled";
            default:
                return "active";
        }
    }

    /**
     * Get or initialize the current chronology for a patient.
     */
    public EMPChronologyProvenance getChronology(String kvnr) {
        return chronologyCache.computeIfAbsent(kvnr, this::createChronologySnapshot);
    }

    /**
     * Updates the chronology state by taking a snapshot of the current plan.
     * Returns the new provenance object.
     */
    public EMPChronologyProvenance updateChronology(String kvnr) {
        EMPChronologyProvenance prov = createChronologySnapshot(kvnr);
        chronologyCache.put(kvnr, prov);
        return prov;
    }

    private EMPChronologyProvenance createChronologySnapshot(String kvnr) {
        MedicationPlan plan = medicationPlanCache.get(kvnr);
        List<MedicationRequest> snapshot = new ArrayList<>();
        if (plan != null && plan.getEntries() != null) {
            // Create deep copy of entries list to freeze state
            snapshot.addAll(plan.getEntries());
        }
        return new EMPChronologyProvenance(java.util.UUID.randomUUID().toString(), snapshot);
    }

    /**
     * Check if the provided chronology ID matches the current one.
     * Throws exception if mismatch.
     */
    public void checkChronologyId(String kvnr, String acknowledgedId) {
        EMPChronologyProvenance current = getChronology(kvnr);
        if (current != null && !current.getId().equals(acknowledgedId)) {
            throw new IllegalArgumentException("MEDSVC_EMP_CHRONOLOGY_ID_MISMATCH");
        }
    }

    /**
     * log activity
     */
    public void logActivity(String agent, String reference) {
        EPAActivityProvenance prov = new EPAActivityProvenance(java.util.UUID.randomUUID().toString(), agent,
                reference);
        activityLog.add(prov);
        System.out.println("AUDIT: " + agent + " modified " + reference);
    }

    /**
     * Add a new entry to the patient's medication plan.
     * If plan doesn't exist, one is created.
     */
    public MedicationPlan addMedicationRequest(String kvnr,
            MedicationRequest entry) {
        MedicationPlan plan = medicationPlanCache.computeIfAbsent(kvnr, k -> {
            MedicationPlan newPlan = new MedicationPlan();
            newPlan.setKvnr(k);
            newPlan.setVersion(1);
            return newPlan;
        });

        // Ensure entry has an ID if missing
        if (entry.getId() == null || entry.getId().isEmpty()) {
            entry.setId("emp-" + kvnr.toLowerCase() + "-" + (plan.getEntryCount() + 100)); // Simple ID generation
        }

        // Default status to active if missing
        if (entry.getStatus() == null || entry.getStatus().isEmpty()) {
            entry.setStatus("active");
        }

        // Set missing fields
        if (entry.getAuthoredDate() == null) {
            entry.setAuthoredDate(java.time.Instant.now());
        }

        plan.getEntries().add(entry);
        plan.setLastUpdated(java.time.Instant.now());
        plan.setVersion(plan.getVersion() + 1);

        return plan;
    }

    /**
     * Update an existing entry in the patient's medication plan.
     * Increments plan version and updates lastModified timestamp.
     *
     * @param kvnr         Patient KVNR
     * @param entryId      ID of the entry to update
     * @param updatedEntry Entry with updated field values
     */
    public Optional<MedicationPlan> updateMedicationRequest(String kvnr, String entryId,
            MedicationRequest updatedEntry) {
        MedicationPlan plan = medicationPlanCache.get(kvnr);
        if (plan == null) {
            return Optional.empty();
        }

        for (int i = 0; i < plan.getEntries().size(); i++) {
            MedicationRequest entry = plan.getEntries().get(i);
            if (entry.getId().equals(entryId)) {
                // Update fields
                entry.setMedicationName(updatedEntry.getMedicationName());
                entry.setPzn(updatedEntry.getPzn());
                entry.setStrength(updatedEntry.getStrength());
                entry.setActiveIngredient(updatedEntry.getActiveIngredient());
                entry.setDosageStructured(updatedEntry.getDosageStructured());
                entry.setDosageText(updatedEntry.getDosageText());
                entry.setIntakeInstructions(updatedEntry.getIntakeInstructions());
                entry.setIndication(updatedEntry.getIndication());
                entry.setNote(updatedEntry.getNote());

                // Update metadata
                plan.setVersion(plan.getVersion() + 1);
                plan.setLastUpdated(java.time.Instant.now());

                return Optional.of(plan);
            }
        }

        return Optional.empty();
    }

    /**
     * Change status of a medication entry (e.g., active -> paused).
     */
    public Optional<MedicationPlan> changeMedicationStatus(String kvnr, String entryId, String newStatus) {
        MedicationPlan plan = medicationPlanCache.get(kvnr);
        if (plan == null) {
            return Optional.empty();
        }

        for (MedicationRequest entry : plan.getEntries()) {
            if (entry.getId().equals(entryId)) {
                entry.setStatus(newStatus);

                // Update metadata
                plan.setVersion(plan.getVersion() + 1);
                plan.setLastUpdated(java.time.Instant.now());

                return Optional.of(plan);
            }
        }

        return Optional.empty();
    }

    /**
     * Delete a medication entry from the plan.
     */
    public boolean deleteMedicationEntry(String kvnr, String entryId) {
        MedicationPlan plan = medicationPlanCache.get(kvnr);
        if (plan == null) {
            return false;
        }

        boolean removed = plan.getEntries().removeIf(entry -> entry.getId().equals(entryId));

        if (removed) {
            plan.setVersion(plan.getVersion() + 1);
            plan.setLastUpdated(java.time.Instant.now());
        }

        return removed;
    }

    /**
     * Check for duplicates in the medication plan.
     */
    public DuplicateMatch checkForDuplicates(String kvnr,
            MedicationRequest newEntry) {
        MedicationPlan plan = medicationPlanCache.get(kvnr);
        if (plan == null || plan.getEntries().isEmpty()) {
            return new DuplicateMatch(false, null, null);
        }

        for (MedicationRequest existing : plan.getEntries()) {
            // Check PZN match
            if (newEntry.getPzn() != null && newEntry.getPzn().equals(existing.getPzn())) {
                return new DuplicateMatch(true, "PZN", existing);
            }

            // Check ATC match (first 5 chars)
            if (newEntry.getAtcCode() != null && existing.getAtcCode() != null) {
                String newAtc = newEntry.getAtcCode().length() > 5 ? newEntry.getAtcCode().substring(0, 5)
                        : newEntry.getAtcCode();
                String existingAtc = existing.getAtcCode().length() > 5 ? existing.getAtcCode().substring(0, 5)
                        : existing.getAtcCode();
                if (newAtc.equals(existingAtc)) {
                    return new DuplicateMatch(true, "ATC", existing);
                }
            }

            // Check ASK match
            if (newEntry.getAskCode() != null && newEntry.getAskCode().equals(existing.getAskCode())) {
                return new DuplicateMatch(true, "ASK", existing);
            }
        }

        return new DuplicateMatch(false, null, null);
    }

    /**
     * Get reconciliation tree for a patient (dgMP compliant).
     * Compares eML (prescriptions/dispensations) with eMP (current plan).
     */
    public List<PrescriptionGroup> getReconciliationTree(String kvnr) {
        MedicationList eml = medicationCache.get(kvnr);
        MedicationPlan emp = medicationPlanCache.get(kvnr);

        List<PrescriptionGroup> groups = new ArrayList<>();

        if (eml == null || eml.getEntries() == null) {
            return groups;
        }

        // Map eMP entries for faster lookup
        Map<String, MedicationRequest> empPznMap = new HashMap<>();
        Map<String, MedicationRequest> empAtcMap = new HashMap<>();

        if (emp != null && emp.getEntries() != null) {
            for (MedicationRequest entry : emp.getEntries()) {
                if (entry.getPzn() != null) {
                    empPznMap.put(entry.getPzn(), entry);
                }
                if (entry.getAtcCode() != null && entry.getAtcCode().length() >= 7) {
                    empAtcMap.put(entry.getAtcCode().substring(0, 7), entry);
                }
            }
        }

        // Iterate through Prescriptions (Root Level)
        for (MedicationStatement prescription : eml.getEntries()) {
            // Only create groups for prescriptions
            if (prescription.isPrescription()) {
                PrescriptionGroup group = new PrescriptionGroup(prescription);

                // Add dispensations
                if (prescription.getDispensations() != null) {
                    group.setDispensations(prescription.getDispensations());
                }

                // Determine Status
                ReconciliationStatus status = ReconciliationStatus.ORPHAN_NEW;
                String matchReason = "No matching medication in plan";
                String empRef = null;

                // 1. Check Hard Link (MedicationPlanIdentifier)
                // This is the primary source of truth now.
                String linkedPlanId = prescription.getMedicationPlanIdentifier();

                // If not on prescription, check if any dispensation is linked (Cascading logic)
                if (linkedPlanId == null && prescription.getDispensations() != null) {
                    for (MedicationStatement disp : prescription.getDispensations()) {
                        if (disp.getMedicationPlanIdentifier() != null) {
                            linkedPlanId = disp.getMedicationPlanIdentifier();
                            break;
                        }
                    }
                }

                // If we found a link identifier...
                if (linkedPlanId != null) {
                    // Find the Plan Entry with this Identifier (UUID matches
                    // MedicationPlanIdentifier field in Plan Entry?)
                    // OR is the stored ID the Entry ID (basedOn)?
                    // The design says:
                    // emlEntry.setMedicationPlanIdentifier(empEntry.getMedicationPlanIdentifier())
                    // But wait, linkMap stored the Entry ID.
                    // Let's check how we link:
                    // emlEntry.setMedicationPlanIdentifier(empEntry.getMedicationPlanIdentifier());
                    // emlEntry.setBasedOn("MedicationRequest/" + empId);

                    // So we need to match against eMP entries' medicationPlanIdentifier OR their ID
                    // if basedOn is used?
                    // The requirement says: Hard Link exists if they have same Identifier.

                    String targetIdentifier = linkedPlanId; // This is the UUID
                    String targetEmpId = null;

                    if (emp != null) {
                        for (MedicationRequest e : emp.getEntries()) {
                            // Check match by Identifier (UUID)
                            if (targetIdentifier.equals(e.getMedicationPlanIdentifier())) {
                                targetEmpId = e.getId();
                                break;
                            }
                            // Fallback: Check match by Entry ID (Legacy / basedOn logic)
                            // linkMap stored Entry ID.
                            if (targetIdentifier.equals(e.getId())) {
                                targetEmpId = e.getId();
                                break;
                            }
                        }
                    }

                    if (targetEmpId != null) {
                        status = ReconciliationStatus.LINKED;
                        empRef = targetEmpId;
                        matchReason = "Linked to plan entry";
                    }
                }

                // 2. Check basedOn / technical link (PZN Match)
                // Only if NOT already linked
                if (status == ReconciliationStatus.ORPHAN_NEW) {
                    String pznMatchId = null;

                    if (prescription.getPzn() != null && empPznMap.containsKey(prescription.getPzn())) {
                        pznMatchId = empPznMap.get(prescription.getPzn()).getId();
                    } else if (prescription.getDispensations() != null) {
                        for (MedicationStatement disp : prescription.getDispensations()) {
                            if (disp.getPzn() != null && empPznMap.containsKey(disp.getPzn())) {
                                pznMatchId = empPznMap.get(disp.getPzn()).getId();
                                break;
                            }
                        }
                    }

                    if (pznMatchId != null) {
                        status = ReconciliationStatus.PROPOSAL_MATCH;
                        empRef = pznMatchId;
                        matchReason = "Same product (PZN match)";
                    }
                }

                // 3. Check for UNLINKED_UPDATE (Fall B) - Same Active Ingredient
                if (status == ReconciliationStatus.ORPHAN_NEW) {
                    if (prescription.getAtcCode() != null && prescription.getAtcCode().length() >= 7) {
                        String atcGroup = prescription.getAtcCode().substring(0, 7);
                        if (empAtcMap.containsKey(atcGroup)) {
                            status = ReconciliationStatus.UNLINKED_UPDATE;
                            matchReason = "Active ingredient found in plan (" + atcGroup + ")";
                            // We don't set empRef here because user needs to choose which one to link to
                        }
                    }
                }

                group.setStatus(status);
                group.setEmpReference(empRef);
                group.setMatchReason(matchReason);

                groups.add(group);
            }
        }

        // Sort: ORPHAN > UNLINKED > LINKED
        groups.sort((g1, g2) -> {
            int s1 = getStatusPriority(g1.getStatus());
            int s2 = getStatusPriority(g2.getStatus());
            if (s1 != s2)
                return Integer.compare(s1, s2);
            // Secondary sort by date
            if (g1.getPrescription().getAuthoredDate() == null)
                return 1;
            if (g2.getPrescription().getAuthoredDate() == null)
                return -1;
            return g2.getPrescription().getAuthoredDate().compareTo(g1.getPrescription().getAuthoredDate());
        });

        return groups;
    }

    private int getStatusPriority(ReconciliationStatus status) {
        switch (status) {
            case ORPHAN_NEW:
                return 1;
            case PROPOSAL_MATCH:
                return 2;
            case UNLINKED_UPDATE:
                return 3;
            case LINKED:
                return 4;
            case CANCELLED:
                return 4;
            default:
                return 5;
        }
    }

    public void linkMedicationPlanEntry(String kvnr, String emlId, String empId, String agent) {
        validateOrganizationHeader(agent);

        MedicationPlan plan = medicationPlanCache.get(kvnr);
        if (plan == null)
            throw new IllegalArgumentException("Plan not found");

        MedicationRequest empEntry = plan.getEntries().stream()
                .filter(e -> e.getId().equals(empId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("eMP Entry not found"));

        MedicationStatement emlEntry = findEntry(emlId)
                .orElseThrow(() -> new IllegalArgumentException("eML Entry not found"));

        // Ensure Identifier exists on eMP entry
        if (empEntry.getMedicationPlanIdentifier() == null) {
            empEntry.setMedicationPlanIdentifier(java.util.UUID.randomUUID().toString());
        }

        // Set Hard Link on eML entry
        emlEntry.setMedicationPlanIdentifier(empEntry.getMedicationPlanIdentifier());
        emlEntry.setBasedOn("MedicationRequest/" + empId);

        // Audit and Chronology
        updateChronology(kvnr);
        logActivity(agent, "Linked eML/" + emlId + " to eMP/" + empId);
    }

    public void unlinkMedicationPlanEntry(String kvnr, String emlId, String agent) {
        validateOrganizationHeader(agent);

        MedicationStatement emlEntry = findEntry(emlId)
                .orElseThrow(() -> new IllegalArgumentException("eML Entry not found"));

        // Remove Hard Link
        emlEntry.setMedicationPlanIdentifier(null);
        emlEntry.setBasedOn(null);

        // Audit and Chronology
        updateChronology(kvnr);
        logActivity(agent, "Unlinked eML/" + emlId);
    }

    public MedicationRequest addMedicationRequest(String kvnr, MedicationRequest entry, String linkedEmlId,
            String agent) {
        validateOrganizationHeader(agent);
        validateDosage(entry);

        entry.setMedicationPlanIdentifier(java.util.UUID.randomUUID().toString());

        addMedicationRequest(kvnr, entry); // Delegate to existing add logic for list management

        if (linkedEmlId != null) {
            MedicationStatement emlEntry = findEntry(linkedEmlId).orElse(null);
            if (emlEntry != null) {
                emlEntry.setMedicationPlanIdentifier(entry.getMedicationPlanIdentifier());
                emlEntry.setBasedOn("MedicationRequest/" + entry.getId());
            }
        }

        updateChronology(kvnr);
        logActivity(agent, "Added eMP/" + entry.getId());
        return entry;
    }

    public MedicationPlan updateMedicationRequest(String kvnr, String entryId, MedicationRequest entry,
            String linkedEmlId,
            String acknowledgedChronologyId, String agent) {
        validateOrganizationHeader(agent);
        checkChronologyId(kvnr, acknowledgedChronologyId);
        validateDosage(entry);

        // Update logic
        MedicationPlan plan = medicationPlanCache.get(kvnr);
        if (plan != null) {
            MedicationRequest existing = plan.getEntries().stream()
                    .filter(e -> e.getId().equals(entryId))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Entry not found"));

            // Update fields
            existing.setMedicationName(entry.getMedicationName());
            existing.setPzn(entry.getPzn());
            existing.setDosageText(entry.getDosageText());
            existing.setDosageStructured(entry.getDosageStructured());
            existing.setNote(entry.getNote());
            // ... other fields
            existing.setEntryType(entry.getEntryType());
            existing.setStrength(entry.getStrength());
            existing.setActiveIngredient(entry.getActiveIngredient());
            existing.setAtcCode(entry.getAtcCode());
            existing.setIntakeInstructions(entry.getIntakeInstructions());
            existing.setIndication(entry.getIndication());

            if (linkedEmlId != null) {
                MedicationStatement emlEntry = findEntry(linkedEmlId).orElse(null);
                if (emlEntry != null) {
                    emlEntry.setMedicationPlanIdentifier(existing.getMedicationPlanIdentifier());
                    emlEntry.setBasedOn("MedicationRequest/" + existing.getId());
                }
            }

            updateChronology(kvnr);
            logActivity(agent, "Updated eMP/" + entryId);

            // Set Chronology ID on plan before returning
            EMPChronologyProvenance chrono = getChronology(kvnr);
            if (chrono != null) {
                plan.setChronologyId(chrono.getId());
            }

            return plan;
        }
        throw new IllegalArgumentException("Plan not found");
    }

    private void validateOrganizationHeader(String agent) {
        if (agent == null || agent.trim().isEmpty()) {
            throw new IllegalArgumentException("SVC_ORG_HEADER_PROFILE_MISMATCH");
        }
        // In prototype, we accept any non-empty string.
        // In real impl, check profile.
    }

    private void validateDosage(MedicationRequest entry) {
        if (entry.getDosageStructured() != null) {
            // Strict check: generated text MUST match dosageText
            // For prototype: we assume simple generation logic: text == structured
            if (entry.getDosageText() != null && !entry.getDosageText().equals(entry.getDosageStructured())) {
                throw new IllegalArgumentException("MEDSVC_DOSAGE_INVALID");
            }
        }
    }
}
