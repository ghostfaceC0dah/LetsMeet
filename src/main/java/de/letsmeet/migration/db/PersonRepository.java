package de.letsmeet.migration.db;

import de.letsmeet.migration.model.Person;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/** Schreibt Personen in die Tabelle {@code person}. */
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
