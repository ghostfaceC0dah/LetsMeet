package de.letsmeet.migration.parse;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests der zentralen Importannahmen (Auftrag Akt 1, Punkt 5).
 *
 * <p>Ein Test pro Regel, der Name sagt, was gelten soll. Die Faelle mit
 * DisplayName sind die Stolperstellen aus dem Datenvertrag - an ihnen scheitert
 * ein naives {@code split(", ")}.
 */
class RowSplitterTest {

    // --- Nachname, Vorname ---------------------------------------------------

    @Test
    void zerlegtNameImNormalfall() {
        assertArrayEquals(new String[]{"Forster", "Martin"},
                RowSplitter.splitName("Forster, Martin"));
    }

    @Test
    @DisplayName("Das zweite Leerzeichen gehoert zum Wert: \"Stanislav , Petrov\"")
    void behaeltZusaetzlichesLeerzeichenImNachnamen() {
        assertArrayEquals(new String[]{"Stanislav ", "Petrov"},
                RowSplitter.splitName("Stanislav , Petrov"));
    }

    @Test
    @DisplayName("Aeussere Leerzeichen bleiben stehen - kein trim()")
    void behaeltAeussereLeerzeichen() {
        assertArrayEquals(new String[]{"Jerome", "Joyeux "},
                RowSplitter.splitName("Jerome, Joyeux "));
    }

    @Test
    @DisplayName("Getrennt wird nur an der ERSTEN Fundstelle")
    void trenntNurEinmal() {
        assertArrayEquals(new String[]{"van Deloo", "Albert, jun."},
                RowSplitter.splitName("van Deloo, Albert, jun."));
    }

    @Test
    void weistNamenszelleOhneTrennzeichenAb() {
        assertThrows(SourceDataException.class, () -> RowSplitter.splitName("Forster Martin"));
        assertThrows(SourceDataException.class, () -> RowSplitter.splitName("Forster,Martin"));
        assertThrows(SourceDataException.class, () -> RowSplitter.splitName(null));
    }

    @Test
    void weistLeereNamensteileAb() {
        assertThrows(SourceDataException.class, () -> RowSplitter.splitName(", Martin"));
        assertThrows(SourceDataException.class, () -> RowSplitter.splitName("Forster, "));
    }

    // --- Strasse Nr, PLZ Ort ------------------------------------------------

    @Test
    void zerlegtAdresseImNormalfall() {
        assertArrayEquals(new String[]{"Minslebener Str. 0", "46286", "Dorsten"},
                RowSplitter.splitAddress("Minslebener Str. 0, 46286, Dorsten"));
    }

    @Test
    @DisplayName("Ein Komma im Ortsnamen gehoert zum Ort: \"Demmin, Hansestadt\"")
    void ortDarfKommaEnthalten() {
        assertArrayEquals(new String[]{"Boniverstr. 25", "17109", "Demmin, Hansestadt"},
                RowSplitter.splitAddress("Boniverstr. 25, 17109, Demmin, Hansestadt"));
    }

    @Test
    @DisplayName("Leerzeichen am Zellrand bleiben Teil von Strasse bzw. Ort")
    void behaeltAeussereLeerzeichenInDerAdresse() {
        assertEquals(" Detmolderstr. 119a",
                RowSplitter.splitAddress(" Detmolderstr. 119a, 99887, Hohenkirchen")[0]);
        assertEquals("Rosenheim ",
                RowSplitter.splitAddress("Mittelweg 19, 83026, Rosenheim ")[2]);
    }

    @Test
    @DisplayName("Vierstellige PLZ und fuehrende Nullen bleiben unveraendert")
    void behaeltKurzeUndNullPostleitzahl() {
        assertEquals("6849", RowSplitter.splitAddress("Hauptstr. 1, 6849, Bregenz")[1]);
        assertEquals("01067", RowSplitter.splitAddress("Postplatz 2, 01067, Dresden")[1]);
    }

    @Test
    void weistAdresseMitZuWenigTeilenAb() {
        assertThrows(SourceDataException.class,
                () -> RowSplitter.splitAddress("Minslebener Str. 0, 46286"));
        assertThrows(SourceDataException.class,
                () -> RowSplitter.splitAddress("Minslebener Str. 0 46286 Dorsten"));
        assertThrows(SourceDataException.class, () -> RowSplitter.splitAddress(null));
    }

    // --- Geburtsdatum -------------------------------------------------------

    @Test
    void liestDeutschesDatum() {
        assertEquals(LocalDate.of(1959, 3, 7), RowSplitter.parseBirthDate("07.03.1959"));
        assertEquals(LocalDate.of(1996, 7, 27), RowSplitter.parseBirthDate("27.07.1996"));
    }

    @Test
    @DisplayName("Ein unmoegliches Datum wird abgewiesen, nicht verschoben")
    void weistUnmoeglichesDatumAb() {
        assertThrows(SourceDataException.class, () -> RowSplitter.parseBirthDate("31.02.1990"));
        assertThrows(SourceDataException.class, () -> RowSplitter.parseBirthDate("00.01.1990"));
    }

    @Test
    void weistAndereDatumsformateAb() {
        assertThrows(SourceDataException.class, () -> RowSplitter.parseBirthDate("1959-03-07"));
        assertThrows(SourceDataException.class, () -> RowSplitter.parseBirthDate("7.3.1959"));
        assertThrows(SourceDataException.class, () -> RowSplitter.parseBirthDate(""));
        assertThrows(SourceDataException.class, () -> RowSplitter.parseBirthDate(null));
    }

    // --- E-Mail-Schluessel --------------------------------------------------

    @Test
    @DisplayName("Gross- und Kleinschreibung macht keinen Unterschied")
    void emailSchluesselIgnoriertSchreibweise() {
        assertEquals(RowSplitter.emailKey("martin.forster@web.ork"),
                RowSplitter.emailKey("Martin.Forster@web.ork"));
        assertEquals("martin.forster@web.ork", RowSplitter.emailKey("MARTIN.FORSTER@WEB.ORK"));
    }

    @Test
    void weistLeereEmailAb() {
        assertThrows(SourceDataException.class, () -> RowSplitter.emailKey("   "));
        assertThrows(SourceDataException.class, () -> RowSplitter.emailKey(null));
    }
}
