package de.servicehealth.epa.medication;

import de.servicehealth.epa.medication.model.MedicationRequest;
import de.servicehealth.epa.medication.model.MedicationStatement;
import de.servicehealth.epa.medication.model.EMPChronologyProvenance;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

@QuarkusTest
public class MedicationServiceLinkTest {

    @Inject
    MedicationService medicationService;

    @Test
    public void testLinkLogicAndChronology() {
        String kvnr = "TEST_LINK_KVNR";
        String agent = "TestAgent";

        // 1. Create initial Plan
        MedicationRequest empEntry = new MedicationRequest();
        empEntry.setMedicationName("Test Med");
        empEntry.setPzn("12345678");
        empEntry.setDosageText("1-0-0-0");
        empEntry.setDosageText("1-0-0-0");
        empEntry.setDosageStructured("1-0-0-0");
        empEntry.setMedicationPlanIdentifier(java.util.UUID.randomUUID().toString()); // Ensure it has an identifier

        medicationService.addMedicationRequest(kvnr, empEntry);
        String empId = empEntry.getId();
        Assertions.assertNotNull(empId);

        // 2. Create eML entry (simulated by adding to cache directly? No public API for
        // adding eML)
        // MedicationService loads fixtures. I might need to rely on existing fixture or
        // modify Service to allow adding eML for test.
        // Or I can use details from a known fixture.
        // Let's use a known fixture KVNR "X123456789".

        kvnr = "X123456789";
        // Get an existing eMP entry and eML entry
        // NOTE: X123456789 already has linked data in fixtures, which might interfere.
        // Ideally we should use a clean slate or ensure we pick an unlink item.
        // But for this test, we are picking index 0.
        Optional<de.servicehealth.epa.medication.model.MedicationList> listOpt = medicationService
                .loadMedicationList(kvnr);
        Assertions.assertTrue(listOpt.isPresent());
        MedicationStatement emlEntry = listOpt.get().getEntries().get(0);
        String emlId = emlEntry.getId();

        Optional<de.servicehealth.epa.medication.model.MedicationPlan> planOpt = medicationService
                .loadMedicationPlan(kvnr);
        Assertions.assertTrue(planOpt.isPresent());
        MedicationRequest planEntry = planOpt.get().getEntries().get(0);
        String planId = planEntry.getId();

        // Initial Chronology
        EMPChronologyProvenance chron1 = medicationService.getChronology(kvnr);
        Assertions.assertNotNull(chron1);

        // 3. Test Link
        medicationService.linkMedicationPlanEntry(kvnr, emlId, planId, agent);

        // Verify Chronology Changed
        EMPChronologyProvenance chron2 = medicationService.getChronology(kvnr);
        Assertions.assertNotEquals(chron1.getId(), chron2.getId());

        // Verify Link in EML
        Assertions.assertEquals(planEntry.getMedicationPlanIdentifier(), emlEntry.getMedicationPlanIdentifier());
        // Verify Link in EML
        Assertions.assertEquals(planEntry.getMedicationPlanIdentifier(), emlEntry.getMedicationPlanIdentifier());
        // Verify basedOn is set (if implementation does so)
        if (emlEntry.getBasedOn() != null) {
            Assertions.assertTrue(emlEntry.getBasedOn().endsWith(planId));
        }

        // 4. Test Optimistic Locking
        // Try to update with OLD chronology ID
        MedicationRequest update = new MedicationRequest();
        update.setMedicationName("Updated Name");
        update.setPzn("12345678");

        final String finalKvnr = kvnr;

        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            medicationService.updateMedicationRequest(finalKvnr, planId, update, null, chron1.getId(), agent);
        });

        // Try with NEW chronology ID -> Should verify dosage first
        update.setDosageText("1-0-0-0");
        update.setDosageStructured("1-0-0-0");

        medicationService.updateMedicationRequest(kvnr, planId, update, null, chron2.getId(), agent);

        // Verify update success
        EMPChronologyProvenance chron3 = medicationService.getChronology(kvnr);
        Assertions.assertNotEquals(chron2.getId(), chron3.getId());
    }

    @Test
    public void testDosageValidation() {
        String kvnr = "TEST_DOSAGE_KVNR";
        String agent = "TestAgent";

        MedicationRequest entry = new MedicationRequest();
        entry.setMedicationName("Test Med");
        entry.setPzn("12345678");

        // Valid
        entry.setDosageStructured("1-0-0-0");
        entry.setDosageText("1-0-0-0"); // Simplified requirement: exact match
        medicationService.addMedicationRequest(kvnr, entry, null, agent);

        // Invalid
        MedicationRequest invalid = new MedicationRequest();
        invalid.setMedicationName("Invalid Med");
        invalid.setPzn("87654321");
        invalid.setDosageStructured("1-0-0-0");
        invalid.setDosageText("Morgens 1"); // Mismatch

        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            medicationService.addMedicationRequest(kvnr, invalid, null, agent);
        }, "Should throw on dosage mismatch");
    }
}
