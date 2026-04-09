package de.servicehealth.epa.medication;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * Contract tests for the /fhir/ endpoints (Phase 2 gates — task 3.11).
 *
 * Phase 2 gates verified:
 *  - POST-based search returns valid Bundle (searchset)
 *  - _include=MedicationDispense:medication-request returns linked MedicationRequest entries
 *  - POST /fhir (batch) returns batch-response with correct per-entry statuses
 */
@QuarkusTest
public class FhirResourceTest {

    private static final String KVNR = "X123456789";
    private static final String ORG_HEADER = "Test-Organization-001";
    private static final String FHIR_CONTENT = "application/fhir+json";

    // -------------------------------------------------------------------------
    // Task 3.3 — Patient search
    // -------------------------------------------------------------------------

    @Test
    void testGetPatientByKvnr() {
        given()
                .queryParam("identifier", KVNR)
                .when().get("/fhir/Patient")
                .then()
                .statusCode(200)
                .contentType(containsString("json"))
                .body("resourceType", equalTo("Bundle"))
                .body("type", equalTo("searchset"))
                .body("entry", not(empty()))
                .body("entry[0].resource.resourceType", equalTo("Patient"))
                .body("entry[0].resource.identifier[0].value", equalTo(KVNR));
    }

    @Test
    void testPostSearchPatient() {
        given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("identifier", KVNR)
                .when().post("/fhir/Patient/_search")
                .then()
                .statusCode(200)
                .body("resourceType", equalTo("Bundle"))
                .body("entry[0].resource.resourceType", equalTo("Patient"));
    }

    @Test
    void testPatientNotFound() {
        given()
                .queryParam("identifier", "ZNOTEXIST")
                .when().get("/fhir/Patient")
                .then()
                .statusCode(200)
                .body("total", equalTo(0))
                .body("entry", empty());
    }

    // -------------------------------------------------------------------------
    // Task 3.1 — MedicationRequest (eMP) search
    // -------------------------------------------------------------------------

    @Test
    void testGetMedicationRequests() {
        given()
                .queryParam("patient", KVNR)
                .when().get("/fhir/MedicationRequest")
                .then()
                .statusCode(200)
                .body("resourceType", equalTo("Bundle"))
                .body("type", equalTo("searchset"))
                .body("total", greaterThan(0))
                .body("entry", not(empty()));
    }

    @Test
    void testPostSearchMedicationRequests() {
        given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("patient", KVNR)
                .when().post("/fhir/MedicationRequest/_search")
                .then()
                .statusCode(200)
                .body("resourceType", equalTo("Bundle"))
                .body("type", equalTo("searchset"))
                .body("total", greaterThan(0));
    }

    @Test
    void testMedicationRequestBundleContainsMatchEntries() {
        given()
                .queryParam("patient", KVNR)
                .when().get("/fhir/MedicationRequest")
                .then()
                .statusCode(200)
                .body("entry.findAll { it.search.mode == 'match' }.resource.resourceType",
                        everyItem(equalTo("MedicationRequest")));
    }

    @Test
    void testMedicationRequestMissingPatientParam() {
        given()
                .when().get("/fhir/MedicationRequest")
                .then()
                .statusCode(400);
    }

    // -------------------------------------------------------------------------
    // Task 3.2 — MedicationDispense (eML) search with _include/_revinclude
    // -------------------------------------------------------------------------

    @Test
    void testPostSearchMedicationDispensesWithIncludes() {
        given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("patient", KVNR)
                .formParam("_include", "MedicationDispense:medication-request")
                .formParam("_revinclude", "MedicationStatement:derived-from")
                .when().post("/fhir/MedicationDispense/_search")
                .then()
                .statusCode(200)
                .body("resourceType", equalTo("Bundle"))
                .body("type", equalTo("searchset"))
                .body("total", greaterThan(0))
                // Phase 2 gate: _include returns linked MedicationRequest entries
                .body("entry.resource.resourceType", hasItem("MedicationRequest"))
                .body("entry.resource.resourceType", hasItem("MedicationDispense"))
                // _revinclude returns MedicationStatement entries
                .body("entry.resource.resourceType", hasItem("MedicationStatement"));
    }

    @Test
    void testMedicationDispenseWithoutRevincludeExcludesStatements() {
        given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("patient", KVNR)
                .formParam("_include", "MedicationDispense:medication-request")
                .when().post("/fhir/MedicationDispense/_search")
                .then()
                .statusCode(200)
                .body("entry.resource.resourceType", not(hasItem("MedicationStatement")));
    }

    @Test
    void testMedicationDispenseMatchEntriesAreDispenses() {
        given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("patient", KVNR)
                .formParam("_include", "MedicationDispense:medication-request")
                .when().post("/fhir/MedicationDispense/_search")
                .then()
                .statusCode(200)
                .body("entry.findAll { it.search.mode == 'match' }.resource.resourceType",
                        everyItem(equalTo("MedicationDispense")));
    }

    // -------------------------------------------------------------------------
    // Task 3.4 — Medication name search
    // -------------------------------------------------------------------------

    @Test
    void testMedicationNameSearch() {
        given()
                .queryParam("name:contains", "Metformin")
                .when().get("/fhir/Medication")
                .then()
                .statusCode(200)
                .body("resourceType", equalTo("Bundle"))
                .body("entry", not(empty()))
                .body("entry[0].resource.resourceType", equalTo("Medication"));
    }

    @Test
    void testMedicationNameSearchTooShort() {
        given()
                .queryParam("name:contains", "M")
                .when().get("/fhir/Medication")
                .then()
                .statusCode(400);
    }

    // -------------------------------------------------------------------------
    // Task 3.5 — Batch endpoint: batch-response with correct per-entry statuses
    // -------------------------------------------------------------------------

    @Test
    void testBatchWithMissingOrgHeader() {
        String batch = batchPutRequest("batch-test-001", KVNR, "50000001");
        given()
                .contentType(FHIR_CONTENT)
                .body(batch)
                .when().post("/fhir")
                .then()
                .statusCode(422)
                .body("issue[0].diagnostics", equalTo("SVC_ORG_HEADER_PROFILE_MISMATCH"));
    }

    @Test
    void testBatchAddNewEntry() {
        String id = "batch-new-" + System.currentTimeMillis();
        // Use a unique PZN derived from the id to avoid cross-test collisions
        String pzn = "10000001";
        String batch = batchPutRequest(id, KVNR, pzn);
        given()
                .contentType(FHIR_CONTENT)
                .header("X-Requesting-Organization", ORG_HEADER)
                .body(batch)
                .when().post("/fhir")
                .then()
                .statusCode(200)
                .body("resourceType", equalTo("Bundle"))
                .body("type", equalTo("batch-response"))
                .body("entry[0].response.status", startsWith("20")); // 201 Created or 200 OK
    }

    @Test
    void testBatchDuplicateDetection() {
        String dupPzn = "88888888";
        // First insert — should succeed
        String id1 = "batch-dup-" + System.currentTimeMillis();
        given()
                .contentType(FHIR_CONTENT)
                .header("X-Requesting-Organization", ORG_HEADER)
                .body(batchPutRequest(id1, KVNR, dupPzn))
                .when().post("/fhir")
                .then()
                .statusCode(200)
                .body("entry[0].response.status", startsWith("20"));

        // Second insert with same PZN → should detect duplicate
        String id2 = "batch-dup-b-" + System.currentTimeMillis();
        given()
                .contentType(FHIR_CONTENT)
                .header("X-Requesting-Organization", ORG_HEADER)
                .body(batchPutRequest(id2, KVNR, dupPzn))
                .when().post("/fhir")
                .then()
                .statusCode(200)
                .body("entry[0].response.status", equalTo("409 Conflict"))
                .body("entry[0].response.outcome.issue[0].diagnostics", equalTo("isDuplicate"));
    }

    @Test
    void testBatchDeleteEntry() {
        // Add first using a unique PZN, then delete
        String id = "batch-del-" + System.currentTimeMillis();
        String pzn = "20000002";
        given()
                .contentType(FHIR_CONTENT)
                .header("X-Requesting-Organization", ORG_HEADER)
                .body(batchPutRequest(id, KVNR, pzn))
                .when().post("/fhir")
                .then()
                .statusCode(200)
                .body("entry[0].response.status", startsWith("20"));

        String deleteBundle = "{"
                + "\"resourceType\":\"Bundle\","
                + "\"type\":\"batch\","
                + "\"entry\":[{"
                + "\"request\":{\"method\":\"DELETE\",\"url\":\"MedicationRequest/" + id + "\"}"
                + "}]}";
        given()
                .contentType(FHIR_CONTENT)
                .header("X-Requesting-Organization", ORG_HEADER)
                .body(deleteBundle)
                .when().post("/fhir")
                .then()
                .statusCode(200)
                .body("entry[0].response.status", equalTo("204 No Content"));
    }

    // -------------------------------------------------------------------------
    // Task 3.5a / 3.5b — Link / Unlink operations
    // -------------------------------------------------------------------------

    @Test
    void testLinkEmpMissingOrgHeader() {
        given()
                .contentType(FHIR_CONTENT)
                .body("{\"resourceType\":\"Parameters\",\"parameter\":[]}")
                .when().post("/fhir/MedicationStatement/stmt-004/link-emp")
                .then()
                .statusCode(422);
    }

    @Test
    void testLinkEmpStatementNotFound() {
        given()
                .contentType(FHIR_CONTENT)
                .header("X-Requesting-Organization", ORG_HEADER)
                .body(linkParameters("emp-x-002"))
                .when().post("/fhir/MedicationStatement/NOTEXIST/link-emp")
                .then()
                .statusCode(404);
    }

    @Test
    void testLinkEmpSuccess() {
        given()
                .contentType(FHIR_CONTENT)
                .header("X-Requesting-Organization", ORG_HEADER)
                .body(linkParameters("emp-x-002"))
                .when().post("/fhir/MedicationStatement/stmt-004/link-emp")
                .then()
                .statusCode(200)
                .body("resourceType", equalTo("Parameters"))
                .body("parameter.name", hasItem("updated-statement"))
                .body("parameter.name", hasItem("provenance"));
    }

    @Test
    void testUnlinkEmpSuccess() {
        // Link first
        given()
                .contentType(FHIR_CONTENT)
                .header("X-Requesting-Organization", ORG_HEADER)
                .body(linkParameters("emp-x-002"))
                .when().post("/fhir/MedicationStatement/stmt-004/link-emp");

        // Then unlink
        given()
                .contentType(FHIR_CONTENT)
                .header("X-Requesting-Organization", ORG_HEADER)
                .body("{\"resourceType\":\"Parameters\"}")
                .when().post("/fhir/MedicationStatement/stmt-004/unlink-emp")
                .then()
                .statusCode(200)
                .body("resourceType", equalTo("Parameters"))
                .body("parameter.name", hasItem("updated-statement"))
                .body("parameter.name", hasItem("provenance"));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private String batchPutRequest(String id, String kvnr, String pzn) {
        return "{"
                + "\"resourceType\":\"Bundle\","
                + "\"type\":\"batch\","
                + "\"entry\":[{"
                + "\"request\":{\"method\":\"PUT\",\"url\":\"MedicationRequest/" + id + "\"},"
                + "\"resource\":{"
                + "\"resourceType\":\"MedicationRequest\","
                + "\"id\":\"" + id + "\","
                + "\"status\":\"active\","
                + "\"intent\":\"plan\","
                + "\"medicationCodeableConcept\":{"
                + "\"coding\":[{\"system\":\"http://fhir.de/CodeSystem/ifa/pzn\",\"code\":\"" + pzn + "\"}],"
                + "\"text\":\"Test Medication " + pzn + "\""
                + "},"
                + "\"subject\":{\"identifier\":{"
                + "\"system\":\"http://fhir.de/sid/gkv/kvid-10\","
                + "\"value\":\"" + kvnr + "\""
                + "}},"
                + "\"authoredOn\":\"2026-01-01\""
                + "}"
                + "}]}";
    }

    private String linkParameters(String empEntryId) {
        return "{"
                + "\"resourceType\":\"Parameters\","
                + "\"parameter\":[{"
                + "\"name\":\"emp-entry-id\","
                + "\"valueString\":\"" + empEntryId + "\""
                + "}]}";
    }
}
