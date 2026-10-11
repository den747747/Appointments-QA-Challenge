package at.cgm.termine.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import at.cgm.termine.dto.BuchungsAnfrage;
import at.cgm.termine.dto.SlotAnfrage;
import at.cgm.termine.model.Slot;
import at.cgm.termine.model.Termin;
import at.cgm.termine.model.TerminStatus;
import at.cgm.termine.web.FachlicheAusnahme;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;

class TerminServiceTest {

    private static final String GUELTIGER_NAME = "Denis Fedorov";
    private static final String GUELTIGER_GRUND = "Kontrolle";
    private static final long STORNOFRIST_MINUTEN = 24 * 60;

    private TerminService service;

    @BeforeEach
    void erzeugeService() {
        service = new TerminService(new KalenderSyncService());
    }

    private Slot slotInMinuten(long minuten) {
        return service.legeSlotAn(new SlotAnfrage("Dr. Test", LocalDateTime.now().plusMinutes(minuten)));
    }

    private Slot zukuenftigerSlot() {
        return slotInMinuten(TimeUnit.DAYS.toMinutes(3));
    }

    private Termin buchen(Slot slot, String name, String grund) {
        return service.buchen(new BuchungsAnfrage(slot.getId(), name, grund));
    }

    private Termin buchen(Slot slot, String name) {
        return buchen(slot, name, GUELTIGER_GRUND);
    }

    private Termin buchenGueltig(Slot slot) {
        return buchen(slot, GUELTIGER_NAME);
    }

    private HttpStatus buchungsstatus(Slot slot, String name) {
        try {
            buchen(slot, name);
            return HttpStatus.CREATED;
        } catch (FachlicheAusnahme e) {
            return e.getStatus();
        }
    }

    private List<HttpStatus> buchenParallel(Slot slot) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<HttpStatus>> ergebnisse = pool.invokeAll(List.of(
                    () -> buchungsstatus(slot, "Patient A"),
                    () -> buchungsstatus(slot, "Patient B")), 10, TimeUnit.SECONDS);
            return List.of(ergebnisse.get(0).get(), ergebnisse.get(1).get());
        } finally {
            pool.shutdownNow();
        }
    }

    private static void erwarteFehler(ThrowingCallable aktion, HttpStatus status) {
        assertThatExceptionOfType(FachlicheAusnahme.class)
                .isThrownBy(aktion)
                .extracting(FachlicheAusnahme::getStatus)
                .isEqualTo(status);
    }

    @Nested
    class Buchung {

        @Test
        @DisplayName("Buchung: Termin GEBUCHT, Slot belegt (Anforderung 1)")
        void buchungBelegtSlot() {
            Slot slot = zukuenftigerSlot();
            Termin termin = buchenGueltig(slot);

            assertThat(termin.getStatus()).isEqualTo(TerminStatus.GEBUCHT);
            assertThat(termin.getSlotId()).isEqualTo(slot.getId());
            assertThat(slot.isGebucht()).isTrue();
        }

        @Test
        @DisplayName("zweite Buchung desselben Slots: 409, nur ein Termin (Anforderung 1)")
        void zweiteBuchungWirdAbgelehnt() {
            Slot slot = zukuenftigerSlot();
            buchenGueltig(slot);

            erwarteFehler(() -> buchen(slot, "Zweite Patientin"), HttpStatus.CONFLICT);
            assertThat(service.alleTermine()).hasSize(1);
        }

        @Tag("Defect-1")
        @Test
        @DisplayName("Defect 1: gleichzeitige Buchungen desselben Slots: genau ein Termin (Anforderung 1)")
        void gleichzeitigeBuchungenErgebenGenauEinenTermin() throws Exception {
            Slot slot = zukuenftigerSlot();

            List<HttpStatus> ergebnisse = buchenParallel(slot);

            assertThat(ergebnisse).containsExactlyInAnyOrder(HttpStatus.CREATED, HttpStatus.CONFLICT);
            assertThat(service.alleTermine()).hasSize(1);
        }

        @Test
        @DisplayName("Slot vor 1 Minute: 400, kein Termin (Anforderung 2)")
        void vergangenerSlotWirdAbgelehnt() {
            Slot slot = slotInMinuten(-1);

            erwarteFehler(() -> buchenGueltig(slot), HttpStatus.BAD_REQUEST);
            assertThat(service.alleTermine()).isEmpty();
            assertThat(slot.isGebucht()).isFalse();
        }

        @Test
        @DisplayName("Slot in 1 Minute: gebucht (Anforderung 2)")
        void slotInEinerMinuteIstBuchbar() {
            assertThat(buchenGueltig(slotInMinuten(1)).getStatus()).isEqualTo(TerminStatus.GEBUCHT);
        }

        @Tag("Defect-5")
        @Test
        @DisplayName("Defect 5: unbekannte Slot-ID: 404 (Anforderung 6)")
        void unbekannteSlotIdLiefert404() {
            erwarteFehler(() -> service.buchen(new BuchungsAnfrage("gibt-es-nicht", GUELTIGER_NAME, GUELTIGER_GRUND)),
                    HttpStatus.NOT_FOUND);
        }
    }

    @Nested
    class Stornierung {

        @Test
        @DisplayName("Stornierung 24 h 1 min vor Beginn: storniert (Anforderung 3)")
        void stornierungKnappVorFristIstMoeglich() {
            Termin termin = buchenGueltig(slotInMinuten(STORNOFRIST_MINUTEN + 1));

            assertThat(service.stornieren(termin.getId()).getStatus()).isEqualTo(TerminStatus.STORNIERT);
        }

        @Tag("Defect-4")
        @Test
        @DisplayName("Defect 4: Stornierung 23 h 59 min vor Beginn: 409, Termin bleibt (Anforderung 3)")
        void stornierungKnappInnerhalbDerFristWirdAbgelehnt() {
            Slot slot = slotInMinuten(STORNOFRIST_MINUTEN - 1);
            Termin termin = buchenGueltig(slot);

            erwarteFehler(() -> service.stornieren(termin.getId()), HttpStatus.CONFLICT);
            assertThat(slot.isGebucht()).isTrue();
        }

        @Test
        @DisplayName("Buchen, Stornieren, erneut Buchen (Anforderung 4)")
        void stornierterSlotIstErneutBuchbar() {
            Slot slot = zukuenftigerSlot();
            Termin storniert = service.stornieren(buchenGueltig(slot).getId());

            assertThat(storniert.getStatus()).isEqualTo(TerminStatus.STORNIERT);
            assertThat(slot.isGebucht()).isFalse();

            Termin neu = buchen(slot, "Zweite Patientin");

            assertThat(neu.getStatus()).isEqualTo(TerminStatus.GEBUCHT);
            assertThat(slot.isGebucht()).isTrue();
        }

        @Tag("Defect-2")
        @Test
        @DisplayName("Defect 2: zweite Stornierung desselben Termins: 409 (Anforderung 5)")
        void zweiteStornierungWirdAbgelehnt() {
            Termin termin = buchenGueltig(zukuenftigerSlot());
            service.stornieren(termin.getId());

            erwarteFehler(() -> service.stornieren(termin.getId()), HttpStatus.CONFLICT);
        }


        @Tag("Defect-3")
        @Test
        @DisplayName("Defect 3: nach erneutem Stornierungsversuch von A bleibt Bs Slot belegt (Anforderung 1)")
        void erneuteStornierungGibtFremdenSlotNichtFrei() {
            Slot slot = zukuenftigerSlot();
            Termin terminA = buchen(slot, "Patient A");
            service.stornieren(terminA.getId());
            Termin terminB = buchen(slot, "Patient B");

            assertSoftly(softly -> {
                softly.check(() -> erwarteFehler(() -> service.stornieren(terminA.getId()), HttpStatus.CONFLICT));
                softly.assertThat(slot.isGebucht()).as("Slot von B vor Cs Buchungsversuch").isTrue();
                softly.assertThat(terminB.getStatus()).as("Termin von B").isEqualTo(TerminStatus.GEBUCHT);
                softly.check(() -> erwarteFehler(() -> buchen(slot, "Patient C"), HttpStatus.CONFLICT));
                softly.assertThat(service.alleTermine())
                        .filteredOn(Termin::getSlotId, slot.getId())
                        .filteredOn(Termin::getStatus, TerminStatus.GEBUCHT)
                        .as("Aktive Termine auf demselben Slot").containsExactly(terminB);
            });
        }

        @Test
        @DisplayName("unbekannte Termin-ID: 404 (Anforderung 6)")
        void unbekannteTerminIdLiefert404() {
            erwarteFehler(() -> service.stornieren("gibt-es-nicht"), HttpStatus.NOT_FOUND);
        }
    }

    @Nested
    class Validierung {

        @ParameterizedTest(name = "Patientenname mit {0} Zeichen: gebucht (Anforderung 7)")
        @ValueSource(ints = {1, 100})
        void gueltigeNamenslaengenWerdenAngenommen(int laenge) {
            Termin termin = buchen(zukuenftigerSlot(), "X".repeat(laenge));

            assertThat(termin.getPatientenname()).hasSize(laenge);
        }

        @Tag("Defect-8")
        @Test
        @DisplayName("Defect 8: Patientenname aus 100 japanischen Zeichen (U+20BB7, wie in Yoshida): gebucht (Anforderung 7)")
        void nameMit100JapanischenZeichenWirdAngenommen() {
            Slot slot = zukuenftigerSlot();
            String name = Character.toString(0x20BB7).repeat(100);

            assertThatCode(() -> buchen(slot, name)).doesNotThrowAnyException();
            assertThat(slot.isGebucht()).isTrue();
        }

        @Test
        @DisplayName("Patientenname mit 101 Zeichen: 400, Slot frei (Anforderung 7)")
        void nameMit101ZeichenWirdAbgelehnt() {
            Slot slot = zukuenftigerSlot();

            erwarteFehler(() -> buchen(slot, "X".repeat(101)), HttpStatus.BAD_REQUEST);
            assertThat(slot.isGebucht()).isFalse();
        }

        @ParameterizedTest(name = "Patientenname \"{0}\": 400 (Anforderung 7)")
        @NullAndEmptySource
        @ValueSource(strings = " ")
        void fehlenderOderLeererNameWirdAbgelehnt(String name) {
            Slot slot = zukuenftigerSlot();

            erwarteFehler(() -> buchen(slot, name), HttpStatus.BAD_REQUEST);
        }

        @Tag("Defect-7")
        @Test
        @DisplayName("Defect 7: Patientenname aus geschuetztem Leerzeichen (U+00A0): 400, Slot frei (Anforderung 7)")
        void nameAusGeschuetztemLeerzeichenWirdAbgelehnt() {
            Slot slot = zukuenftigerSlot();

            erwarteFehler(() -> buchen(slot, "\u00A0"), HttpStatus.BAD_REQUEST);
            assertThat(slot.isGebucht()).isFalse();
        }

        @ParameterizedTest(name = "Grund mit {0} Zeichen: gebucht (Anforderung 7)")
        @ValueSource(ints = {1, 255})
        void gueltigeGrundlaengenWerdenAngenommen(int laenge) {
            Slot slot = zukuenftigerSlot();
            String grund = "X".repeat(laenge);
            Termin termin = buchen(slot, GUELTIGER_NAME, grund);

            assertThat(termin.getGrund()).isEqualTo(grund);
            assertThat(termin.getStatus()).isEqualTo(TerminStatus.GEBUCHT);
            assertThat(slot.isGebucht()).isTrue();
        }

        @Tag("Defect-6")
        @Test
        @DisplayName("Defect 6: Grund mit 256 Zeichen: 400, Slot frei (Anforderung 7)")
        void grundMit256ZeichenWirdAbgelehnt() {
            Slot slot = zukuenftigerSlot();

            erwarteFehler(() -> buchen(slot, GUELTIGER_NAME, "X".repeat(256)), HttpStatus.BAD_REQUEST);
            assertThat(slot.isGebucht()).isFalse();
        }

        @ParameterizedTest(name = "Grund \"{0}\": 400 (Anforderung 7)")
        @NullAndEmptySource
        @ValueSource(strings = " ")
        void fehlenderOderLeererGrundWirdAbgelehnt(String grund) {
            Slot slot = zukuenftigerSlot();

            erwarteFehler(() -> buchen(slot, GUELTIGER_NAME, grund), HttpStatus.BAD_REQUEST);
        }
    }
}
