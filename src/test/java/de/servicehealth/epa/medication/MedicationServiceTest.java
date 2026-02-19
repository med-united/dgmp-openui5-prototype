package de.servicehealth.epa.medication;

import de.servicehealth.epa.medication.model.PrescriptionGroup;
import de.servicehealth.epa.medication.model.ReconciliationStatus;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

@QuarkusTest
public class MedicationServiceTest {

        @Inject
        MedicationService medicationService;

        @Test
        public void testReconciliationTreeStructure() {
                // KVNR X123456789 has hierarchical data in the updated fixture
                String kvnr = "X123456789";

                List<PrescriptionGroup> groups = medicationService.getReconciliationTree(kvnr);

                Assertions.assertNotNull(groups);
                Assertions.assertFalse(groups.isEmpty());

                // Check hierarchy
                boolean foundHierarchy = groups.stream()
                                .anyMatch(g -> g.getDispensations() != null && !g.getDispensations().isEmpty());
                Assertions.assertTrue(foundHierarchy, "Should find at least one prescription with dispensations");

                // Find Amoxicillin (should be ORPHAN_NEW or LINKED depending on logic)
                // In fixture: "Amoxicillin 500mg Kapseln", PZN "00764973"
                // In eMP (MedicationDatabase): Amoxicillin-Ratiopharm 500mg, PZN 00764973
                // So it should be LINKED by PZN

                PrescriptionGroup amoxicillin = groups.stream()
                                .filter(g -> "00764973".equals(g.getPrescription().getPzn()))
                                .findFirst()
                                .orElse(null);

                Assertions.assertNotNull(amoxicillin, "Amoxicillin should be present");
                Assertions.assertEquals(ReconciliationStatus.PROPOSAL_MATCH, amoxicillin.getStatus(),
                                "Amoxicillin should be PROPOSAL_MATCH by PZN (Soft Link)");

                // Find Ramipril (In fixture: PZN 00885967, substituted with Ramipril-Ratiopharm
                // 07654321)
                // In eMP: Ramipril-1A Pharma 5mg, PZN 01476329 (Different PZN!)
                // But ATC C09AA05 matches
                // So it should be UNLINKED_UPDATE

                PrescriptionGroup ramipril = groups.stream()
                                .filter(g -> "00885967".equals(g.getPrescription().getPzn()))
                                .findFirst()
                                .orElse(null);

                Assertions.assertNotNull(ramipril, "Ramipril should be present");
                // Note: Logic might be tricky depending on strictness.
                // Logic says: manual link -> PZN match -> ATC match (UNLINKED_UPDATE) ->
                // ORPHAN_NEW
                // Ramipril has different PZN in eMP. So check ATC.
                Assertions.assertEquals(ReconciliationStatus.UNLINKED_UPDATE, ramipril.getStatus(),
                                "Ramipril should be UNLINKED_UPDATE via ATC match");
        }
}
