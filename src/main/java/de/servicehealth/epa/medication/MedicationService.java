package de.servicehealth.epa.medication;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import de.servicehealth.epa.medication.model.DuplicateMatch;
import de.servicehealth.epa.medication.model.MedicationList;
import de.servicehealth.epa.medication.model.MedicationListEntry;
import de.servicehealth.epa.medication.model.MedicationPlan;
import de.servicehealth.epa.medication.model.MedicationPlanEntry;
import de.servicehealth.epa.medication.model.ReconciliationItem;
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
        return Optional.ofNullable(medicationCache.get(kvnr));
    }

    /**
     * Load medication plan (eMP) for a patient by KVNR.
     * Returns empty Optional if no data found.
     */
    public Optional<MedicationPlan> loadMedicationPlan(String kvnr) {
        return Optional.ofNullable(medicationPlanCache.get(kvnr));
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
        } catch (IOException e) {
            System.err.println("Failed to load medication list fixture: " + filename);
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
     * @return Updated MedicationPlan, or empty Optional if entry not found
     */
    public Optional<MedicationPlan> updateMedicationPlanEntry(String kvnr,
            String entryId, MedicationPlanEntry updatedEntry) {
        MedicationPlan plan = medicationPlanCache.get(kvnr);

        if (plan == null) {
            return Optional.empty();
        }

        // Find the entry to update
        MedicationPlanEntry existingEntry = null;
        for (MedicationPlanEntry entry : plan.getEntries()) {
            if (entry.getId().equals(entryId)) {
                existingEntry = entry;
                break;
            }
        }

        if (existingEntry == null) {
            return Optional.empty();
        }

        // Update modifiable fields (FR-009: dosage and intake instructions primarily)
        if (updatedEntry.getDosageStructured() != null) {
            existingEntry.setDosageStructured(updatedEntry.getDosageStructured());
        }
        if (updatedEntry.getDosageText() != null) {
            existingEntry.setDosageText(updatedEntry.getDosageText());
        }
        if (updatedEntry.getIntakeInstructions() != null) {
            existingEntry.setIntakeInstructions(updatedEntry.getIntakeInstructions());
        }
        if (updatedEntry.getIndication() != null) {
            existingEntry.setIndication(updatedEntry.getIndication());
        }
        if (updatedEntry.getNote() != null) {
            existingEntry.setNote(updatedEntry.getNote());
        }
        // Allow updating medication name, strength, and active ingredient
        if (updatedEntry.getMedicationName() != null) {
            existingEntry.setMedicationName(updatedEntry.getMedicationName());
        }
        if (updatedEntry.getStrength() != null) {
            existingEntry.setStrength(updatedEntry.getStrength());
        }
        if (updatedEntry.getActiveIngredient() != null) {
            existingEntry.setActiveIngredient(updatedEntry.getActiveIngredient());
        }

        // FR-008: Increment plan version on edit
        plan.setVersion(plan.getVersion() + 1);

        // FR-009: Update lastModified timestamp
        plan.setLastUpdated(java.time.Instant.now());

        return Optional.of(plan);
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
     * Get reconciliation items for a patient.
     * Compares eML (history) with eMP (current plan) to identify discrepancies.
     */
    public List<ReconciliationItem> getReconciliation(String kvnr) {
        MedicationList eml = medicationCache.get(kvnr);
        MedicationPlan emp = medicationPlanCache.get(kvnr);

        List<ReconciliationItem> items = new ArrayList<>();

        if (eml == null || eml.getEntries() == null) {
            return items;
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

        // Iterate through eML entries and find matches in eMP
        for (MedicationListEntry emlEntry : eml.getEntries()) {
            // Skip dispensements for now, or maybe include them? implementing for all eML
            // entries as per spec imply "medication history"
            // Let's include everything from eML.

            MedicationPlanEntry match = null;

            // Try PZN match
            if (emlEntry.getPzn() != null) {
                match = empPznMap.get(emlEntry.getPzn());
            }

            // Try ATC match if no PZN match
            if (match == null && emlEntry.getAtcCode() != null && emlEntry.getAtcCode().length() >= 5) {
                match = empAtcMap.get(emlEntry.getAtcCode().substring(0, 5));
            }

            items.add(new ReconciliationItem(emlEntry, match));
        }

        return items;
    }
}
