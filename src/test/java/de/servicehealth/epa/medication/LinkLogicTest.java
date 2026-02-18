package de.servicehealth.epa.medication;

import de.servicehealth.epa.medication.model.MedicationList;
import de.servicehealth.epa.medication.model.MedicationListEntry;
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

        // 1. Load initial state
        Optional<MedicationList> listOpt = medicationService.loadMedicationList(kvnr);
        Assertions.assertTrue(listOpt.isPresent(), "Medication list should exist");
        MedicationList list = listOpt.get();

        // Find Amoxicillin prescription (PZN 00764973)
        MedicationListEntry prescription = list.getEntries().stream()
                .filter(e -> "00764973".equals(e.getPzn()) && "prescription".equals(e.getEntryType()))
                .findFirst()
                .orElse(null);

        Assertions.assertNotNull(prescription, "Should find Amoxicillin prescription");
        Assertions.assertTrue(prescription.getDispensations() != null && !prescription.getDispensations().isEmpty(),
                "Prescription should have dispensations");

        MedicationListEntry dispensation = prescription.getDispensations().get(0);
        String prescriptionId = prescription.getId();
        String dispensationId = dispensation.getId();
        String targetPlanId = "emp-test-link-target";

        // 2. Test Link
        System.out.println("Testing Link...");
        medicationService.createLink(kvnr, prescriptionId, targetPlanId);

        // Reload to verify effects
        list = medicationService.loadMedicationList(kvnr).get();
        prescription = findEntryInList(list, prescriptionId);
        dispensation = findEntryInList(list, dispensationId); // Helper needed because findEntryInList might not check
                                                              // deep

        // Note: findEntryInList above needs to be smart or we just navigate
        // Easier to navigate from prescription again
        if (prescription == null) {
            // scan top level
            prescription = list.getEntries().stream().filter(e -> e.getId().equals(prescriptionId)).findFirst()
                    .orElse(null);
        }
        Assertions.assertNotNull(prescription);
        dispensation = prescription.getDispensations().get(0);

        Assertions.assertEquals(targetPlanId, prescription.getLinkedToPlanId(), "Prescription should be linked");
        Assertions.assertEquals(targetPlanId, dispensation.getLinkedToPlanId(),
                "Dispensation should be linked (cascaded)");

        // 3. Test Unlink
        System.out.println("Testing Unlink...");
        medicationService.removeLink(kvnr, prescriptionId);

        // Reload to verify effects
        list = medicationService.loadMedicationList(kvnr).get();
        // scan top level
        prescription = list.getEntries().stream().filter(e -> e.getId().equals(prescriptionId)).findFirst()
                .orElse(null);
        Assertions.assertNotNull(prescription);
        dispensation = prescription.getDispensations().get(0);

        Assertions.assertNull(prescription.getLinkedToPlanId(), "Prescription should be unlinked");
        Assertions.assertNull(dispensation.getLinkedToPlanId(), "Dispensation should be unlinked (cascaded)");
    }

    private MedicationListEntry findEntryInList(MedicationList list, String id) {
        for (MedicationListEntry entry : list.getEntries()) {
            if (entry.getId().equals(id))
                return entry;
            if (entry.getDispensations() != null) {
                for (MedicationListEntry disp : entry.getDispensations()) {
                    if (disp.getId().equals(id))
                        return disp;
                }
            }
        }
        return null;
    }
}
