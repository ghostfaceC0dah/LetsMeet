package de.letsmeet.migration;

import de.letsmeet.migration.config.AppConfig;
import de.letsmeet.migration.db.Database;
import de.letsmeet.migration.db.SqlCheckRunner;
import de.letsmeet.migration.db.SqlScriptRunner;
import de.letsmeet.migration.pipeline.ExcelImport;
import de.letsmeet.migration.pipeline.ImportReport;
import de.letsmeet.migration.source.excel.ExcelUserReader;
import de.letsmeet.migration.source.excel.ExcelUserRow;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

/**
 * Einstiegspunkt.
 *
 * <pre>
 * mvn package
 * java -jar target/letsmeet-migration.jar          zeigt Umgebung und Rohdaten
 * java -jar target/letsmeet-migration.jar build    Schema + View + Import + Pruefungen
 * </pre>
 *
 * Der Abschluss von Akt 1 - leeren, importieren, pruefen:
 * <pre>
 * docker compose exec postgres_for_lf8_starter psql -U user -d lf8_lets_meet_db \
 *   -c "DROP SCHEMA public CASCADE; CREATE SCHEMA public;"
 * java -jar target/letsmeet-migration.jar build
 * docker compose run --rm -e CONTRACT_VERSION=V1 kundinnen_app node server/dist/cli.js
 * </pre>
 *
 * Das Leeren macht absichtlich der dokumentierte Handbefehl und nicht dieses
 * Programm: ein Importwerkzeug mit eingebautem DROP SCHEMA ist ein Werkzeug zu
 * viel.
 */
public final class Main {

    private static final String SCHEMA = "sql/010_schema.sql";
    private static final String VIEW_V1 = "sql/020_views_v1.sql";

    public static void main(String[] args) throws Exception {
        AppConfig config = AppConfig.fromEnvironment();
        Database database = new Database(config);
        String befehl = args.length == 0 ? "zeigen" : args[0];
        int exitCode = 0;

        switch (befehl) {
            case "zeigen" -> zeigen(config, database);
            case "schema" -> {
                skript(database, SCHEMA);
                System.out.println("Schema angelegt (" + SCHEMA + ").");
            }
            case "view" -> {
                skript(database, VIEW_V1);
                System.out.println("View angelegt (" + VIEW_V1 + ").");
            }
            case "import" -> bericht(new ExcelImport(config, database).run());
            case "check" -> exitCode = pruefen(database);
            case "build" -> {
                skript(database, SCHEMA);
                skript(database, VIEW_V1);
                System.out.println("Schema und View angelegt.");
                System.out.println();
                bericht(new ExcelImport(config, database).run());
                System.out.println();
                exitCode = pruefen(database);
            }
            default -> {
                hilfe();
                exitCode = 64;
            }
        }

        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    // ---------------------------------------------------------------- Befehle ---

    private static void skript(Database database, String resource) throws Exception {
        try (Connection connection = database.open()) {
            SqlScriptRunner.run(connection, resource);
        }
    }

    private static void bericht(ImportReport report) throws Exception {
        StringBuilder out = new StringBuilder();
        report.printTo(out);
        System.out.print(out);
        report.writeRejections(Path.of("results"));
    }

    /** @return 0 wenn alle Pruefungen durchlaufen, sonst 2 */
    private static int pruefen(Database database) throws Exception {
        System.out.println("== eigene Datenpruefungen ==");
        int exitCode = 0;
        try (Connection connection = database.open()) {
            connection.setAutoCommit(false);   // die Pruefungen arbeiten mit Savepoints
            for (SqlCheckRunner.Result ergebnis : new SqlCheckRunner().runAll(connection)) {
                System.out.println(ergebnis);
                if (ergebnis.failed()) {
                    exitCode = 2;
                }
            }
            connection.rollback();
        }
        return exitCode;
    }

    // -------------------------------------------------- Umgebung und Rohdaten ---

    private static void zeigen(AppConfig config, Database database) {
        System.out.println("=== LetsMeet-Migration: Umgebung pruefen ===");
        System.out.println("Datenbank: " + config.jdbcUrl() + " (Benutzer " + config.dbUser() + ")");
        System.out.println("Excel:     " + config.excelFile().toAbsolutePath());
        System.out.println();
        datenbank(config, database);
        System.out.println();
        excel(config);
        System.out.println();
        hilfe();
    }

    private static void datenbank(AppConfig config, Database database) {
        System.out.println("--- PostgreSQL ---");
        try (Connection connection = database.open();
             Statement statement = connection.createStatement()) {

            try (ResultSet version = statement.executeQuery("SELECT version()")) {
                if (version.next()) {
                    System.out.println("verbunden: " + version.getString(1).split(",")[0]);
                }
            }
            try (ResultSet zeilen = statement.executeQuery("""
                    SELECT table_type, count(*)
                    FROM information_schema.tables
                    WHERE table_schema = 'public'
                    GROUP BY table_type ORDER BY table_type
                    """)) {
                boolean etwas = false;
                while (zeilen.next()) {
                    System.out.println("  " + zeilen.getString(1) + ": " + zeilen.getInt(2));
                    etwas = true;
                }
                if (!etwas) {
                    System.out.println("  Schema 'public' ist leer - Startpunkt fuer 'build'.");
                }
            }
            try (ResultSet zeilen = statement.executeQuery(
                    "SELECT count(*) FROM person")) {
                if (zeilen.next()) {
                    System.out.println("  Personen im Bestand: " + zeilen.getInt(1));
                }
            } catch (Exception e) {
                System.out.println("  Tabelle person gibt es noch nicht.");
            }
        } catch (Exception e) {
            System.out.println("KEINE VERBINDUNG: " + e.getMessage());
            System.out.println("  Laeuft die Datenbank? Variante A: 'docker compose up -d',");
            System.out.println("  Variante B: 'letsmeet up'. Anderer Port? Siehe AppConfig.");
        }
    }

    private static void excel(AppConfig config) {
        System.out.println("--- Excel-Quelle ---");
        if (!Files.exists(config.excelFile())) {
            System.out.println("Datei nicht gefunden. Startet das Programm im Projektverzeichnis,");
            System.out.println("oder setzt LETSMEET_EXCEL auf den Pfad.");
            return;
        }
        try {
            List<ExcelUserRow> zeilen = new ExcelUserReader().read(config.excelFile());
            System.out.println("Datenzeilen gelesen: " + zeilen.size());
            if (!zeilen.isEmpty()) {
                ExcelUserRow erste = zeilen.get(0);
                System.out.println("Erste Zeile, unveraendert:");
                System.out.println("    \"" + erste.nameCell() + "\" | \"" + erste.addressCell()
                        + "\" | \"" + erste.email() + "\" | \"" + erste.birthDateCell() + "\"");
            }
        } catch (Exception e) {
            System.out.println("Lesen fehlgeschlagen: " + e);
        }
    }

    private static void hilfe() {
        System.out.println("""
                --- Befehle ---
                  (ohne)    Umgebung pruefen und Rohdaten zeigen
                  build     Schema + View + Import + eigene Pruefungen  <- der Neuaufbau
                  schema    nur die Tabelle person anlegen
                  view      nur die View migration_users anlegen
                  import    nur den Excel-Import
                  check     nur die eigenen Datenpruefungen

                Exit-Code 0 = alles durchgelaufen, 2 = offener Befund.
                Ablauf fuer den Akt-Abschluss steht in START.md.
                """);
    }
}
