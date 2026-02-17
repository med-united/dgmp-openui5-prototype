# ePA Medication Service Prototype

This project is a prototype for the German Electronic Patient Record (ePA) Medication Service UI. It simulates the interaction between the Electronic Medication List (eML) and the Electronic Medication Plan (eMP).

## Tech Stack

- **Backend**: Java 17, Quarkus (RESTEasy Reactive, Jackson)
- **Frontend**: SAPUI5 (OpenUI5)
- **FHIR**: HAPI FHIR (R4)
- **Security**: Telematik Smartcard integration (planned)

## Features

- **Patient Search**: Search by KVNR (e.g., X123456789).
- **Medication Plan (eMP)**: View, add, edit, pause, and delete medication entries.
- **Medication List (eML)**: View historical prescriptions and dispensements.
- **Reconciliation**: Compare eML vs eMP and easily add missing medications to the plan.
- **AMTS Safety Check**: Simulate safety checks for drug-drug interactions (e.g., Ibuprofen + Aspirin).
- **Duplicate Detection**: Smart detection of similar medications when adding to plan.
- **PDF Export**: Generate PDF versions of the medication plan (placeholder).

## Getting Started

### Prerequisites

- Java 17+
- Maven 3.8+
- Node.js (optional, for OpenUI5 development tools)

### Running the Application

1. Clone the repository.
2. Run the following command in the project root:

```bash
mvn quarkus:dev
```

3. Open your browser and navigate to `http://localhost:8080/webapp/index.html`.

## Testing

Run tests with:

```bash
mvn test
```

## Project Structure

- `src/main/java`: Backend resources and logic.
- `src/main/resources/META-INF/resources`: Frontend SAPUI5 application.
- `specs`: Project specifications and task tracking.
