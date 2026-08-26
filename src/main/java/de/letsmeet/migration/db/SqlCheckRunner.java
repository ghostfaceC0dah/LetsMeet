package de.letsmeet.migration.db;

import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Fuehrt die eigenen Datenpruefungen aus {@code sql/920_datenpruefung.sql} aus.
 * Jede Abfrage dort muss 0 Zeilen liefern; alles andere ist ein Befund.
 */
public final class SqlCheckRunner {

    private static final String MARKER = "-- @check ";
    private static final String TABELLE_FEHLT = "42P01";   // PostgreSQL: undefined_table

    public record Result(String name, int treffer, boolean uebersprungen) {

        public boolean failed() {
            return !uebersprungen && treffer > 0;
        }

        @Override
        public String toString() {
            if (uebersprungen) {
                return "  [uebersprungen] " + name;
            }
            return (treffer == 0 ? "  [ok]   " : "  [FEHL] ") + name
                    + (treffer == 0 ? "" : " (" + treffer + " Zeilen)");
        }
    }

    public List<Result> runAll(Connection connection) throws IOException, SQLException {
        List<Result> ergebnisse = new ArrayList<>();
        for (Check pruefung : parse(SqlScriptRunner.read("sql/920_datenpruefung.sql"))) {
            ergebnisse.add(run(connection, pruefung));
        }
        return ergebnisse;
    }

    /**
     * Jede Pruefung laeuft hinter einem SAVEPOINT.
     *
     * <p>Grund: in PostgreSQL bricht ein fehlgeschlagenes Statement die ganze
     * Transaktion ab ("current transaction is aborted"). Ohne Savepoint wuerde
     * eine Pruefung auf eine noch nicht existierende Tabelle alle folgenden
     * Pruefungen mitreissen.
     */
    private Result run(Connection connection, Check pruefung) throws SQLException {
        Savepoint savepoint = connection.setSavepoint("check");
        try (Statement statement = connection.createStatement();
             ResultSet zeilen = statement.executeQuery(pruefung.sql())) {
            int treffer = 0;
            while (zeilen.next()) {
                treffer++;
            }
            connection.releaseSavepoint(savepoint);
            return new Result(pruefung.name(), treffer, false);
        } catch (SQLException e) {
            connection.rollback(savepoint);
            if (TABELLE_FEHLT.equals(e.getSQLState())) {
                return new Result(pruefung.name(), 0, true);
            }
            throw e;
        }
    }

    private record Check(String name, String sql) {
    }

    private static List<Check> parse(String skript) {
        List<Check> pruefungen = new ArrayList<>();
        String name = null;
        StringBuilder sql = new StringBuilder();
        for (String zeile : skript.split("\n")) {
            if (zeile.startsWith(MARKER)) {
                add(pruefungen, name, sql);
                name = zeile.substring(MARKER.length()).trim();
                sql.setLength(0);
            } else if (name != null && !zeile.startsWith("--")) {
                sql.append(zeile).append('\n');
            }
        }
        add(pruefungen, name, sql);
        return pruefungen;
    }

    private static void add(List<Check> pruefungen, String name, StringBuilder sql) {
        if (name != null && !sql.toString().isBlank()) {
            pruefungen.add(new Check(name, sql.toString().trim()));
        }
    }
}
