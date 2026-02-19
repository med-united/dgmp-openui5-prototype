package de.servicehealth.epa.medication;

import de.servicehealth.epa.medication.model.MedicationList;
import de.servicehealth.epa.medication.model.MedicationStatement;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Optional;

@QuarkusTest
public class LinkLogicTest {

    @Inject
    MedicationService medicationService;

    @Test
    public void testCascadingLinkAndUnlink() {
        String kvnr = "X123456789";
        String agent = "TestAgent";

        // 1. Load initial state
        Optional<MedicationList> listOpt = medicationService.loadMedicationList(kvnr);
        Assertions.assertTrue(listOpt.isPresent(), "Medication list should exist");
        MedicationList list = listOpt.get();

        // 2. Ensure we have a plan entry to link TO
        Optional<de.servicehealth.epa.medication.model.MedicationPlan> planOpt = medicationService
                .loadMedicationPlan(kvnr);
        Assertions.assertTrue(planOpt.isPresent(), "Medication plan should exist");
        de.servicehealth.epa.medication.model.MedicationRequest planEntry = planOpt.get().getEntries().get(0);
        String targetPlanEntryId = planEntry.getId();

        // Find Amoxicillin prescription (PZN 00764973)
        MedicationStatement prescription = list.getEntries().stream()
                .filter(e -> "00764973".equals(e.getPzn()) && "prescription".equals(e.getEntryType()))
                .findFirst()
                .orElse(null);

        Assertions.assertNotNull(prescription, "Should find Amoxicillin prescription");
        Assertions.assertTrue(prescription.getDispensations() != null && !prescription.getDispensations().isEmpty(),
                "Prescription should have dispensations");

        String prescriptionId = prescription.getId();

        // 3. Test Link
        System.out.println("Testing Link...");
        // Use new API: linkMedicationPlanEntry
        medicationService.linkMedicationPlanEntry(kvnr, prescriptionId, targetPlanEntryId, agent);

        // Reload to verify effects
        list = medicationService.loadMedicationList(kvnr).get();
        // scan top level
        prescription = list.getEntries().stream().filter(e -> e.getId().equals(prescriptionId)).findFirst()
                .orElse(null);

        Assertions.assertNotNull(prescription);

        // Verify Hard Link (Identifier is set)
        Assertions.assertNotNull(prescription.getMedicationPlanIdentifier(), "Prescription should have Identifier set");
        Assertions.assertEquals(planEntry.getMedicationPlanIdentifier(), prescription.getMedicationPlanIdentifier(),
                "Identifier should match plan entry");

        // 4. Test Unlink
        System.out.println("Testing Unlink...");
        medicationService.unlinkMedicationPlanEntry(kvnr, prescriptionId, agent);

        // Reload to verify effects
        list = medicationService.loadMedicationList(kvnr).get();
        // scan top level
        prescription = list.getEntries().stream().filter(e -> e.getId().equals(prescriptionId)).findFirst()
                .orElse(null);
        Assertions.assertNotNull(prescription);

        Assertions.assertNull(prescription.getMedicationPlanIdentifier(),
                "Prescription should be unlinked (Identifier null)");
    }
}
