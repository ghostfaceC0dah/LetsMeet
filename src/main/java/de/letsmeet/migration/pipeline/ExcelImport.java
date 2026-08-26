package de.letsmeet.migration.pipeline;

import de.letsmeet.migration.config.AppConfig;
import de.letsmeet.migration.db.Database;
import de.letsmeet.migration.db.PersonRepository;
import de.letsmeet.migration.model.Person;
import de.letsmeet.migration.parse.RowSplitter;
import de.letsmeet.migration.parse.SourceDataException;
import de.letsmeet.migration.source.excel.ExcelUserReader;
import de.letsmeet.migration.source.excel.ExcelUserRow;

import java.sql.Connection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Der Import fuer Akt 1: lesen, zerlegen, schreiben.
 *
 * <p>Drei Regeln des Datenvertrags stecken in dieser Klasse:
 *
 * <ul>
 *   <li><strong>Keine Platzhalter.</strong> Eine Zeile, die sich nicht
 *       regelkonform zerlegen laesst, wird abgewiesen und protokolliert. Es wird
 *       nichts geraten und nichts leer gelassen, damit "die Zeile trotzdem
 *       durchgeht".</li>
 *   <li><strong>E-Mail eindeutig, Schreibweise egal.</strong> Kommt dieselbe
 *       Adresse zweimal, gewinnt die erste Zeile; die zweite wird abgewiesen -
 *       so ueberschreibt keine Zeile stillschweigend eine andere.</li>
 *   <li><strong>Alles in einer Transaktion.</strong> Entweder sind alle
 *       Personen drin oder keine. Ein abgebrochener Import laesst keine halbe
 *       Migration zurueck, auf der ein Pruefstand gruen werden koennte.</li>
 * </ul>
 *
 * <p>Uebernommen werden alle Personenfelder der Excel-Zeile, nicht nur die
 * sechs Spalten der View V1: Telefon und Geschlecht stehen in derselben Zeile,
 * gehoeren zur selben Person und kosten nichts. Sie sind in Akt 2 ohnehin
 * gefordert - sie jetzt wegzulassen hiesse, den Import zweimal zu schreiben.
 */
public final class ExcelImport {

    private final AppConfig config;
    private final Database database;

    public ExcelImport(AppConfig config, Database database) {
        this.config = config;
        this.database = database;
    }

    public ImportReport run() throws Exception {
        ImportReport bericht = new ImportReport("Excel-Import");

        List<ExcelUserRow> zeilen = new ExcelUserReader().read(config.excelFile());
        bericht.count("Zeilen in der Quelle", zeilen.size());

        // LinkedHashMap: behaelt die Reihenfolge der Quelle und erkennt Dubletten.
        Map<String, Person> personen = new LinkedHashMap<>();

        for (ExcelUserRow zeile : zeilen) {
            try {
                String schluessel = RowSplitter.emailKey(zeile.email());
                if (personen.containsKey(schluessel)) {
                    bericht.reject(zeile.rowNumber(), "E-Mail doppelt: " + zeile.email());
                    continue;
                }

                String[] name = RowSplitter.splitName(zeile.nameCell());
                String[] adresse = RowSplitter.splitAddress(zeile.addressCell());

                personen.put(schluessel, new Person(
                        zeile.email(),
                        name[0],
                        name[1],
                        RowSplitter.parseBirthDate(zeile.birthDateCell()),
                        adresse[0],
                        adresse[1],
                        adresse[2],
                        zeile.phone(),
                        zeile.gender()));
            } catch (SourceDataException e) {
                bericht.reject(zeile.rowNumber(), e.getMessage());
            }
        }

        try (Connection connection = database.open()) {
            connection.setAutoCommit(false);
            try {
                int geschrieben = new PersonRepository()
                        .insertAll(connection, List.copyOf(personen.values()));
                connection.commit();
                bericht.count("Personen geschrieben", geschrieben);
            } catch (Exception e) {
                connection.rollback();
                throw e;
            }
        }

        return bericht;
    }
}
