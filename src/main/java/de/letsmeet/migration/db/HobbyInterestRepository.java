package de.letsmeet.migration.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Schreibt Hobby-Woerterbuch, Hobbyzuordnungen und Interessen-Codes (Akt 2). */
public final class HobbyInterestRepository {

    public record PersonHobby(long personId, String hobbyName, int prioritaet, String quelle) {
    }

    public record PersonInterest(long personId, String code) {
    }

    /** Legt fehlende Hobbynamen an und gibt {@code bezeichnung -> hobby_id} zurueck. */
    public Map<String, Long> upsertHobbies(Connection connection, Set<String> namen)
            throws SQLException {
        try (PreparedStatement einfuegen = connection.prepareStatement(
                "INSERT INTO hobby (bezeichnung) VALUES (?) ON CONFLICT (bezeichnung) DO NOTHING")) {
            for (String name : namen) {
                einfuegen.setString(1, name);
                einfuegen.addBatch();
            }
            einfuegen.executeBatch();
        }
        Map<String, Long> ids = new HashMap<>();
        try (Statement statement = connection.createStatement();
             ResultSet zeilen = statement.executeQuery("SELECT hobby_id, bezeichnung FROM hobby")) {
            while (zeilen.next()) {
                ids.put(zeilen.getString(2), zeilen.getLong(1));
            }
        }
        return ids;
    }

    public int insertPersonHobbies(Connection connection, Map<String, Long> hobbyIds,
                                   List<PersonHobby> zuordnungen) throws SQLException {
        String sql = "INSERT INTO person_hobby (person_id, hobby_id, quelle, prioritaet) "
                + "VALUES (?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (PersonHobby ph : zuordnungen) {
                statement.setLong(1, ph.personId());
                statement.setLong(2, hobbyIds.get(ph.hobbyName()));
                statement.setString(3, ph.quelle());
                statement.setInt(4, ph.prioritaet());
                statement.addBatch();
            }
            return summe(statement.executeBatch());
        }
    }

    public int insertInterests(Connection connection, List<PersonInterest> interessen)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO person_interesse (person_id, interesse_code) VALUES (?, ?)")) {
            for (PersonInterest pi : interessen) {
                statement.setLong(1, pi.personId());
                statement.setString(2, pi.code());
                statement.addBatch();
            }
            return summe(statement.executeBatch());
        }
    }

    private static int summe(int[] ergebnisse) {
        int summe = 0;
        for (int ergebnis : ergebnisse) {
            summe += ergebnis == Statement.SUCCESS_NO_INFO ? 1 : Math.max(ergebnis, 0);
        }
        return summe;
    }
}
