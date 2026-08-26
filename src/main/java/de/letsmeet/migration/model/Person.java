package de.letsmeet.migration.model;

import java.time.LocalDate;

/**
 * Eine migrierte Person - genau die Spalten der Tabelle {@code person}, die aus
 * der Excel-Quelle gefuellt werden koennen.
 *
 * <p>Bewusst getrennt von {@code ExcelUserRow}: dort steht, was in der Datei
 * steht (alles Text, ungeprueft), hier steht das Ergebnis der Zerlegung
 * (Datentypen, geprueft). Wer die beiden vermischt, merkt spaeter nicht mehr,
 * ob ein Wert aus der Quelle kommt oder aus einer Regel.
 *
 * <p>{@code rolle}, {@code passwort_hash}, {@code angelegt_am} und
 * {@code geaendert_am} fehlen hier absichtlich: Erstere hat die Excel-Quelle
 * nicht (Standardwert der Tabelle), Letztere liefert erst die MongoDB in Akt 2.
 */
public record Person(String email,
                     String nachname,
                     String vorname,
                     LocalDate geburtsdatum,
                     String strasse,
                     String plz,
                     String ort,
                     String telefon,
                     String geschlecht) {
}
