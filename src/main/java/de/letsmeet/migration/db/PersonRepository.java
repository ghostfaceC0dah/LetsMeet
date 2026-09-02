package de.letsmeet.migration.db;

import de.letsmeet.migration.model.Person;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Schreibt Personen in die Tabelle {@code person} und liest ihre IDs zurueck. */
public final class PersonRepository {

    /**
     * Wie viele Zeilen in einem Rutsch an die Datenbank gehen. 1576 Zeilen
     * einzeln zu schicken kostet 1576 Netzwerkrunden; gebuendelt sind es vier.
     */
    private static final int BUENDEL = 500;

    private static final String INSERT = """
            INSERT INTO person (email, nachname, vorname, geburtsdatum,
                                strasse, plz, ort, telefon, geschlecht)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    /**
     * Fuegt alle Personen ein. Der Aufrufer hat vorher sichergestellt, dass die
     * E-Mails untereinander eindeutig sind - schlaegt die Datenbank hier
     * trotzdem zu (UNIQUE-Index auf lower(email)), soll das auffallen und nicht
     * stillschweigend verschluckt werden.
     *
     * @return Anzahl geschriebener Zeilen
     */
    public int insertAll(Connection connection, List<Person> personen) throws SQLException {
        int geschrieben = 0;
        try (PreparedStatement statement = connection.prepareStatement(INSERT)) {
            int imBuendel = 0;
            for (Person person : personen) {
                statement.setString(1, person.email());
                statement.setString(2, person.nachname());
                statement.setString(3, person.vorname());
                statement.setDate(4, person.geburtsdatum() == null
                        ? null : Date.valueOf(person.geburtsdatum()));
                statement.setString(5, person.strasse());
                statement.setString(6, person.plz());
                statement.setString(7, person.ort());
                statement.setString(8, person.telefon());
                statement.setString(9, person.geschlecht());
                statement.addBatch();

                if (++imBuendel % BUENDEL == 0) {
                    geschrieben += zaehle(statement.executeBatch());
                }
            }
            geschrieben += zaehle(statement.executeBatch());
        }
        return geschrieben;
    }

    /** @return {@code lower(email) -> person_id} fuer alle Personen im Bestand */
    public Map<String, Long> idsByEmailKey(Connection connection) throws SQLException {
        Map<String, Long> ids = new HashMap<>();
        try (Statement statement = connection.createStatement();
             ResultSet zeilen = statement.executeQuery("SELECT person_id, email FROM person")) {
            while (zeilen.next()) {
                ids.put(zeilen.getString(2).strip().toLowerCase(Locale.ROOT), zeilen.getLong(1));
            }
        }
        return ids;
    }

    private static int zaehle(int[] ergebnisse) {
        int summe = 0;
        for (int ergebnis : ergebnisse) {
            if (ergebnis > 0) {
                summe += ergebnis;
            } else if (ergebnis == Statement.SUCCESS_NO_INFO) {
                summe++;   // PostgreSQL meldet im Batch nicht immer eine Zeilenzahl
            }
        }
        return summe;
    }
}