package de.servicehealth.epa.patient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import de.servicehealth.epa.patient.model.Patient;
import jakarta.enterprise.context.ApplicationScoped;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Service for patient operations
 * In prototype: loads from JSON fixtures
 * In production: would integrate with TI backend
 */
@ApplicationScoped
public class PatientService {

    private static final Logger LOG = Logger.getLogger(PatientService.class.getName());
    private static final String PATIENTS_FIXTURE = "fixtures/patients.json";

    private final ConcurrentHashMap<String, Patient> patientCache = new ConcurrentHashMap<>();
    private final List<String> recentPatients = new ArrayList<>();
    private final ObjectMapper objectMapper;

    public PatientService() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        loadFixtures();
    }

    private void loadFixtures() {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(PATIENTS_FIXTURE)) {
            if (is == null) {
                LOG.warning("Patients fixture not found: " + PATIENTS_FIXTURE);
                return;
            }

            JsonNode bundle = objectMapper.readTree(is);
            List<Patient> patients = parseFhirPatientBundle(bundle);
            patients.forEach(p -> patientCache.put(p.getKvnr(), p));
            LOG.info("Loaded " + patients.size() + " patients from fixtures");

            // Pre-load first 2 patients as recent for convenience
            if (patients.size() >= 2) {
                // Add in reverse order so the first one ends up at the index 0 (most recent)
                addToRecentPatients(patients.get(1).getKvnr());
                addToRecentPatients(patients.get(0).getKvnr());
            } else if (!patients.isEmpty()) {
                addToRecentPatients(patients.get(0).getKvnr());
            }

        } catch (IOException e) {
            LOG.severe("Failed to load patient fixtures: " + e.getMessage());
        }
    }

    private List<Patient> parseFhirPatientBundle(JsonNode bundle) {
        List<Patient> patients = new ArrayList<>();
        JsonNode entries = bundle.path("entry");
        if (entries.isMissingNode()) return patients;

        for (JsonNode entry : entries) {
            JsonNode resource = entry.path("resource");
            if (!"Patient".equals(resource.path("resourceType").asText())) continue;

            String kvnr = null;
            String insuranceType = null;
            for (JsonNode identifier : resource.path("identifier")) {
                String system = identifier.path("system").asText("");
                if (system.contains("gkv/kvid-10")) {
                    kvnr = identifier.path("value").asText(null);
                    insuranceType = "GKV";
                } else if (system.contains("pkv/kvid-10")) {
                    kvnr = identifier.path("value").asText(null);
                    insuranceType = "PKV";
                }
            }
            if (kvnr == null) continue;

            JsonNode nameNode = resource.path("name");
            String firstName = "";
            String lastName = "";
            if (!nameNode.isMissingNode() && nameNode.size() > 0) {
                JsonNode name = nameNode.get(0);
                lastName = name.path("family").asText("");
                JsonNode given = name.path("given");
                if (!given.isMissingNode() && given.size() > 0) {
                    firstName = given.get(0).asText("");
                }
            }

            String birthDateStr = resource.path("birthDate").asText(null);
            LocalDate dateOfBirth = birthDateStr != null ? LocalDate.parse(birthDateStr) : null;

            patients.add(new Patient(kvnr, firstName, lastName, dateOfBirth, insuranceType));
        }
        return patients;
    }

    public Optional<Patient> findByKvnr(String kvnr) {
        Patient patient = patientCache.get(kvnr);
        if (patient != null) {
            addToRecentPatients(kvnr);
        }
        return Optional.ofNullable(patient);
    }

    public List<Patient> getRecentPatients() {
        return recentPatients.stream()
                .limit(10)
                .map(patientCache::get)
                .filter(p -> p != null)
                .toList();
    }

    private void addToRecentPatients(String kvnr) {
        recentPatients.remove(kvnr); // Remove if exists
        recentPatients.add(0, kvnr); // Add to front

        // Keep max 10
        if (recentPatients.size() > 10) {
            recentPatients.remove(recentPatients.size() - 1);
        }
    }
}
