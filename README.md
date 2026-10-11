# Terminbuchung – QA-Coding-Challenge (Senior)

## Tests der Abgabe ausführen

Voraussetzungen: JDK 17+, Node.js 22+ und npm. Beide Befehlsblöcke beginnen im Hauptverzeichnis des Repositories.

```text
cd backend
.\mvnw.cmd test                  # alle Backend-Tests, 12 schlagen absichtlich fehl
.\mvnw.cmd test -Dgroups=Defect-4 # ein einzelner Defect
```

```text
cd frontend
npm ci
npx playwright install chromium
npm run test:e2e                 # alle E2E-Tests, 3 schlagen absichtlich fehl
npm run test:e2e:allure          # Tests mit Allure-Report
npm run report:allure:open       # Report öffnen
```

Unter Linux/macOS `./mvnw` statt `.\mvnw.cmd`. Playwright startet Backend und Frontend selbst.
Die Fehlschläge und Annahmen sind in [Abschnitt 9 der Test-Strategie](TESTSTRATEGIE.md#9-gefundene-defects) dokumentiert.

## Aufgabenstellung

Unser Team hat eine kleine Anwendung zur Terminbuchung in einer Arztpraxis entwickelt. Freie Zeitfenster („Slots") können gebucht und wieder storniert werden. Die Anwendung wurde funktional abgenommen, ist aber nie systematisch getestet worden.

Du übernimmst die Rolle des verantwortlichen QA-Experten. Deine Aufgabe besteht aus **drei Teilen**: einer schriftlichen Test-Strategie, deren praktischer Umsetzung und einer kurzen Reflexion zum Einsatz von KI in der QA.

Die bestehende Lösung besteht aus einem **Java-/Spring-Boot-Backend** und einem **React-Frontend (Vite, TypeScript)**. Die Daten werden ausschließlich im Arbeitsspeicher gehalten und gehen bei einem Neustart des Backends verloren.

## Ursprüngliche Kundenanforderung

Diese Spezifikation ist die verbindliche Grundlage. Weicht die Implementierung davon ab, ist die Implementierung falsch – nicht die Spezifikation.

1. Ein Slot kann **genau einmal** gebucht werden. Eine zweite Buchung desselben Slots wird mit `409` abgelehnt.
2. Ein Slot, dessen Beginn in der **Vergangenheit** liegt, kann nicht gebucht werden (`400`).
3. Ein Termin kann **bis spätestens 24 Stunden vor Slot-Beginn** storniert werden. Danach wird die Stornierung mit `409` abgelehnt.
4. Eine Stornierung gibt den Slot wieder frei, sodass er erneut gebucht werden kann.
5. Ein bereits stornierter Termin kann **nicht erneut** storniert werden (`409`).
6. Eine unbekannte Slot- oder Termin-ID führt zu `404`.
7. Pflichtfelder einer Buchung: **Patientenname** (1 bis 100 Zeichen) und **Grund des Besuchs** (höchstens 255 Zeichen). Verletzungen führen zu `400`.

Fehlerantworten haben das Format `{"fehler":"..."}`.

## Deine Aufgabe

### Teil 1 – Test-Strategie (35 %)

Fülle die Abschnitte 1 bis 9 der Datei `TESTSTRATEGIE.md` aus. Das Gerüst gibt die erwarteten Abschnitte vor. Uns interessiert deine fachliche Begründung, nicht die Länge des Dokuments: Welche Risiken siehst du, wie verteilst du den Testaufwand über die Teststufen, welchen Test-Stack wählst du und warum, woran machst du fest, dass genug getestet wurde.

### Teil 2 – Implementierung (50 %)

1. Erstelle für deine Änderungen einen Branch oder Fork dieses Starters.
2. Setze die in Teil 1 beschriebene Strategie um. Welche Werkzeuge du dafür verwendest, entscheidest du selbst – die mitgelieferten Starter-Tests (Playwright) sind nur ein Einstiegspunkt, kein Zwang.
3. Decke Abweichungen zwischen Spezifikation und Implementierung durch **reproduzierbare, fehlschlagende Tests** auf. Ein Test, der eine Abweichung zeigt, ist mehr wert als eine Prosa-Beschreibung.
4. Dokumentiere die gefundenen Abweichungen mit Reproduktionsschritten sowie erwartetem und tatsächlichem Verhalten – im Abschnitt „Gefundene Defects" deiner Strategie. **Fehler zu beheben ist nicht Teil der Aufgabe.**
5. Beschreibe in der README deines Abgabe-Repos oder in `TESTSTRATEGIE.md`, mit welchen Befehlen deine Tests ausgeführt werden.

Es gibt **kein vorgegebenes Zeitbudget**. Entscheide selbst, welcher Umfang angemessen ist – diese Entscheidung ist Teil der Bewertung. Begründe sie kurz im Abschnitt „Was ich mit mehr Zeit zusätzlich getan hätte".

Die beiden mitgelieferten Starter-Tests sind bewusst **grün** und decken keine Abweichung auf.

### Teil 3 – KI in der QA (15 %)

Fülle Abschnitt 10 der Datei `TESTSTRATEGIE.md` aus. Der Einsatz von KI-Werkzeugen bei dieser Aufgabe ist ausdrücklich erlaubt und erwünscht – es gibt keine Punkte dafür, darauf zu verzichten, und keine Abzüge für den Einsatz. Bewertet wird, wie differenziert du über Nutzen, Grenzen und Risiken urteilst und wie offen du deinen eigenen Einsatz reflektierst.

## Lokal starten

Voraussetzungen: **JDK 17** (oder neuer), **Node.js ab Version 22** und npm. Maven wird über den mitgelieferten Wrapper (`mvnw`) verwendet und muss nicht installiert sein.

Backend starten:

```text
cd backend
.\mvnw.cmd spring-boot:run      # Windows
./mvnw spring-boot:run          # Linux / macOS
```

Das Backend lauscht auf `http://localhost:8080`.

In einem zweiten Terminal das Frontend:

```text
cd frontend
npm ci
npm run dev
```

Öffne http://localhost:5173. Vite leitet Anfragen an `/api` an das Backend weiter.

### Hinweis für Firmennetze

Scheitern npm- oder Playwright-Downloads mit `UNABLE_TO_GET_ISSUER_CERT_LOCALLY`, hilft es, die Zertifikate des Betriebssystems zu aktivieren:

```text
$env:NODE_OPTIONS='--use-system-ca'   # PowerShell
export NODE_OPTIONS=--use-system-ca   # bash
```

Scheitern Maven-Downloads mit einem Zertifikatsfehler, hilft unter Windows analog:

```text
$env:MAVEN_OPTS='-Djavax.net.ssl.trustStoreType=Windows-ROOT'
```

## Die API

| Methode | Pfad | Zweck |
|---|---|---|
| `GET` | `/api/slots` | Alle Slots mit Status (`gebucht` true/false) |
| `POST` | `/api/slots` | Zusätzlichen Slot anlegen (Testdaten) |
| `POST` | `/api/appointments` | Slot buchen |
| `GET` | `/api/appointments` | Alle Buchungen samt Status |
| `DELETE` | `/api/appointments/{id}` | Termin stornieren |

Buchung anlegen:

```json
POST /api/appointments
{ "slotId": "slot-5", "patientenname": "Max Muster", "grund": "Kontrolle" }
```

Slot anlegen (Testdaten-Hook, damit Tests unabhängig von den vorbelegten Slots und vom Tagesdatum sind):

```json
POST /api/slots
{ "arzt": "Dr. Beispiel", "beginn": "2026-12-24T09:30:00" }
```

`beginn` ist eine lokale Zeitangabe **ohne Zeitzone** im Format `yyyy-MM-ddTHH:mm:ss`.

Beim Start legt das Backend acht Slots an, deren Zeitpunkte **relativ zum aktuellen Zeitpunkt** liegen – von einigen Stunden in der Vergangenheit bis mehrere Tage in der Zukunft. Die Browseroberfläche verwendet dieselben Endpunkte wie die API.

## Abgabe

Stelle dein Ergebnis als Git-Repository bereit (Branch, Fork oder Archiv). Es sollte enthalten:

- die ausgefüllte `TESTSTRATEGIE.md`,
- deine Tests,
- eine kurze Anleitung, wie die Tests ausgeführt werden.

## Bewertungskriterien

**Test-Strategie (35 %)**

- Nachvollziehbare Risikoanalyse und daraus abgeleitete Priorisierung
- Begründete Verteilung über die Teststufen statt pauschaler „alles E2E"-Ansätze
- Begründete Wahl des Test-Stacks
- Belastbare Exit-Kriterien und Metriken
- Realistische Einschätzung des eigenen Scopes

**Implementierung (50 %)**

- Aussagekraft der Tests: Deckt ein fehlschlagender Test eine echte Abweichung von der Spezifikation auf?
- Reproduzierbarkeit und Unabhängigkeit der Tests voneinander
- Abdeckung über das Offensichtliche hinaus: Grenzwerte, Zustandsübergänge, Nebenläufigkeit
- Qualität der Defect-Dokumentation
- Lesbarkeit und Struktur des Testcodes

**KI in der QA (15 %)**

- Konkretheit: Bezug auf dieses Projekt statt allgemeiner Aussagen über KI
- Differenziertes Risikoverständnis, auch über offensichtliche Punkte hinaus
- Offene und selbstkritische Reflexion des eigenen Einsatzes
