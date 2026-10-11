# Test-Strategie

**Bearbeitet von:** Denis Fedorov
**Datum:** 11.10.2026

---

## 1. Scope und Zielsetzung

Die Spezifikation ist das Orakel: Weicht die Implementierung davon ab, gilt die Implementierung als
fehlerhaft.

Ziele:

1. Nachweisen, ob die Anwendung die Spezifikation erfüllt.
2. Eine Testsuite aufbauen, die zuverlässig und reproduzierbar gegen die Spezifikation läuft und nach
   Behebung der Fehler als Regressionssuite dient.
3. **Risiken aufdecken, die die Spezifikation nicht abdeckt** (z. B. fehlende Zugriffskontrolle), und
   sie als offene Fragen an den Product Owner melden, damit er sie bewerten kann.

Nicht im Scope:

- Datenverlust bei Neustart: laut README gewolltes Verhalten.
- Performance- und Lasttests: keine Anforderung vorhanden. Die gleichzeitige Buchung desselben Slots
  wird trotzdem funktional getestet, weil sie Anforderung 1 betrifft.
- Security (Authentifizierung, Zugriffsschutz): nicht spezifiziert, daher nicht automatisiert getestet.
  Die fehlende Zugriffskontrolle ist dennoch ein hohes Risiko und wird als offene Frage gemeldet.
- Usability und Barrierefreiheit: nicht automatisiert getestet. Barrierefreiheit wird wegen der
  Zielgruppe Patienten explorativ geprüft und als offene Frage gemeldet.
- Browser-Kompatibilität (Tests laufen nur in Chromium), Mobile, Lokalisierung.

---

## 2. Risikoanalyse und Risikobasierung

**Bewertung:** Eintrittswahrscheinlichkeit (W.) × Schaden. Die Wahrscheinlichkeit ist eine
Schätzung, da keine Betriebsdaten vorliegen. Sie steigt, wenn der Code einen fehleranfälligen
Aufbau zeigt.

| Stufe | Eintrittswahrscheinlichkeit | Schaden |
|---|---|---|
| hoch (3) | tritt im normalen Betrieb auf, ohne besondere Eingaben oder Timing | Doppelbuchung, Verlust eines Termins oder Offenlegung von Gesundheitsdaten |
| mittel (2) | braucht eine bestimmte, aber realistische Situation | Funktion eingeschränkt oder falsche Anzeige, Umweg möglich |
| niedrig (1) | nur mit ungewöhnlichen Eingaben oder sehr seltenen Umständen | Ärgernis ohne Datenfehler |

**Priorität:** 1 = Produkt ≥ 6 und Schaden hoch, 2 = Produkt ≥ 4, 3 = Produkt ≤ 3.

| Risiko | Art | Ursache / Auswirkung | W. | Schaden | Priorität |
|---|---|---|---|---|---|
| Doppelbuchung eines Slots | fachlich + technisch | Gleichzeitige Buchungen oder Doppelklick. Zwei Patienten zur selben Zeit beim Arzt | hoch | hoch | 1 |
| Fehlende Zugriffskontrolle (nicht spezifiziert) | fachlich + technisch | Keine Anmeldung. Jeder Patient sieht Namen und Besuchsgründe aller Patienten, kann jeden Termin stornieren und unter beliebigem Namen alle Slots blockieren | hoch | hoch | 1 (Offene Frage) |
| Stornofrist falsch berechnet | fachlich + technisch | Zu späte Stornierung wird angenommen, Termin nicht mehr vergebbar | mittel | hoch | 1 |
| Falsche Zustandsübergänge eines Slots | fachlich + technisch | Ein Slot wechselt zwischen frei und gebucht (frei → gebucht → storniert → wieder frei → neu gebucht). Fehlende Statusprüfung erlaubt unzulässige Wechsel, z. B. erneute Stornierung. Dadurch wird der Slot eines anderen Patienten freigegeben und doppelt vergeben; der Patient verliert seinen Termin, ohne es zu wissen | mittel | hoch | 1 |
| Oberfläche zeigt falschen Zustand | fachlich + technisch | Arzt und Beginn fehlen, stornierter Termin wirkt aktiv. Patient weiß nicht, wann und bei wem sein Termin ist | hoch | mittel | 2 |
| Fehlende Barrierefreiheit (nicht spezifiziert) | fachlich | Patienten mit Screenreader oder Einschränkungen können nicht selbstständig buchen. Rechtliche Anforderungen (z. B. Barrierefreiheitsgesetz) sind zu klären | hoch | mittel | 2 (Offene Frage) |
| Unvollständige Validierung (Name, Grund) | fachlich + technisch | Längengrenze für den Grund fehlt, Sonderfälle beim Namen. Unbrauchbare Datensätze; ein einzelner sehr großer Request kann die Seite für alle Patienten stark beeinträchtigen | hoch | mittel | 2 |
| Falsche Statuscodes, falsches Fehlerformat | technisch | Unbehandelte Ausnahmen. Clients reagieren falsch auf Fehler | mittel | mittel | 2 |
| Vergangene Slots werden angeboten | fachlich | Oberfläche filtert vergangene Slots nicht, Backend lehnt die Buchung ab. Unnötige Fehlermeldung für den Patienten | niedrig | niedrig | 3 |

Daraus folgt: Den meisten Testaufwand bekommen Doppelbuchung, Stornofrist und die
Zustandsübergänge eines Slots. Die fehlende Zugriffskontrolle ist ebenfalls Priorität 1, aber nicht
spezifiziert. Sie wird nicht automatisiert getestet, sondern als dringendste offene Frage an den
Product Owner gemeldet, ebenso wie die Barrierefreiheit.

---

## 3. Teststufen und Verteilung des Aufwands

| Stufe | Tests | Davon Defect-Nachweise | Wofür |
|---|---|---|---|
| Unit (`TerminServiceTest`) | 26 | 8 | Fachregeln, Grenzwerte, Zustandsübergänge, Nebenläufigkeit |
| API (`TermineApiTest`) | 11 | 4 | Statuscodes, Fehlerformat, Nebenläufigkeit über HTTP |
| E2E (`oberflaeche.spec.ts`) | 5 | 3 | Zusammenspiel Oberfläche-Backend, Fehler nur im Frontend |

**Unit:** Hier teste ich Grenzwerte und Zustände. Die Tests sind schnell und brauchen keinen Server.

**API:** Die Spezifikation verlangt bestimmte Statuscodes und ein Fehlerformat. Das sieht man nur über
HTTP, nicht im Unit-Test. Defect 9 kann ich nur hier nachweisen. Die Doppelbuchung teste ich auf Unit-
und API-Ebene, weil sie das höchste Risiko ist.

**E2E:** Hier teste ich nur den Ablauf Buchen-Stornieren, die Anzeige eines Fehlers und die drei
Frontend-Defects. Alles andere ist schon im Backend abgedeckt.

**Hinweis:** In einem echten Projekt schreiben die Entwickler meist die Unit-Tests, und ich würde die
API-Tests als einfache REST-Tests gegen eine Testumgebung schreiben. Hier gibt es beides nicht, deshalb
startet mein API-Test die Anwendung selbst.

---

## 4. Testarten

| Testart | Relevant? | Begründung |
|---|---|---|
| Funktional | ja, sehr hoch | Jede Anforderung beschreibt ein klares erwartetes Verhalten mit Statuscode |
| Grenzwertanalyse | ja, hoch | Die Spezifikation nennt feste Grenzen (1 bis 100 und 255 Zeichen, 24 h, Vergangenheit). Fehler liegen typischerweise genau dort (Defect 4, 6, 7, 8) |
| Zustandsübergänge | ja, hoch | Anforderungen 1, 4 und 5 beschreiben einen Ablauf. Ein falscher Übergang führt zur Doppelbuchung (Defect 2, 3) |
| Nebenläufigkeit | ja, hoch | Anforderung 1 muss auch gelten, wenn mehrere Patienten gleichzeitig buchen (Defect 1) |
| Security | in einem echten Projekt: sehr hoch; hier: teilweise | Nicht spezifiziert, aber Gesundheitsdaten von Patienten. Manuell über die API geprüft; Bibliotheken mit `npm audit` (Frontend) und per Abgleich der Maven-Abhängigkeiten mit der OSV-Datenbank (osv.dev, Backend) geprüft. Befunde als offene Fragen in Abschnitt 9 |
| Performance | nein | Keine Anforderung, kleine Anwendung mit Daten im Arbeitsspeicher |
| Usability und Barrierefreiheit | teilweise (Barrierefreiheit in einem echten Projekt hoch: gesetzliche Vorgaben, Zielgruppe Patienten) | Nicht spezifiziert. Nur explorativ geprüft; Befunde in Abschnitt 9 |

---

## 5. Wahl des Test-Stacks

**Tech-Stack:**

- **Unit-Tests:** Java, JUnit 5, AssertJ
- **API-Tests:** Java, JUnit 5, AssertJ, zusätzlich Spring Boot Test (startet die echte Anwendung
  und sendet HTTP-Requests)
- **E2E-Tests:** TypeScript, Playwright, Page Object
- **Reporting:** Allure, Playwright-HTML-Report
- **Exploratives Testen:** manuell, Playwright MCP

Begründung: JUnit 5, AssertJ und Spring Boot Test sind über `spring-boot-starter-test` bereits
vorhanden, Playwright ist im Starter enthalten. Die Backend-Tests sind in derselben Sprache wie das
Backend, die E2E-Tests in derselben Sprache wie das Frontend. Es gibt keine zusätzlichen Werkzeuge
und keine eigene Testumgebung.

**Verworfen:**

- **Java + Selenium:** mehr Aufwand (eigene Waits, Treiberverwaltung) und anfälliger für Flakiness.
  Playwright wartet automatisch und ist im Starter bereits vorhanden.
- **TestNG:** JUnit 5 ist schon vorhanden und bietet alles Nötige (`@Nested`, `@ParameterizedTest`,
  `@Tag`). Der Hauptvorteil von TestNG, Abhängigkeiten zwischen Tests, widerspricht der
  Testunabhängigkeit.
- **REST Assured:** lesbarer, aber eine zusätzliche Abhängigkeit für elf Tests.
- **Postman + Newman:** sinnvoll für Black-Box-Tests gegen eine deployte Testumgebung. Hier gibt es
  keine, das Backend müsste separat gestartet werden. Außerdem sendet Newman Requests nacheinander;
  der Nebenläufigkeitstest (Defect 1) wäre damit nicht möglich.
- **Cucumber (BDD):** Die Spezifikation ist kurz und präzise. Eine zusätzliche Gherkin-Schicht bringt
  hier keinen Mehrwert, nur mehr Code.

---

## 6. Testdaten und Testumgebung

**Testdaten:** Kein Test nutzt die vorbelegten Slots, weil ihre Zeitpunkte vom Start des Backends
abhängen. Jeder Test legt eigene Slots an, im Service (Unit) oder über `POST /api/slots` (API, E2E).

**Zeitabhängigkeiten:** Slots werden immer relativ zur aktuellen Zeit angelegt, nie mit festem Datum.
Zeitabhängige Tests halten mindestens eine Minute Abstand zu den Grenzen. Exakte Grenztests sind mit
unkontrollierter Systemzeit nicht zuverlässig. Statisches Mocking oder eine injizierte `Clock` würden
sie ermöglichen; darauf habe ich hier zugunsten geringerer Komplexität verzichtet.

**Isolation:** Daten bleiben im Arbeitsspeicher, solange das Backend läuft. Deshalb erwartet kein
Test ein leeres System, und jeder prüft nur seine eigenen Daten (eigene IDs, eindeutige Namen). Kein
Test hängt von einem anderen ab. Unit-Tests bekommen jeweils einen neuen Service; API- und E2E-Tests
teilen sich einen Server, E2E-Tests laufen nacheinander.

---

## 7. Automatisierungsgrad und CI-Einbindung

**Automatisiert:** Alle sieben Anforderungen und das Fehlerformat sind durch Unit-, API- und E2E-Tests
abgedeckt. Das bedeutet Testabdeckung je Anforderung, keine vollständige Prüfung aller möglichen Fälle.

**Manuell/explorativ:** Usability, Barrierefreiheit, Security-Sondierung und die offenen Fragen, weil
es dafür kein Orakel in der Spezifikation gibt.

**Pipeline (Vorschlag):**

| Wann | Was |
|---|---|
| Jeder Pull Request | Unit- und API-Tests, Lint (Backend etwa 20–25 s inklusive Serverstart) |
| Nächtlich | E2E-Tests mit Allure-Report (etwa 45 s inklusive Start von Backend und Frontend) |
| Vor Release | Manuelle explorative Sitzung, KI-gestützte explorative Sitzung mit Playwright MCP, Security-Sondierung, Klärung der offenen Fragen |

### Tests ausführen

Voraussetzungen wie in der README (JDK 17+, Node 22+).

```text
cd backend
.\mvnw.cmd test                       # alle Backend-Tests, 12 schlagen absichtlich fehl
.\mvnw.cmd test -Dgroups=Defect-4     # ein einzelner Defect
```

```text
cd frontend
npm ci
npx playwright install chromium
npm run test:e2e                 # alle E2E-Tests, 3 schlagen absichtlich fehl
npm run test:e2e:allure          # Tests mit Allure-Report
npm run report:allure:open       # Report oeffnen
```

Unter Linux/macOS `./mvnw` statt `.\mvnw.cmd`. Playwright startet Backend und Frontend selbst.

---

## 8. Exit-Kriterien und Metriken

Ausreichend getestet ist, wenn:

1. jede Anforderung und das Fehlerformat mindestens einen positiven und einen negativen Test hat, auf
   der Stufe, auf der sie nachweisbar sind;
2. jedes Risiko der Priorität 1 aus Abschnitt 2 durch Tests abgedeckt ist, soweit es spezifiziert ist;
3. die Suite in drei aufeinanderfolgenden Läufen dasselbe Ergebnis liefert: alle Tests grün außer den
   dokumentierten Defect-Nachweisen (erste Stabilitätsprüfung, kein Beweis für Flakiness-Freiheit);
4. die offenen Fragen beantwortet sind.

| Metrik | Stand | Aussagekraft |
|---|---|---|
| Anforderungsabdeckung | 7 von 7 plus Fehlerformat | Hoch, solange man auch prüft, *wie* getestet wird. Eine Anforderung "abgedeckt" durch einen einzigen Happy-Path-Test wäre wertlos |
| Defects nach Schweregrad | 2 kritisch, 4 hoch, 3 mittel, 3 niedrig | Hoch für die Freigabeentscheidung |
| Stabilität | Suite mehrfach mit identischem Ergebnis ausgeführt | Hoch, weil zeit- und threadabhängige Tests die üblichen Kandidaten für Flakiness sind |
| Laufzeit | siehe Abschnitt 7 | Relevant für die CI-Einbindung |
| Codeabdeckung (Unit-Tests) | nicht erhoben | Mittel. Wird üblicherweise von der Entwicklung erhoben |

---

## 9. Gefundene Defects

| # | Titel | Anforderung | Schweregrad | Nachweis (Test) |
|---|---|---|---|---|
| 1 | Gleichzeitige Buchungen führen zur Doppelbuchung | 1 | kritisch | Unit `Buchung.gleichzeitigeBuchungenErgebenGenauEinenTermin`, API `paralleleBuchungenLiefernGenauEin201` |
| 2 | Stornierter Termin lässt sich erneut stornieren (200 statt 409) | 5 | hoch | Unit `Stornierung.zweiteStornierungWirdAbgelehnt`, API `zweiteStornierungLiefert409` |
| 3 | Erneute Stornierung gibt den Slot eines anderen Patienten frei | 1 (Folge von Defect 2) | kritisch | Unit `Stornierung.erneuteStornierungGibtFremdenSlotNichtFrei` |
| 4 | Stornierung bis 30 Minuten innerhalb der 24-h-Frist möglich | 3 | mittel | Unit `Stornierung.stornierungKnappInnerhalbDerFristWirdAbgelehnt` |
| 5 | Unbekannte Slot-ID liefert 500 statt 404 | 6, Fehlerformat | mittel | Unit `Buchung.unbekannteSlotIdLiefert404`, API `unbekannteSlotIdLiefert404` |
| 6 | Grund länger als 255 Zeichen wird angenommen | 7 | hoch | Unit `Validierung.grundMit256ZeichenWirdAbgelehnt` |
| 7 | Patientenname aus Unicode-Leerzeichen wird angenommen | 7 (Annahme, siehe unten) | niedrig | Unit `Validierung.nameAusGeschuetztemLeerzeichenWirdAbgelehnt` |
| 8 | 100 Zeichen außerhalb der BMP werden als zu lang abgelehnt | 7 (Zeichenbegriff zu klären) | niedrig | Unit `Validierung.nameMit100JapanischenZeichenWirdAngenommen` |
| 9 | Framework-Fehler nicht im Format `{"fehler":"..."}` | Fehlerformat | mittel | API `ungueltigesJsonLiefertFehlerformat` |
| 10 | "Meine Termine" zeigt Arzt und Beginn nicht an | keine (Frontend/API) | hoch | E2E "Defect 10" |
| 11 | Stornierter Termin ist nicht als storniert erkennbar | keine (Frontend) | hoch | E2E "Defect 11" |
| 12 | Vergangene Slots werden als frei angeboten | keine (Frontend) | niedrig | E2E "Defect 12" |

### Defect 1: Gleichzeitige Buchungen führen zur Doppelbuchung

- **Schritte:** Slot anlegen, zwei `POST /api/appointments` auf denselben Slot gleichzeitig senden.
- **Erwartet:** ein 201, ein 409, ein Termin.
- **Tatsächlich:** zwei 201, zwei aktive Termine auf einem Slot.
- **Testaufbau:** Ein Executor mit zwei Threads führt zwei Buchungsaufgaben für denselben Slot aus.
  Der Test wartet mit einem Timeout auf beide Ergebnisse. Die Überlappung hängt vom Scheduling ab;
  ein grüner Lauf allein beweist daher keine Thread-Sicherheit.
- **Schweregrad kritisch:** zwei Patienten zur selben Zeit beim Arzt.

### Defect 2: Stornierter Termin lässt sich erneut stornieren

- **Schritte:** Termin buchen (Slot > 24 h entfernt), `DELETE /api/appointments/{id}`, denselben Request
  wiederholen.
- **Erwartet:** zweiter Request 409.
- **Tatsächlich:** 200, Status `STORNIERT`.
- **Schweregrad hoch:** direkte Verletzung von Anforderung 5 und Voraussetzung für Defect 3.

### Defect 3: Erneute Stornierung gibt den Slot eines anderen Patienten frei

- **Schritte:** Slot > 24 h entfernt. A bucht, A storniert, B bucht, A storniert erneut, C bucht.
- **Erwartet:** A erhält 409, der Slot bleibt B, C erhält 409.
- **Tatsächlich:** A erhält 200, `GET /api/slots` zeigt den Slot als frei, C erhält 201. B und C haben
  aktive Termine auf demselben Slot.
- **Schweregrad kritisch:** Doppelbuchung; ein anderer Patient verliert seinen Termin, ohne es zu wissen.

### Defect 4: Stornierung bis 30 Minuten innerhalb der 24-h-Frist möglich

- **Schritte:** Slot über `POST /api/slots` mit Beginn in 23 h 50 min anlegen, buchen, stornieren.
- **Erwartet:** 409.
- **Tatsächlich:** 200, Slot frei.
- **Schweregrad mittel:** Die Frist ist um bis zu 30 Minuten zu kurz; die Praxis verliert kurzfristig
  einen Termin, den sie nicht mehr vergeben kann.

### Defect 5: Unbekannte Slot-ID liefert 500 statt 404

- **Schritte:** `POST /api/appointments` mit `"slotId":"gibt-es-nicht"` und gültigem Namen und Grund.
- **Erwartet:** 404 mit `{"fehler":"..."}`.
- **Tatsächlich:** 500 mit Spring-Body `{"timestamp","status","error","path"}`. Zwei Spezifikationspunkte
  verletzt (Anforderung 6 und Fehlerformat). Die Oberfläche zeigt "Anfrage fehlgeschlagen (500)".
- **Schweregrad mittel:** Ein falscher Statuscode bricht Clients, die auf 404 reagieren.

### Defect 6: Grund länger als 255 Zeichen wird angenommen

- **Schritte:** Buchung mit einem Grund von 256 Zeichen.
- **Erwartet:** 400.
- **Tatsächlich:** 201.
- **Schweregrad hoch:** Ohne jede Grenze wird auch ein Grund von 5 MB angenommen. Danach
  lädt jeder Seitenaufruf 5 MB, die Seite wird 26 Mio. Pixel breit, und die Stornieren-Buttons sind per
  Maus oder Touch nicht mehr erreichbar (nur noch per Tastatur). Ein einzelner Request macht das
  Stornieren für praktisch alle Patienten unmöglich (`docs/security-ui-dos-grosse-eingaben.png`).

### Defect 7: Patientenname aus Unicode-Leerzeichen wird angenommen

- **Schritte:** Buchung mit Patientenname `"\u00A0"` (geschütztes Leerzeichen).
- **Erwartet:** 400.
- **Tatsächlich:** 201, in der Liste erscheint ein leerer Name.
- **Annahme:** Wörtlich erfüllt ein Zeichen die Regel "1 bis 100 Zeichen". Ich lese "Pflichtfeld" als
  "sichtbarer Inhalt", so wie es die Implementierung für `" "` selbst tut. Der Defect gilt unter dieser
  Annahme; siehe offene Fragen.
- **Schweregrad niedrig.**

### Defect 8: 100 Zeichen außerhalb der BMP werden als zu lang abgelehnt

- **Schritte:** Buchung mit einem Namen aus 100 × 𠮷.
- **Erwartet:** 201 (100 Zeichen).
- **Tatsächlich:** 400 "darf hoechstens 100 Zeichen lang sein". Bereits ab 51 solcher Zeichen.
- **Schweregrad niedrig:** wörtliche Verletzung, betrifft aber nur sehr lange Namen mit seltenen Zeichen.

### Defect 9: Framework-Fehler nicht im Format `{"fehler":"..."}`

- **Schritte:** `POST /api/appointments` mit Body `{kein json` und `Content-Type: application/json`.
- **Erwartet:** 400 mit `{"fehler":"..."}`.
- **Tatsächlich:** 400 mit `{"timestamp","status","error","path"}`.
- Ebenfalls manuell beobachtet bei: leerem Body, `[]` statt Objekt, falschem Content-Type (415),
  falscher Methode (405), unbekanntem Pfad (404), ungültigem Datum bei `POST /api/slots`.
- **Schweregrad mittel:** Clients, die `fehler` auslesen, zeigen nichts an.

### Defect 10: "Meine Termine" zeigt Arzt und Beginn nicht an

- **Schritte:** Termin buchen, Seite öffnen.
- **Erwartet:** Arzt und Beginn des gebuchten Slots.
- **Tatsächlich:** beide Spalten leer.
- Keine nummerierte Anforderung verletzt; Widerspruch zwischen Frontend und API.
- **Schweregrad hoch:** Der Patient sieht nirgends in der Oberfläche, wann und bei wem sein Termin ist,
  und kann ihn verpassen.

### Defect 11: Stornierter Termin ist nicht als storniert erkennbar

- **Schritte:** Termin über die Oberfläche stornieren.
- **Erwartet:** als storniert gekennzeichnet, kein Button "Stornieren".
- **Tatsächlich:** sieht aus wie ein aktiver Termin, der Button bleibt.
- Keine nummerierte Anforderung; die API liefert den Status ("samt Status").
- **Schweregrad hoch:** Der Patient hält einen stornierten Termin für aktiv und erscheint womöglich
  trotzdem in der Praxis.

### Defect 12: Vergangene Slots werden als frei angeboten

- **Schritte:** Slot über `POST /api/slots` mit Beginn vor einer Stunde anlegen, Seite öffnen.
- **Erwartet:** nicht angeboten.
- **Tatsächlich:** angeboten; die Buchung scheitert mit 400, der Slot bleibt danach in der Liste.
- Anforderung 2 selbst hält, das Backend lehnt ab.
- **Schweregrad niedrig:** ärgerlich, aber ohne Datenfehler.

### Offene Fragen außerhalb der Spezifikation

| Befund | Empfehlung |
|---|---|
| **Keine Zugriffskontrolle:** Jeder sieht Namen und Besuchsgründe aller Patienten (`GET /api/appointments`), kann jeden Termin stornieren und unter beliebigem Namen alle Slots blockieren | Anmeldung, Beschränkung auf eigene Termine, Begrenzung der aktiven Termine pro Patient |
| **Veraltete Bibliotheken im Backend:** Spring Boot 3.3.5 bringt Tomcat 10.1.31, Jackson 2.17.2 und Spring Framework 6.1.14 mit bekannten Schwachstellen mit (OSV-Abgleich von 68 Abhängigkeiten, Stand 11.10.2026: 15 betroffen, 8 kritisch, 30 hoch). Die meisten betreffen hier nicht genutzte Funktionen (z. B. DIGEST-/FORM-Anmeldung, WebDAV, HTTP/2, `XsltView`). Jackson 2.17.2 ist von CVE-2026-89407 betroffen. Ob die Schwachstelle über die Endpunkte dieser Anwendung ausnutzbar ist, wurde nicht nachgewiesen. Frontend: `npm audit` ohne Befund | Spring Boot auf eine aktuelle, unterstützte Version heben (bringt korrigierte Versionen von Tomcat, Jackson und Spring mit); Abhängigkeitsscan (z. B. OWASP Dependency-Check oder OSV-Scanner) in die Pipeline aufnehmen |
| **Fehlende Security-Header:** kein `Cache-Control` auf Gesundheitsdaten, keine Schutz-Header | Header ergänzen |
| **Barrierefreiheit:** Acht Buttons heißen "Auswaehlen", zwei Spaltenköpfe sind leer, nach der Auswahl gibt es keinen Fokuswechsel und keine Markierung. Die Bedienung mit Screenreader ist dadurch erschwert | Eindeutige Button-Namen (z. B. "Termin bei Dr. Bauer am … auswählen"), beschriftete Spaltenköpfe, sichtbare Auswahl und Fokusführung |
| **Layout:** Lange Eingaben ohne Leerzeichen brechen die Tabelle, auch ein gültiger Grund mit 255 Zeichen | Zeilenumbruch in Zellen, `maxlength` an den Eingabefeldern |
| **Bedienung:** Das Feld "Termin-ID" enthält eine Slot-ID, Stornieren erfolgt ohne Rückfrage, Meldungen erscheinen am Seitenende | Feld umbenennen, Rückfrage vor dem Stornieren, Meldungen in Sichtweite |

### Offene Fragen zur Spezifikation

- **Unsichtbare Zeichen im Namen:** Gilt ein Name aus einem geschützten Leerzeichen (`\u00A0`) als
  ausgefüllt? Heute wird er angenommen (Defect 7).
- **Zeichenbegriff:** Zählt die Namensgrenze UTF-16-Codeeinheiten, Unicode-Codepunkte oder sichtbare
  Graphemcluster? Defect 8 setzt Unicode-Codepunkte voraus.
- **Fehlende Slot-ID:** Soll eine Buchung ohne `slotId` mit 400 oder 404 abgelehnt werden? Heute 400;
  Anforderung 7 nennt nur Name und Grund als Pflichtfelder.
- **Testdaten-Endpunkt:** Ist `POST /api/slots` nur für Testumgebungen gedacht? Im Betrieb könnte
  sonst jeder erfundene oder doppelte Slots anlegen, die Patienten dann buchen.

---

## 10. KI in der QA

### 10.1 Einsatzpotenzial in diesem Projekt

**1. Testfälle aus der Spezifikation ableiten**
- **Input:** nur der Abschnitt "Ursprüngliche Kundenanforderung" aus der README, ohne Code.
- **Beispiel-Anfrage:** "Leite für Anforderung 7 Grenzwerte und Äquivalenzklassen ab und nenne für jeden
  Fall den laut Spezifikation erwarteten Statuscode."
- **Ergebnis:** Neben den offensichtlichen Grenzen (0, 1, 100, 101 Zeichen) kamen Fälle, die ich selbst
  nicht ausprobiert hätte: ein Name aus einem geschützten Leerzeichen und ein Name aus 100 seltenen
  Zeichen wie 𠮷. Beide wurden zu klärungsbedürftigen Befunden (7 und 8). Da die Spezifikation kurz ist,
  konnte ich jeden Vorschlag in wenigen Minuten daran prüfen.

**2. Dokumentation gegen die Tests prüfen**
- **Input:** `TESTSTRATEGIE.md`, der Testcode und die Ausgaben der Testläufe.
- **Ergebnis:** Ich habe parallel an Tests und Dokumentation gearbeitet und nach Änderungen an einigen
  Tests vergessen, das Dokument nachzuziehen. Die KI hat die dokumentierten Befehle ausgeführt und die
  Ergebnisse mit den Angaben im Dokument verglichen. So habe ich gesehen, wo beides auseinanderlief.

**3. Explorative Sitzung mit Playwright MCP**
- **Input:** die laufende Anwendung und eine kurze Vorgabe.
- **Beispiel-Anfrage:** "Das ist eine kleine App zur Buchung von Arztterminen. Buche und storniere als
  Patient Termine, probiere ungewöhnliche Eingaben aus und melde alles, was falsch aussieht."
- **Ergebnis:** keine neue Abweichung von der Spezifikation, aber ein Layoutbruch, den keiner meiner
  Tests prüft.
- **Einschätzung:** Hier lohnt sich das aus meiner Sicht nicht. Nach meiner Erfahrung bringt KI-gestütztes
  exploratives Testen vor allem dann etwas, wenn eine Anwendung viele Seiten und Abläufe hat, die man
  allein kaum vollständig durchgehen kann. Diese Anwendung besteht aus einer Seite mit zwei Abläufen;
  das gehe ich manuell schneller und gründlicher durch.

**4. Bestehende Code-Konventionen einhalten**
- **Input:** der Produktivcode und mein Testcode. Das Projekt verwendet deutsche Bezeichner
  (`TerminService`, `buchen`, `FachlicheAusnahme`) und schreibt Umlaute im Code als `ae`/`oe`/`ue`
  (`pruefeAnfrage`, `hoechstens`). Code auf Deutsch zu schreiben ist für mich ungewohnt.
- **Beispiel-Anfrage:** "Vergleiche meinen Testcode mit den Konventionen des bestehenden Codes: Sprache
  der Bezeichner, Schreibweise der Umlaute, Benennung von Klassen, Methoden und Variablen. Nenne jede
  Abweichung mit Datei und Zeile und schlage einen Namen im bestehenden Stil vor."
- **Ergebnis:** Meine Tests folgen jetzt demselben Stil (`buchenParallel`, `TerminbuchungSeite`,
  `erwarteFehler`) und lesen sich für die Entwickler wie der Rest des Projekts.

**5. Allure-Report einrichten**
- **Input:** die Playwright-Konfiguration, die E2E-Tests mit ihren Tags (`@anf-1`, `@Defect-10`, …) und
  mein Ziel: ein Report, gegliedert nach Anforderung und Defect.
- **Beispiel-Anfrage:** "Richte Allure für die Playwright-Tests ein. Gliedere die Tests nach Anforderung,
  zeige Defect-Nachweise getrennt von Regressionstests und hänge bei Fehlschlag Screenshot und Trace an."
- **Ergebnis:** die Reporter-Konfiguration, die Hilfsdatei `allure-labels.ts` und die npm-Skripte. Das ist
  überwiegend Konfiguration, hier hat mir die KI Zeit gespart; ich habe nur geprüft, ob die Gliederung im
  Report stimmt.

**Bewusst ohne KI:**
- **Sensible Daten:** Echte Patientendaten dürfen nie an einen externen KI-Dienst gehen. Hier nicht
  relevant, in einem echten Projekt aber zwingend.
- **Spezifikation hinterfragen:** Ob die Anforderungen richtig und vollständig sind, kläre ich mit dem
  Product Owner, nicht mit der KI.
- **Erwartete Ergebnisse:** Was als korrekt gilt, ergibt sich aus der Spezifikation.
- **Schweregrad:** Er hängt davon ab, was ein Defect für Patienten und die Praxis bedeutet. Das
  entscheide ich.

### 10.2 Risiken

**Falscher Input, falsche Schlüsse.** Die KI nimmt die Spezifikation und den Kontext als gegeben. Die
Spezifikation selbst kann aber falsch sein, und dann baut jede Schlussfolgerung darauf auf.
Gegenmaßnahme: die Spezifikation mit einer Person aus dem Fachbereich prüfen.

**Überproduktion und grüne Tests.** Für die 24-h-Frist hat die KI mehrere Tests aus derselben
Äquivalenzklasse vorgeschlagen (22 h, 23 h) und neigte zu Assertions, die beschreiben, was der Code tut,
statt was die Spezifikation verlangt. Gegenmaßnahme: ein Test pro Äquivalenzklasse plus die Grenzen,
erwartete Ergebnisse nur aus der Spezifikation ableiten.

**Kosten und Nutzen.** Einrichtung, Tokens und vor allem die Prüfung der Ergebnisse kosten Zeit. Die
explorative Sitzung mit Playwright MCP war für eine einzelne Seite mit zwei Abläufen zu viel Aufwand.
Gegenmaßnahme: KI nur dort einsetzen, wo der Nutzen die Kosten rechtfertigt.

**Bestätigungsfehler.** Die explorative Sitzung lief, nachdem die Defects bekannt waren, und hat sie
vor allem bestätigt. Gegenmaßnahme: Exploration nur mit der Spezifikation beginnen.

**Multitasking und übersehene Details im Review.** In dieser kleinen Aufgabe liefen höchstens zwei Dinge
parallel, etwa die Playwright-MCP-Sitzung, während ich etwas an den Tests korrigiert habe. In echten
Projekten arbeiten mehrere Agenten gleichzeitig, und deren Ergebnisse zusätzlich zur eigenen Arbeit zu
prüfen, macht es leicht, Dinge zu übersehen. Eine allgemeine Gegenmaßnahme außer "ein Mensch prüft
alles" habe ich nicht, und die skaliert schlecht. Darüber würde ich gern sprechen.

### 10.3 Eigener Einsatz bei dieser Aufgabe

Ich habe OpenCode mit verschiedenen Modellen genutzt, je nach Aufgabe: für Code-Reviews, die Vorbereitung
der Tests und die Prüfung von Rechtschreibung und Stimmigkeit der Dokumentation. Für die explorative
Sitzung habe ich Playwright MCP verwendet.

**Gut funktioniert:** alles mit klarem Umfang und konkreter Aufgabe, z. B. die Einrichtung des
Allure-Reports oder das Erzeugen des Page Objects für die E2E-Tests.

**Verworfen oder nicht gut:**
- **Playwright MCP:** Für diese kleine Anwendung hat sich die explorative Sitzung nicht gelohnt
  (siehe 10.1 und 10.2).
- **KI-Slop:** Der erzeugte Code hatte zu viele Kommentare, die ein Mensch so nicht schreiben würde. Ich
  habe sie mit einem eigenen Review-Schritt entfernt, der gezielt danach sucht.
- **Agenten-Setup aus `npx playwright init-agents`:** Für diesen Umfang war es mehr Konfiguration als
  Nutzen, deshalb habe ich es wieder entfernt.
- **Architektur:** Anfangs nutzten die erzeugten E2E-Tests kein Page Object und wiederholten die Locators
  in jedem Test. Ich habe sie in das Page Object `TerminbuchungSeite` verschoben.
- **Falsche Annahme über die Nutzer:** Ein erzeugter Test ging davon aus, dass sich die Anwendung an Ärzte
  richtet. Die Oberfläche ("Meine Termine", Eingabe des eigenen Namens) und die 24-h-Stornofrist zeigen,
  dass sie für Patienten gedacht ist.
- **REST Assured:** Die KI hat es erst verworfen, dann empfohlen. Ich bin bei `TestRestTemplate`
  geblieben, weil eine weitere Abhängigkeit für elf Tests den Gewinn nicht trägt.

---

## 11. Was ich mit mehr Zeit zusätzlich getan hätte

Die nächsten Schritte:

1. **CI-Pipeline aufsetzen** wie in Abschnitt 7: Unit- und API-Tests bei jedem Pull Request, E2E-Tests
   nächtlich, dazu ein Abhängigkeitsscan (z. B. OSV-Scanner).
2. **Self-Healing für UI-Tests in einem realen Projekt.** Bei vielen E2E-Tests und häufigen Änderungen an
   der Oberfläche spart es Wartung, wenn ein Werkzeug geänderte Locators selbst anpasst. Es darf aber nur
   Locators reparieren, nie erwartete Ergebnisse, sonst werden Defect-Nachweise still grün.
3. **Vertragstests zwischen Frontend und API** (z. B. OpenAPI-Schema oder Pact). Sie hätten Defect 10
   schon beim Build gefunden. Für diese kleine Anwendung mit einem Team war mir das zu viel Aufwand; bei
   getrennten Frontend- und Backend-Teams lohnt es sich.
4. **Wiederverwendbare Agenten für die weitere Arbeit an der Codebasis**, z. B. "Test schreiben" (nach den
   Konventionen des Projekts) oder "Fehlschlag untersuchen" (Fehlermeldung, Trace und Code auswerten und
   zwischen Defect, Testfehler und Umgebungsproblem unterscheiden).
5. **Videos der E2E-Tests im Allure-Report**, damit ein Fehlschlag ohne erneuten Lauf nachvollziehbar
   ist.
