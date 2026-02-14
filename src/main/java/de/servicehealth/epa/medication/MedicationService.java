package de.servicehealth.epa.medication;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import de.servicehealth.epa.medication.model.MedicationList;
import jakarta.enterprise.context.ApplicationScoped;

import java.io.IOException;
import java.io.InputStream;
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
     * Load all medication list fixtures on startup
     */
    private void loadAllFixtures() {
        loadFixture("X123456789");
        loadFixture("Y987654321");
        loadFixture("Z555111222");
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
}
