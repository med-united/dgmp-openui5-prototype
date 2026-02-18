package de.servicehealth.epa.medication;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import de.servicehealth.epa.medication.model.DuplicateMatch;
import de.servicehealth.epa.medication.model.MedicationList;
import de.servicehealth.epa.medication.model.MedicationListEntry;
import de.servicehealth.epa.medication.model.MedicationPlan;
import de.servicehealth.epa.medication.model.MedicationPlanEntry;
import de.servicehealth.epa.medication.model.PrescriptionGroup;
import de.servicehealth.epa.medication.model.ReconciliationStatus;
import jakarta.enterprise.context.ApplicationScoped;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

import java.util.HashMap;
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
    private final ObjectMapper objectMapper;

    // In-memory storage for links between eML and eMP entries
    // Key: emlId, Value: empId
    private final ConcurrentHashMap<String, String> linkMap = new ConcurrentHashMap<>();

    // Set of eML IDs that have been explicitly unlinked (even if PZN matches)
    private final ConcurrentHashMap.KeySetView<String, Boolean> unlinkedSet = ConcurrentHashMap.newKeySet();

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
                for (MedicationPlanEntry entry : plan.getEntries()) {
                    if (entry.getPzn() != null) {
                        pznMap.put(entry.getPzn(), entry.getId());
                    }
                }
            }
            populateLinkStatus(list.getEntries(), pznMap);
        }

        return Optional.ofNullable(list);
    }

    private void populateLinkStatus(List<MedicationListEntry> entries, Map<String, String> pznMap) {
        if (entries == null)
            return;

        for (MedicationListEntry entry : entries) {
            // 1. Check manual link (priority)
            String empId = linkMap.get(entry.getId());

            // 2. Check implicit PZN link
            if (empId == null && entry.getPzn() != null && !unlinkedSet.contains(entry.getId())) {
                empId = pznMap.get(entry.getPzn());
            }

            entry.setLinkedToPlanId(empId);

            // Check children (dispensations)
            populateLinkStatus(entry.getDispensations(), pznMap);
        }
    }

    /**
     * Create a link between an eML entry and an eMP entry.
     * Cascades to dispensations if the eML entry is a prescription.
     */
    public void createLink(String kvnr, String emlId, String empId) {
        // Link the target entry
        linkMap.put(emlId, empId);
        unlinkedSet.remove(emlId);
        System.out.println("Linked eML entry " + emlId + " to eMP entry " + empId);

        // Check if it's a prescription with dispensations and link them too
        Optional<MedicationListEntry> entryOpt = findEntry(emlId);
        if (entryOpt.isPresent()) {
            MedicationListEntry entry = entryOpt.get();
            if (entry.getDispensations() != null) {
                for (MedicationListEntry disp : entry.getDispensations()) {
                    linkMap.put(disp.getId(), empId);
                    unlinkedSet.remove(disp.getId());
                    System.out.println("  -> Cascaded link to dispensation " + disp.getId());
                }
            }
        }
    }

    /**
     * Remove a link for an eML entry.
     * Cascades to dispensations if the eML entry is a prescription.
     */
    public boolean removeLink(String kvnr, String emlId) {
        boolean removed = linkMap.remove(emlId) != null;

        // Add to unlinkedSet to prevent implicit relinking
        unlinkedSet.add(emlId);
        System.out.println("Unlinked eML entry " + emlId);

        // Check if it's a prescription with dispensations and unlink them too
        Optional<MedicationListEntry> entryOpt = findEntry(emlId);
        if (entryOpt.isPresent()) {
            MedicationListEntry entry = entryOpt.get();
            if (entry.getDispensations() != null) {
                for (MedicationListEntry disp : entry.getDispensations()) {
                    linkMap.remove(disp.getId());
                    unlinkedSet.add(disp.getId());
                    System.out.println("  -> Cascaded unlink to dispensation " + disp.getId());
                }
            }
        }

        return true; // Always return true as we might be unlinking an implicit link
    }

    /**
     * Helper to find an entry by ID across all cached medication lists.
     * Needed because link operations might not provide the correct KVNR.
     */
    private Optional<MedicationListEntry> findEntry(String emlId) {
        for (MedicationList list : medicationCache.values()) {
            if (list.getEntries() != null) {
                for (MedicationListEntry entry : list.getEntries()) {
                    if (entry.getId().equals(emlId)) {
                        return Optional.of(entry);
                    }
                    // Check dispensations
                    if (entry.getDispensations() != null) {
                        for (MedicationListEntry disp : entry.getDispensations()) {
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

                for (MedicationPlanEntry entry : plan.getEntries()) {
                    List<String> linkedEmls = new ArrayList<>();

                    // 1. Manual links
                    for (Map.Entry<String, String> link : linkMap.entrySet()) {
                        if (link.getValue().equals(entry.getId())) {
                            linkedEmls.add(link.getKey());
                        }
                    }

                    // 2. Implicit PZN links
                    if (eml != null && eml.getEntries() != null && entry.getPzn() != null) {
                        for (MedicationListEntry emlEntry : eml.getEntries()) {
                            // Check if PZN matches AND not already manually linked AND not explicitly
                            // unlinked
                            if (entry.getPzn().equals(emlEntry.getPzn()) && !unlinkedSet.contains(emlEntry.getId())) {
                                // Avoid duplicates if already manually linked
                                if (!linkedEmls.contains(emlEntry.getId())) {
                                    linkedEmls.add(emlEntry.getId());
                                }
                            }
                        }
                    }

                    entry.setLinkedEmlIds(linkedEmls);
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
     * Load a single medication list fixture from JSON file
     */
    private void loadFixture(String kvnr) {
        String filename = "/fixtures/medication-list-" + kvnr + ".json";
        try (InputStream is = getClass().getResourceAsStream(filename)) {
            if (is != null) {
                MedicationList medicationList = objectMapper.readValue(is, MedicationList.class);
                // Sort by authored date descending (newest first)
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
     * Load a single medication plan fixture from JSON file
     */
    private void loadPlanFixture(String kvnr) {
        String filename = "/fixtures/medication-plan-" + kvnr + ".json";
        try (InputStream is = getClass().getResourceAsStream(filename)) {
            if (is != null) {
                MedicationPlan medicationPlan = objectMapper.readValue(is,
                        MedicationPlan.class);
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

    /**
     * Add a new entry to the patient's medication plan.
     * If plan doesn't exist, one is created.
     */
    public MedicationPlan addMedicationPlanEntry(String kvnr,
            MedicationPlanEntry entry) {
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
    public Optional<MedicationPlan> updateMedicationPlanEntry(String kvnr, String entryId,
            MedicationPlanEntry updatedEntry) {
        MedicationPlan plan = medicationPlanCache.get(kvnr);
        if (plan == null) {
            return Optional.empty();
        }

        for (int i = 0; i < plan.getEntries().size(); i++) {
            MedicationPlanEntry entry = plan.getEntries().get(i);
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

        for (MedicationPlanEntry entry : plan.getEntries()) {
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
            MedicationPlanEntry newEntry) {
        MedicationPlan plan = medicationPlanCache.get(kvnr);
        if (plan == null || plan.getEntries().isEmpty()) {
            return new DuplicateMatch(false, null, null);
        }

        for (MedicationPlanEntry existing : plan.getEntries()) {
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
        Map<String, MedicationPlanEntry> empPznMap = new HashMap<>();
        Map<String, MedicationPlanEntry> empAtcMap = new HashMap<>();

        if (emp != null && emp.getEntries() != null) {
            for (MedicationPlanEntry entry : emp.getEntries()) {
                if (entry.getPzn() != null) {
                    empPznMap.put(entry.getPzn(), entry);
                }
                if (entry.getAtcCode() != null && entry.getAtcCode().length() >= 5) {
                    empAtcMap.put(entry.getAtcCode().substring(0, 5), entry);
                }
            }
        }

        // Iterate through Prescriptions (Root Level)
        for (MedicationListEntry prescription : eml.getEntries()) {
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

                // 1. Check manual link (priority)
                String manuallyLinkedEmpId = linkMap.get(prescription.getId());

                // If not found on prescription, check dispensations
                if (manuallyLinkedEmpId == null && prescription.getDispensations() != null) {
                    for (MedicationListEntry disp : prescription.getDispensations()) {
                        String dispLinkedId = linkMap.get(disp.getId());
                        if (dispLinkedId != null) {
                            manuallyLinkedEmpId = dispLinkedId;
                            break; // Found one
                        }
                    }
                }

                if (manuallyLinkedEmpId != null && emp != null) {
                    // Verify entry still exists
                    boolean exists = false;
                    for (MedicationPlanEntry e : emp.getEntries()) {
                        if (e.getId().equals(manuallyLinkedEmpId)) {
                            exists = true;
                            break;
                        }
                    }

                    if (exists) {
                        status = ReconciliationStatus.LINKED;
                        empRef = manuallyLinkedEmpId;
                        matchReason = "Manually linked to plan entry";
                    }
                }

                // 2. Check basedOn / technical link
                // For prototype, we treat PZN match as implicit technical link (Fall C) if not
                // manually linked AND not explicitly unlinked.
                // We check prescription PZN and dispensation PZNs
                if (status == ReconciliationStatus.ORPHAN_NEW) {
                    String pznMatchId = null;

                    if (prescription.getPzn() != null && empPznMap.containsKey(prescription.getPzn())
                            && !unlinkedSet.contains(prescription.getId())) {
                        pznMatchId = empPznMap.get(prescription.getPzn()).getId();
                    } else if (prescription.getDispensations() != null) {
                        for (MedicationListEntry disp : prescription.getDispensations()) {
                            if (disp.getPzn() != null && empPznMap.containsKey(disp.getPzn())
                                    && !unlinkedSet.contains(disp.getId())) {
                                pznMatchId = empPznMap.get(disp.getPzn()).getId();
                                break;
                            }
                        }
                    }

                    if (pznMatchId != null) {
                        status = ReconciliationStatus.LINKED;
                        empRef = pznMatchId;
                        matchReason = "Same product (PZN match)";
                    }
                }

                // 3. Check for UNLINKED_UPDATE (Fall B) - Same Active Ingredient
                if (status == ReconciliationStatus.ORPHAN_NEW) {
                    if (prescription.getAtcCode() != null && prescription.getAtcCode().length() >= 5) {
                        String atcGroup = prescription.getAtcCode().substring(0, 5);
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
            case UNLINKED_UPDATE:
                return 2;
            case LINKED:
                return 3;
            case CANCELLED:
                return 4;
            default:
                return 5;
        }
    }
}
