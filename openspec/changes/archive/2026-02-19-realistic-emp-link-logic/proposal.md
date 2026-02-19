## Why

Die aktuelle Link-Logik zwischen eML und eMP ist ein Prototyp-Workaround: Sie verwendet eine In-Memory-Map (`linkMap`) mit einfachen String-IDs statt des im ePA-Standard definierten `MedicationPlanIdentifier`. Das ist nicht FHIR-konform und bildet die realen Abläufe (Verordnung → Dispensierung → Reconciliation) nicht korrekt ab. Diese Änderung ersetzt den Workaround durch eine realistische Implementierung, die dem dgMP-Standard entspricht.

## What Changes

- **`MedicationPlanIdentifier` einführen**: Jeder eMP-Eintrag (`MedicationPlanEntry`) erhält einen stabilen, eindeutigen `medicationPlanIdentifier` (UUID). Dieser Identifier ist das technische Bindeglied zwischen eML und eMP.
- **Linking-Mechanismus umstellen**: Statt einer separaten `linkMap` wird der `medicationPlanIdentifier` direkt in den eML-Einträgen (`MedicationListEntry`) gespeichert. Ein **harter Link** (grünes Kettensymbol 🔗) besteht nur, wenn eML-Eintrag und eMP-Eintrag denselben Identifier tragen.
- **DTO-Erweiterung statt FHIR-Ressourcen**: Wir behalten die bestehenden einfachen Java-DTOs (`MedicationListEntry`, `MedicationPlanEntry`) bei und erweitern sie lediglich um das Feld `medicationPlanIdentifier`. Es werden keine vollen FHIR-Ressourcen generiert oder geparst.
- **Soft-Match vs. Hard-Link**: Die bisherige PZN/ATC-Matching-Logik bleibt als **Vorschlags-Logik** erhalten (gelb: "Möglicher Match"). Der Soft-Match priorisiert den Wirkstoff (ATC/ASK, erste 7 Stellen) über die PZN, um Rabattverträge (unterschiedliche Präparate) korrekt abzubilden. Nur `link-emp` setzt den echten Identifier (Hard-Link).
- **FHIR-Logik als REST-Operationen**: Die Endpunkte bilden die Logik der FHIR-Operationen nach, nutzen aber unsere einfachen DTOs (kein `$`-Prefix, um URL-Probleme zu vermeiden):
  - `POST /api/medications/link-emp` (ersetzt `POST /api/medications/link`)
  - `POST /api/medications/unlink-emp` (ersetzt `DELETE /api/medications/link`)
  - `POST /api/medications/add-emp-entry` (neuer eMP-Eintrag aus eML-Daten, schreibt Identifier zurück)
  - `POST /api/medications/update-emp-entry` (verknüpft Abgabe mit bestehendem eMP-Eintrag, prüft Versionskonflikte: `acknowledgedChronologyId` muss mit aktueller `EMPChronologyProvenance` ID übereinstimmen -> 409 Conflict wenn ungleich)
- **`X-Requesting-Organization` Header**: Alle schreibenden Operationen müssen diesen HTTP-Header senden (Base64-kodierte FHIR-Organization). Backend validiert Vorhandensein (ohne kryptografische Prüfung im Prototyp).
- **Provenance-Generierung**: Bei jeder schreibenden Operation (`link-emp`, `unlink-emp`, `add-emp-entry`, `update-emp-entry`) generiert das Backend automatisch zwei Provenance-Ressourcen:
  1. `EPAActivityProvenance`: Audit-Log (Wer hat es getan? Quelle: `X-Requesting-Organization`).
  2. `EMPChronologyProvenance`: Neuer Gesamtzustand des Plans (Versionierung).
- **eMP-Eintrag zeigt "Zuletzt abgegeben am"**: Nach dem Verknüpfen zeigt der eMP-Eintrag das Datum der jüngsten verknüpften Abgabe aus der eML.

## Capabilities

### New Capabilities
- `emp-link-identifier`: Einführung des `MedicationPlanIdentifier` als zentrales Bindeglied zwischen eML und eMP, inklusive der vier FHIR-Operationen `link-emp`, `unlink-emp`, `add-emp-entry` und `update-emp-entry`.

### Modified Capabilities
- `001-epa-medication-ui`: Die Reconciliation-Logik und die API-Endpunkte für Link/Unlink/Add ändern sich grundlegend (neue Operation-Namen, neues Datenmodell).

## Impact

- **Backend**: `MedicationService.java` (Kernlogik + Versionskonflikt-Prüfung), `MedicationResource.java` (neue `operation`-Pfade + Header-Validierung), `MedicationListEntry.java` und `MedicationPlanEntry.java` (neues Feld `medicationPlanIdentifier`). Implementierung der `checkChronologyId`-Logik via `EMPChronologyProvenance`, um sicherzustellen, dass Clients immer auf dem neuesten Planstand arbeiten (Optimistic Locking). Automatische Generierung von `EMPChronologyProvenance` und `EPAActivityProvenance` bei jeder Operation.
- **Frontend**: `Reconciliation.controller.js`, `MedicationList.controller.js`, `MedicationPlan.controller.js` (neue API-Pfade, `X-Requesting-Organization` Header bei allen POST-Calls)
- **Fixtures**: JSON-Fixtures müssen `medicationPlanIdentifier` Felder erhalten
- **Tests**: `LinkLogicTest.java` und `MedicationServiceTest.java` müssen angepasst werden; neuer Test für Versionskonflikt (`MSG_VERSION_AWARE_CONFLICT`)
- **BREAKING**: Die alten Endpunkte `/api/medications/link` (POST/DELETE) entfallen
