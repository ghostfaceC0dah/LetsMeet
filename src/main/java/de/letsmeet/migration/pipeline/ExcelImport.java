package de.letsmeet.migration.pipeline;

import de.letsmeet.migration.config.AppConfig;
import de.letsmeet.migration.db.Database;
import de.letsmeet.migration.db.HobbyInterestRepository;
import de.letsmeet.migration.db.HobbyInterestRepository.PersonHobby;
import de.letsmeet.migration.db.HobbyInterestRepository.PersonInterest;
import de.letsmeet.migration.db.PersonRepository;
import de.letsmeet.migration.model.Person;
import de.letsmeet.migration.parse.HobbyParser;
import de.letsmeet.migration.parse.RowSplitter;
import de.letsmeet.migration.parse.SourceDataException;
import de.letsmeet.migration.source.excel.ExcelUserReader;
import de.letsmeet.migration.source.excel.ExcelUserRow;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Der Excel-Import: lesen, zerlegen, schreiben.
 *
 * <p>Akt 1 hat hier die Personen-Stammdaten angelegt. Akt 2 loest zusaetzlich
 * die beiden Mehrfachwerte der Quelle auf:
 * <ul>
 *   <li>Spalte D "Hobby1 %Prio1%; ..." -> {@code hobby} + {@code person_hobby}
 *       mit {@code quelle = 'excel'};</li>
 *   <li>Spalte G "Interessiert an" -> {@code person_interesse}, ein Rohwert je
 *       Zeile. "mw" ist zwei Interessen (m und w) und ergibt zwei Zeilen
 *       (Datenvertrag: "Mehrere Interessen ergeben mehrere Zeilen").</li>
 * </ul>
 *
 * <p>Drei Regeln des Datenvertrags stecken in dieser Klasse:
 *
 * <ul>
 *   <li><strong>Keine Platzhalter.</strong> Eine Zeile, die sich nicht
 *       regelkonform zerlegen laesst, wird abgewiesen und protokolliert - mit
 *       allem, was an ihr haengt (auch ihre Hobbys).</li>
 *   <li><strong>E-Mail eindeutig, Schreibweise egal.</strong> Kommt dieselbe
 *       Adresse zweimal, gewinnt die erste Zeile; die zweite wird abgewiesen.</li>
 *   <li><strong>Alles in einer Transaktion.</strong> Entweder ist der ganze
 *       Excel-Stand drin oder keiner.</li>
 * </ul>
 */
public final class ExcelImport {

    private static final String QUELLE = "excel";

    private final AppConfig config;
    private final Database database;

    public ExcelImport(AppConfig config, Database database) {
        this.config = config;
        this.database = database;
    }

    /** Eine zerlegte Excel-Zeile: die Person und ihre Mehrfachwerte. */
    private record Zerlegt(Person person, List<HobbyParser.Hobby> hobbys, String interesse) {
    }

    public ImportReport run() throws Exception {
        ImportReport bericht = new ImportReport("Excel-Import");

        List<ExcelUserRow> zeilen = new ExcelUserReader().read(config.excelFile());
        bericht.count("Zeilen in der Quelle", zeilen.size());

        // LinkedHashMap: behaelt die Reihenfolge der Quelle und erkennt Dubletten.
        Map<String, Zerlegt> nachEmail = new LinkedHashMap<>();

        for (ExcelUserRow zeile : zeilen) {
            try {
                String schluessel = RowSplitter.emailKey(zeile.email());
                if (nachEmail.containsKey(schluessel)) {
                    bericht.reject(zeile.rowNumber(), "E-Mail doppelt: " + zeile.email());
                    continue;
                }

                String[] name = RowSplitter.splitName(zeile.nameCell());
                String[] adresse = RowSplitter.splitAddress(zeile.addressCell());
                List<HobbyParser.Hobby> hobbys = HobbyParser.parse(zeile.hobbyCell());

                Person person = new Person(
                        zeile.email(), name[0], name[1],
                        RowSplitter.parseBirthDate(zeile.birthDateCell()),
                        adresse[0], adresse[1], adresse[2],
                        zeile.phone(), zeile.gender());

                nachEmail.put(schluessel, new Zerlegt(person, hobbys, zeile.interest()));
            } catch (SourceDataException e) {
                bericht.reject(zeile.rowNumber(), e.getMessage());
            }
        }

        try (Connection connection = database.open()) {
            connection.setAutoCommit(false);
            try {
                schreibe(connection, bericht, nachEmail.values());
                connection.commit();
            } catch (Exception e) {
                connection.rollback();
                throw e;
            }
        }
        return bericht;
    }

    private void schreibe(Connection connection, ImportReport bericht,
                          Iterable<Zerlegt> zerlegt) throws Exception {
        List<Person> personen = new ArrayList<>();
        for (Zerlegt z : zerlegt) {
            personen.add(z.person());
        }

        PersonRepository personRepo = new PersonRepository();
        bericht.count("Personen geschrieben", personRepo.insertAll(connection, personen));

        Map<String, Long> personIds = personRepo.idsByEmailKey(connection);

        // Hobbys: erst das Woerterbuch, dann die Zuordnungen.
        Set<String> hobbyNamen = new LinkedHashSet<>();
        List<PersonHobby> zuordnungen = new ArrayList<>();
        List<PersonInterest> interessen = new ArrayList<>();

        for (Zerlegt z : zerlegt) {
            long personId = personIds.get(RowSplitter.emailKey(z.person().email()));
            for (HobbyParser.Hobby h : z.hobbys()) {
                hobbyNamen.add(h.name());
                zuordnungen.add(new PersonHobby(personId, h.name(), h.prioritaet(), QUELLE));
            }
            for (String code : RowSplitter.splitInterests(z.interesse())) {
                interessen.add(new PersonInterest(personId, code));
            }
        }

        HobbyInterestRepository hobbyRepo = new HobbyInterestRepository();
        Map<String, Long> hobbyIds = hobbyRepo.upsertHobbies(connection, hobbyNamen);
        bericht.count("Hobbynamen im Woerterbuch", hobbyNamen.size());
        bericht.count("Hobbyzuordnungen geschrieben",
                hobbyRepo.insertPersonHobbies(connection, hobbyIds, zuordnungen));
        bericht.count("Interessen-Zeilen geschrieben",
                hobbyRepo.insertInterests(connection, interessen));
    }
}