package de.letsmeet.migration.parse;

/**
 * Eine Quellzeile passt nicht zu den Regeln des Datenvertrags.
 *
 * <p>Wird je Zeile gefangen: die Zeile wird abgewiesen und protokolliert, nicht
 * mit Platzhaltern gerettet. Der Datenvertrag sagt dazu: "keine Platzhalter fuer
 * misslungene Zeilen - was nicht importiert ist, fehlt sichtbar."
 */
public class SourceDataException extends RuntimeException {

    public SourceDataException(String message) {
        super(message);
    }
}
