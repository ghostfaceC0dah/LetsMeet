package de.letsmeet.migration.db;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Fuehrt die SQL-Dateien aus {@code src/main/resources/sql/} aus.
 *
 * <p>Damit steckt die Struktur der Datenbank in versionierten Dateien und nicht
 * in Java-Strings - lesbar auch fuer jemanden, der das Programm nicht startet.
 * Und der Neuaufbau laeuft mit einem Befehl, wie der Auftrag es verlangt.
 */
public final class SqlScriptRunner {

    private SqlScriptRunner() {
    }

    /** @param resource z.B. {@code "sql/010_schema.sql"} */
    public static void run(Connection connection, String resource) throws SQLException, IOException {
        try (Statement statement = connection.createStatement()) {
            for (String befehl : splitStatements(read(resource))) {
                statement.execute(befehl);
            }
        }
    }

    static String read(String resource) throws IOException {
        try (InputStream in = SqlScriptRunner.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IOException("SQL-Datei nicht gefunden: " + resource);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * Zerlegt ein Skript an Semikolons am Zeilenende. Bewusst einfach: unsere
     * Schema-Dateien enthalten keine PL/pgSQL-Bloecke mit inneren Semikolons.
     * Der Modelltest (900) enthaelt welche - der wird deshalb mit psql
     * ausgefuehrt, nicht hier.
     */
    static List<String> splitStatements(String script) {
        List<String> befehle = new ArrayList<>();
        StringBuilder aktuell = new StringBuilder();
        for (String zeile : script.split("\n")) {
            String ohneKommentar = zeile.startsWith("--") ? "" : zeile;
            aktuell.append(ohneKommentar).append('\n');
            if (ohneKommentar.stripTrailing().endsWith(";")) {
                if (!aktuell.toString().isBlank()) {
                    befehle.add(aktuell.toString().trim());
                }
                aktuell.setLength(0);
            }
        }
        if (!aktuell.toString().isBlank()) {
            befehle.add(aktuell.toString().trim());
        }
        return befehle;
    }
}
