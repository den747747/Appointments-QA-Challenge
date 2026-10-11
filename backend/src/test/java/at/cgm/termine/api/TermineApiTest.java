package at.cgm.termine.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TermineApiTest {

    private static final DateTimeFormatter LOKALE_ZEIT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    @Autowired
    private TestRestTemplate http;

    private String neuerSlotInStunden(long stunden) {
        String beginn = LocalDateTime.now().plusHours(stunden).format(LOKALE_ZEIT);
        ResponseEntity<JsonNode> antwort = http.postForEntity("/api/slots",
                Map.of("arzt", "Dr. API-Test", "beginn", beginn), JsonNode.class);
        assertThat(antwort.getStatusCode().value()).isEqualTo(201);
        return antwort.getBody().get("id").asText();
    }

    private String zukuenftigerSlot() {
        return neuerSlotInStunden(72);
    }

    private ResponseEntity<JsonNode> buchen(String slotId, String name, String grund) {
        return http.postForEntity("/api/appointments",
                Map.of("slotId", slotId, "patientenname", name, "grund", grund), JsonNode.class);
    }

    private ResponseEntity<JsonNode> buchen(String slotId) {
        return buchen(slotId, "Max Muster", "Kontrolle");
    }

    private String buchenUndId(String slotId, String name) {
        ResponseEntity<JsonNode> antwort = buchen(slotId, name, "Kontrolle");
        assertThat(antwort.getStatusCode().value()).isEqualTo(201);
        return antwort.getBody().get("id").asText();
    }

    private ResponseEntity<JsonNode> stornieren(String terminId) {
        return http.exchange("/api/appointments/" + terminId, HttpMethod.DELETE, null, JsonNode.class);
    }

    private JsonNode eintrag(String pfad, String id) {
        for (JsonNode e : http.getForObject(pfad, JsonNode.class)) {
            if (e.get("id").asText().equals(id)) {
                return e;
            }
        }
        throw new AssertionError(id + " fehlt in GET " + pfad);
    }

    private static void erwarteFehler(ResponseEntity<JsonNode> antwort, int status) {
        assertThat(antwort.getStatusCode().value()).as("Body: %s", antwort.getBody()).isEqualTo(status);
        assertThat(antwort.getBody()).as("Fehler ohne Body").isNotNull();
        assertThat(antwort.getBody().path("fehler").asText()).as("Fehlerformat, Body: %s", antwort.getBody())
                .isNotBlank();
    }

    private List<Integer> buchenParallel(String slotId) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Integer>> ergebnisse = pool.invokeAll(List.of(
                    () -> buchen(slotId, "Patient A", "Kontrolle").getStatusCode().value(),
                    () -> buchen(slotId, "Patient B", "Kontrolle").getStatusCode().value()),
                    15, TimeUnit.SECONDS);
            return List.of(ergebnisse.get(0).get(), ergebnisse.get(1).get());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("Buchung: 201, Termin im Body, Slot als gebucht gelistet (Anforderung 1)")
    void buchungLiefert201MitTermin() {
        String slotId = zukuenftigerSlot();

        ResponseEntity<JsonNode> antwort = buchen(slotId, "Denis Fedorov", "Impfung");

        assertThat(antwort.getStatusCode().value()).isEqualTo(201);
        JsonNode termin = antwort.getBody();
        assertThat(termin.get("id").asText()).isNotBlank();
        assertThat(termin.get("slotId").asText()).isEqualTo(slotId);
        assertThat(termin.get("patientenname").asText()).isEqualTo("Denis Fedorov");
        assertThat(termin.get("grund").asText()).isEqualTo("Impfung");
        assertThat(termin.get("status").asText()).isEqualTo("GEBUCHT");
        assertThat(eintrag("/api/slots", slotId).get("gebucht").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("zweite Buchung desselben Slots: 409, erster Termin unveraendert, Slot belegt (Anforderung 1)")
    void zweiteBuchungLiefert409() {
        String slotId = zukuenftigerSlot();
        String terminId = buchenUndId(slotId, "Erster");
        JsonNode ersterTermin = eintrag("/api/appointments", terminId);
        assertThat(ersterTermin.get("status").asText()).isEqualTo("GEBUCHT");

        erwarteFehler(buchen(slotId, "Zweiter", "Kontrolle"), 409);

        assertThat(http.getForObject("/api/appointments", JsonNode[].class))
                .filteredOn(termin -> termin.path("slotId").asText().equals(slotId))
                .as("Termine des Slots nach abgelehnter Zweitbuchung")
                .containsExactly(ersterTermin);
        assertThat(eintrag("/api/slots", slotId).get("gebucht").asBoolean()).isTrue();
    }

    @Tag("Defect-1")
    @Test
    @DisplayName("Defect 1: zwei parallele Buchungen desselben Slots: ein 201, ein 409 (Anforderung 1)")
    void paralleleBuchungenLiefernGenauEin201() throws Exception {
        List<Integer> status = buchenParallel(zukuenftigerSlot());

        assertThat(status).containsExactlyInAnyOrder(201, 409);
    }

    @Test
    @DisplayName("Slot vor 1 Stunde: 400 (Anforderung 2)")
    void vergangenerSlotLiefert400() {
        erwarteFehler(buchen(neuerSlotInStunden(-1)), 400);
    }

    @Test
    @DisplayName("Stornierung 2 h vor Beginn: 409, Termin bleibt gebucht (Anforderung 3)")
    void stornierungInnerhalbDerFristLiefert409() {
        String terminId = buchenUndId(neuerSlotInStunden(2), "Kurzfristig");

        erwarteFehler(stornieren(terminId), 409);
        assertThat(eintrag("/api/appointments", terminId).get("status").asText()).isEqualTo("GEBUCHT");
    }

    @Test
    @DisplayName("Buchen, Stornieren (200, STORNIERT), Slot frei, erneut Buchen (Anforderung 4)")
    void stornierungGibtSlotFrei() {
        String slotId = zukuenftigerSlot();
        String terminId = buchenUndId(slotId, "Patient A");

        ResponseEntity<JsonNode> storno = stornieren(terminId);

        assertThat(storno.getStatusCode().value()).isEqualTo(200);
        assertThat(storno.getBody().get("status").asText()).isEqualTo("STORNIERT");
        assertThat(eintrag("/api/slots", slotId).get("gebucht").asBoolean()).isFalse();
        assertThat(buchen(slotId, "Patient B", "Kontrolle").getStatusCode().value()).isEqualTo(201);
    }


    @Tag("Defect-2")
    @Test
    @DisplayName("Defect 2: zweite Stornierung desselben Termins: 409 (Anforderung 5)")
    void zweiteStornierungLiefert409() {
        String terminId = buchenUndId(zukuenftigerSlot(), "Patient A");
        assertThat(stornieren(terminId).getStatusCode().value()).isEqualTo(200);

        erwarteFehler(stornieren(terminId), 409);
    }

    @Test
    @DisplayName("unbekannte Termin-ID: 404 (Anforderung 6)")
    void unbekannteTerminIdLiefert404() {
        erwarteFehler(stornieren("gibt-es-nicht"), 404);
    }

    @Tag("Defect-5")
    @Test
    @DisplayName("Defect 5: unbekannte Slot-ID: 404 (Anforderung 6)")
    void unbekannteSlotIdLiefert404() {
        erwarteFehler(buchen("gibt-es-nicht"), 404);
    }

    @Test
    @DisplayName("Patientenname mit 101 Zeichen: 400 (Anforderung 7)")
    void nameMit101ZeichenLiefert400() {
        erwarteFehler(buchen(zukuenftigerSlot(), "T".repeat(101), "Kontrolle"), 400);
    }

    @Tag("Defect-9")
    @Test
    @DisplayName("Defect 9: ungueltiges JSON: 400 im Format {\"fehler\":...} (Fehlerformat)")
    void ungueltigesJsonLiefertFehlerformat() {
        HttpHeaders kopf = new HttpHeaders();
        kopf.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> kaputterBody = new HttpEntity<>("{kein json", kopf);

        erwarteFehler(http.postForEntity("/api/appointments", kaputterBody, JsonNode.class), 400);
    }
}
