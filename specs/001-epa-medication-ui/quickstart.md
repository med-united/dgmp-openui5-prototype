# Quickstart Guide: ePA Medication Service UI

**Feature**: ePA Medication Service UI Prototype  
**Branch**: `001-epa-medication-ui`  
**Date**: 2026-02-14

## Prerequisites

### Required Software

- **Java**: JDK 17 or higher
- **Maven**: 3.8+
- **Node.js**: 16+ (optional, only if modifying OpenUI5 build tools)
- **Git**: For cloning and branch management
- **PC/SC Card Reader**: For eGK card reading (optional - fixtures work without card reader)

### Recommended IDE

- IntelliJ IDEA (Ultimate or Community) with:
  - Java plugin
  - Maven plugin
  - JavaScript/TypeScript plugin (for OpenUI5 development)
- Or VSCode with:
  - Extension Pack for Java
  - UI5 Language Assistant extension

---

## Initial Setup

### 1. Clone Repository and Checkout Feature Branch

```bash
git clone <repository-url>
cd fhir-prototype
git checkout 001-epa-medication-ui
```

### 2. Build Backend

```bash
# From repository root
mvn clean install
```

**Expected Output**: BUILD SUCCESS

### 3. Verify Card Reader (Optional)

If you have a PC/SC card reader connected:

```bash
# List available card readers
pcsc_scan
```

**Expected Output**: List of connected readers. If none found, eGK reading will show error but fixtures still work.

---

## Running the Application

### Start Backend (Quarkus Dev Mode)

```bash
# From repository root
mvn quarkus:dev
```

**Expected Output**:
```
Listening for transport dt_socket at address: 5005
__  ____  __  _____   ___  __ ____  ______ 
 --/ __ \/ / / / _ | / _ \/ //_/ / / / __/ 
 -/ /_/ / /_/ / __ |/ , _/ ,< / /_/ /\ \   
--\___\_\____/_/ |_/_/|_/_/|_|\____/___/   
INFO  [io.quarkus] (Quarkus Main Thread) fhir-prototype 1.0.0-SNAPSHOT on JVM (powered by Quarkus 3.x.x) started in 2.xxxs. Listening on: http://localhost:8080
```

**Backend URLs**:
- Application: http://localhost:8080
- Dev UI: http://localhost:8080/q/dev
- API Base: http://localhost:8080/api
- Health Check: http://localhost:8080/q/health

### Access Frontend

Once Quarkus is running, open your browser to:

```
http://localhost:8080
```

**Expected UI**: Patient selection screen with:
- KVNR search input field
- "Read from eGK Card" button
- Recent patients sidebar (initially empty)

---

## Sample Data

### Fixture Patients

The application includes 3 pre-loaded patients in `src/main/resources/fixtures/patients.json`:

| KVNR | Name | Description |
|------|------|-------------|
| X123456789 | Erika Mustermann | Patient with many medications (10+ in eMP) |
| Y987654321 | Max Beispiel | Patient with few medications (2-3 in eMP) |
| Z555111222 | Anna Test | Patient with empty eML and eMP |

### Quick Test Flow

1. **Search for Patient**:
   - Enter KVNR: `X123456789`
   - Click search or press Enter
   - Patient view loads with three tabs

2. **View eML** (History):
   - Default tab shows historical prescriptions/dispensements
   - Entries sorted by date (newest first)

3. **View eMP** (Current Plan):
   - Click "eMP (Current Plan)" tab
   - See toolbar with 3 buttons: Add Medication | Export to PDF | Run AMTS Check
   - See medication table with row-level action icons

4. **Add Medication**:
   - Click "Add Medication" in toolbar
   - Search for medication by PZN (e.g., `12345678`)
   - Fill dosage, instructions
   - Save

5. **Duplicate Detection**:
   - Try adding same medication again
   - Modal dialog appears with side-by-side comparison
   - Choose "Update existing" or "Create new"

6. **Reconciliation**:
   - Click "Reconciliation" tab
   - See split-view (eML left, eMP right)
   - Highlighted discrepancies show in eML
   - Click "Add to eMP" on discrepant entry

7. **eGK Card Reading** (if card reader available):
   - Return to home screen ("Switch Patient" button)
   - Click "Read from eGK Card"
   - Insert eGK card in reader
   - Patient data auto-populates

---

## Development Workflow

### Backend Development

**Project Structure**:
```
src/main/java/de/servicehealth/epa/
├── patient/          # Patient operations
├── medication/       # eML/eMP CRUD
└── cardreading/      # eGK integration
```

**Hot Reload**: Quarkus dev mode automatically reloads on Java file changes.

**Testing**:
```bash
# Run all tests
mvn test

# Run specific test class
mvn test -Dtest=MedicationServiceTest

# Run integration tests
mvn verify
```

### Frontend Development

**Project Structure**:
```
webapp/
├── controller/      # OpenUI5 controllers
├── view/            # XML views
├── model/           # Data models and formatters
└── test/            # QUnit and OPA5 tests
```

**Modify UI**:
1. Edit XML views in `webapp/view/*.view.xml`
2. Edit controllers in `webapp/controller/*.controller.js`
3. Browser auto-refreshes (Quarkus serves static files)

**Testing**:
```bash
# Open in browser
http://localhost:8080/test/unit/allTests.qunit.html      # QUnit tests
http://localhost:8080/test/integration/opaTests.qunit.html  # OPA5 journey tests
```

---

## API Testing

### Using cURL

**Search for patient**:
```bash
curl http://localhost:8080/api/patients/search?kvnr=X123456789
```

**Get eMP**:
```bash
curl http://localhost:8080/api/medications/plan/X123456789
```

**Add medication**:
```bash
curl -X POST http://localhost:8080/api/medications/plan/X123456789/entries \
  -H "Content-Type: application/json" \
  -d '{
    "pzn": "12345678",
    "medicationName": "Test Medication",
    "activeIngredient": "Test Active Ingredient",
    "status": "active",
    "entrySource": "self_medication",
    "dosageText": "1x täglich"
  }'
```

**Read from eGK**:
```bash
curl -X POST http://localhost:8080/api/cardreader/read-patient
```

### Using Swagger UI

Access OpenAPI documentation:
```
http://localhost:8080/q/swagger-ui
```

Explore all endpoints interactively with request/response examples.

---

## Fixture Customization

### Adding New Patient

1. Edit `src/main/resources/fixtures/patients.json`:
```json
{
  "kvnr": "W111222333",
  "firstName": "New",
  "lastName": "Patient",
  "dateOfBirth": "1990-05-15",
  "insuranceType": "GKV"
}
```

2. Create corresponding medication files:
   - `medication-list-W111222333.json`
   - `medication-plan-W111222333.json`

3. Restart Quarkus (fixtures loaded at startup)

### Modifying Medications

Edit fixture files in `src/main/resources/fixtures/`:
- `medication-list-{kvnr}.json`: eML entries
- `medication-plan-{kvnr}.json`: eMP entries

Follow JSON structure from `contracts/openapi.yaml` schemas.

---

## Troubleshooting

### Backend Issues

**Problem**: `mvn quarkus:dev` fails with port conflict
**Solution**: Change port in `application.properties`:
```properties
quarkus.http.port=8081
```

**Problem**: Card reader errors in logs
**Solution**: Card reader is optional. If not needed, ignore errors. Fixtures work independently.

**Problem**: FHIR validation warnings
**Solution**: Prototype uses simplified FHIR structures. Warnings expected; focus on structure not full validation.

### Frontend Issues

**Problem**: UI not loading (blank page)
**Solution**: Check browser console for errors. Verify Quarkus is running on http://localhost:8080.

**Problem**: OpenUI5 components not rendering
**Solution**: Check `webapp/manifest.json` for correct CDN URL. Verify internet connection (CDN required).

**Problem**: Tab navigation not working
**Solution**: Check browser console for JavaScript errors. Verify `PatientView.controller.js` loaded correctly.

### Data Issues

**Problem**: Patient not found after search
**Solution**: Verify KVNR exists in `fixtures/patients.json`. KVNR is case-sensitive.

**Problem**: Empty eML or eMP
**Solution**: Check corresponding fixture files exist and contain valid JSON. For patient Z555111222, empty is expected (test case).

**Problem**: Duplicate detection not working
**Solution**: Verify PZN or ATC codes match between new and existing entries. Check `MedicationService.java` matching logic.

---

## Next Steps

### For Development (Implementation Phase)

1. **Review Specification**: See `specs/001-epa-medication-ui/spec.md` for all 14 user stories
2. **Review Plan**: See `specs/001-epa-medication-ui/plan.md` for architecture and constitution check
3. **Generate Tasks**: Run `/speckit.tasks` to create task breakdown from plan
4. **Start Implementation**: Pick tasks from `specs/001-epa-medication-ui/tasks.md` and begin coding

### For Testing (Verification Phase)

1. **Unit Tests**: Run `mvn test` to verify backend services
2. **API Tests**: Run integration tests with `mvn verify`
3. **UI Tests**: Open QUnit and OPA5 test pages in browser
4. **Manual Testing**: Follow user scenarios from spec.md

### For Deployment (Future)

*Not in scope for initial UI prototype. Backend serves fixtures only.*

When integrating with real TI backend:
1. Replace `FixtureLoader` with actual TI service calls
2. Update `application.properties` with TI connector configuration
3. Add authentication/authorization (currently not implemented)

---

## Additional Resources

- **Specification**: [`specs/001-epa-medication-ui/spec.md`](file:///home/dennis/git/fhir-prototype/specs/001-epa-medication-ui/spec.md)
- **Implementation Plan**: [`specs/001-epa-medication-ui/plan.md`](file:///home/dennis/git/fhir-prototype/specs/001-epa-medication-ui/plan.md)
- **Data Model**: [`specs/001-epa-medication-ui/data-model.md`](file:///home/dennis/git/fhir-prototype/specs/001-epa-medication-ui/data-model.md)
- **API Contracts**: [`specs/001-epa-medication-ui/contracts/openapi.yaml`](file:///home/dennis/git/fhir-prototype/specs/001-epa-medication-ui/contracts/openapi.yaml)
- **Quarkus Docs**: https://quarkus.io/guides/
- **OpenUI5 Docs**: https://ui5.sap.com/
- **ePA Medication Service IG**: https://simplifier.net/epa-medication

---

**Ready to Start**: Development environment configured. Begin implementation by running `/speckit.tasks` to generate task breakdown.
