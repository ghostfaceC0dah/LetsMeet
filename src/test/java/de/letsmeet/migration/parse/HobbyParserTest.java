package de.letsmeet.migration.parse;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests fuer die Zerlegung der Hobby-Zelle (Akt 2). */
class HobbyParserTest {

    @Test
    @DisplayName("Name und Prioritaet je Eintrag, getrennt an ';'")
    void zerlegtMehrereHobbys() {
        List<HobbyParser.Hobby> hobbys = HobbyParser.parse(
                "Im Wasser waten %80%; Tagebuch schreiben %12%;");
        assertEquals(List.of(
                new HobbyParser.Hobby("Im Wasser waten", 80),
                new HobbyParser.Hobby("Tagebuch schreiben", 12)), hobbys);
    }

    @Test
    @DisplayName("Das Trennzeichen hinter dem letzten Wert erzeugt keinen Leereintrag")
    void ignoriertLeerenRestHinterLetztemSemikolon() {
        assertEquals(1, HobbyParser.parse("Faulenzen %5%;").size());
    }

    @Test
    void leereZelleErgibtLeereListe() {
        assertTrue(HobbyParser.parse(null).isEmpty());
        assertTrue(HobbyParser.parse("   ").isEmpty());
    }

    @Test
    @DisplayName("Prioritaet darf den vereinbarten Randwert -100 tragen")
    void erlaubtNegativePrioritaet() {
        assertEquals(-100, HobbyParser.parse("Holz hacken %-100%;").get(0).prioritaet());
    }

    @Test
    void weistEintragOhnePrioritaetAb() {
        assertThrows(SourceDataException.class, () -> HobbyParser.parse("Angeln; Kochen %3%;"));
    }

    @Test
    @DisplayName("Derselbe Hobbyname in einer Zelle erscheint nur einmal")
    void entdoppeltNamenInEinerZelle() {
        assertEquals(1, HobbyParser.parse("Reiten %10%; Reiten %90%;").size());
    }
}
