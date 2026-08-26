package de.letsmeet.migration.source.excel;

/**
 * Eine Zeile der Excel-Quelle - roh, ungeprueft, unveraendert.
 *
 * <p>Bewusst getrennt von einem spaeteren Zielmodell: hier steht, was in der
 * Datei steht. Das Zerlegen passiert an anderer Stelle
 * ({@link de.letsmeet.migration.parse.RowSplitter}).
 *
 * <p>Spalten der Quelle:
 * A "Nachname, Vorname" | B "Strasse Nr, PLZ Ort" | C Telefon |
 * D "Hobby1 %Prio1%; ..." | E E-Mail | F "Geschlecht (m/w/nonbinary)" |
 * G "Interessiert an" | H Geburtsdatum
 *
 * @param rowNumber Zeilennummer in der Datei (1-basiert, wie in Excel sichtbar)
 */
public record ExcelUserRow(int rowNumber,
                           String nameCell,
                           String addressCell,
                           String phone,
                           String hobbyCell,
                           String email,
                           String gender,
                           String interest,
                           String birthDateCell) {
}
