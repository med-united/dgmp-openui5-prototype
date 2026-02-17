package de.servicehealth.epa.medication;

import de.servicehealth.epa.medication.model.MedicationPlanEntry;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * In-memory database of common medications for prototype purposes.
 * Provides search functionality by PZN or Name.
 */
@ApplicationScoped
public class MedicationDatabase {

    private final List<MedicationPlanEntry> database = new ArrayList<>();

    public MedicationDatabase() {
        initDatabase();
    }

    private void initDatabase() {
        // Ibuprofen
        add("00206123", "Ibuprofen 400mg ADGC", "Ibuprofen", "400mg", "1-0-1-0", "Pain/Fever");
        add("02353842", "Ibuprofen Ratiopharm 400mg", "Ibuprofen", "400mg", "1-0-1-0", "Pain");
        add("00185342", "Ibflam 600mg Lichtenstein", "Ibuprofen", "600mg", "1-0-1-0", "Rheumatoid Arthritis");

        // Aspirin / ASA
        add("00037833", "Aspirin 500mg überzogene Tabletten", "Acetylsalicylsäure", "500mg", "1-0-0-0",
                "Pain/Headache");
        add("02568222", "ASS-XA 100mg TAH", "Acetylsalicylsäure", "100mg", "0-1-0-0", "Blood thinning");

        // Paracetamol
        add("01318813", "Paracetamol-Ratiopharm 500 MG", "Paracetamol", "500mg", "Bei Bedarf", "Pain/Fever");
        add("03253765", "Ben-u-ron 500mg Tabletten", "Paracetamol", "500mg", "1-1-1-1", "Fever");

        // Antibiotics
        add("00764973", "Amoxicillin-Ratiopharm 500mg", "Amoxicillin", "500mg", "1-0-1-0", "Infection");
        add("03882329", "Clarithromycin 500mg - 1A Pharma", "Clarithromycin", "500mg", "1-0-1-0",
                "Respiratory Infection");
        add("04756312", "Ciprofloxacin AL 250 mg Filmtabletten", "Ciprofloxacin", "250mg", "1-0-1-0",
                "Urinary Infection");

        // Statins
        add("00262660", "Simvastatin-Ratiopharm 20mg", "Simvastatin", "20mg", "0-0-0-1", "High Cholesterol");
        add("01234567", "Atorvastatin AbZ 20mg", "Atorvastatin", "20mg", "0-0-0-1", "High Cholesterol");

        // Blood Pressure / Heart
        add("01476329", "Ramipril-1A Pharma 5mg", "Ramipril", "5mg", "1-0-0-0", "Hypertension");
        add("00552467", "Metoprololsuccinat 47,5mg", "Metoprolol", "47.5mg", "1-0-0-0", "Hypertension");
        add("01020304", "Bisoprolol ratiopharm 2.5mg", "Bisoprolol", "2.5mg", "1-0-0-0", "Heart Failure");

        // Diabetes
        add("02468135", "Metformin Lich 1000mg", "Metformin", "1000mg", "1-0-1-0", "Diabetes Type 2");
        add("13579246", "Januvia 100mg Filmtabletten", "Sitagliptin", "100mg", "1-0-0-0", "Diabetes Type 2");

        // Others
        add("05566778", "Pantoprazol-Actavis 20 mg", "Pantoprazol", "20mg", "0-0-0-1 before dinner", "Acid Reflux");
        add("09988776", "L-Thyroxin Henning 50", "Levothyroxin-Natrium", "50µg", "1-0-0-0", "Hypothyroidism");
    }

    private void add(String pzn, String name, String ingredient, String strength, String dosage, String indication) {
        MedicationPlanEntry entry = new MedicationPlanEntry();
        entry.setPzn(pzn);
        entry.setMedicationName(name);
        entry.setActiveIngredient(ingredient);
        entry.setStrength(strength);
        entry.setDosageStructured(dosage);
        entry.setIndication(indication);
        database.add(entry);
    }

    /**
     * Search medications by name or PZN (case-insensitive substring).
     */
    public List<MedicationPlanEntry> search(String query) {
        if (query == null || query.trim().isEmpty()) {
            return new ArrayList<>();
        }

        String lowerQuery = query.toLowerCase().trim();

        return database.stream()
                .filter(m -> (m.getMedicationName() != null && m.getMedicationName().toLowerCase().contains(lowerQuery))
                        ||
                        (m.getPzn() != null && m.getPzn().contains(lowerQuery)) ||
                        (m.getActiveIngredient() != null && m.getActiveIngredient().toLowerCase().contains(lowerQuery)))
                .limit(20) // Limit results
                .collect(Collectors.toList());
    }

    /**
     * Find exact match by PZN.
     */
    public MedicationPlanEntry findByPzn(String pzn) {
        return database.stream()
                .filter(m -> pzn.equals(m.getPzn()))
                .findFirst()
                .orElse(null);
    }
}
