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
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
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
 * viel. Vergisst man es, bricht {@code build} mit einer Erklaerung ab.
 */
public final class Main {

    private static final String SCHEMA = "sql/010_schema.sql";
    private static final String VIEW_V1 = "sql/020_views_v1.sql";

    private static final String LEEREN_A =
            "  docker compose exec postgres_for_lf8_starter psql -U user \\\n"
            + "    -d lf8_lets_meet_db \\\n"
            + "    -c \"DROP SCHEMA public CASCADE; CREATE SCHEMA public;\"";

    public static void main(String[] args) {
        AppConfig config = AppConfig.fromEnvironment();
        Database database = new Database(config);
        String befehl = args.length == 0 ? "zeigen" : args[0];

        int exitCode;
        try {
            exitCode = fuehreAus(befehl, config, database);
        } catch (Exception e) {
            exitCode = abbruch(e);
        }
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    private static int fuehreAus(String befehl, AppConfig config, Database database)
            throws Exception {
        return switch (befehl) {
            case "zeigen" -> {
                zeigen(config, database);
                yield 0;
            }
            case "schema" -> {
                skript(database, SCHEMA);
                System.out.println("Schema angelegt (" + SCHEMA + ").");
                yield 0;
            }
            case "view" -> {
                skript(database, VIEW_V1);
                System.out.println("View angelegt (" + VIEW_V1 + ").");
                yield 0;
            }
            case "import" -> {
                bericht(new ExcelImport(config, database).run());
                yield 0;
            }
            case "check" -> pruefen(database);
            case "build" -> build(config, database);
            default -> {
                hilfe();
                yield 64;
            }
        };
    }

    // ---------------------------------------------------------------- Befehle ---

    /**
     * Der reproduzierbare Neuaufbau: Schema, View, Import, eigene Pruefungen.
     *
     * <p>Bricht ab, wenn das Schema nicht leer ist. Fuer den Akt-Abschluss
     * zaehlt der Aufbau aus einer LEEREN Datenbank - und ein zur Haelfte
     * ueberschriebenes Schema waere schlimmer als ein klarer Abbruch.
     */
    private static int build(AppConfig config, Database database) throws Exception {
        List<String> vorhanden = objekteImSchema(database);
        if (!vorhanden.isEmpty()) {
            System.out.println("Abgebrochen: das Schema 'public' ist nicht leer.");
            System.out.println();
            System.out.println("  gefunden: " + String.join(", ", vorhanden));
            System.out.println();
            System.out.println("Fuer den Akt-Abschluss zaehlt der Aufbau aus einer leeren");
            System.out.println("Datenbank. Also erst leeren, dann build:");
            System.out.println();
            System.out.println(LEEREN_A);
            System.out.println();
            System.out.println("Auf dem Schulserver genuegt: letsmeet leeren");
            return 2;
        }

        skript(database, SCHEMA);
        skript(database, VIEW_V1);
        System.out.println("Schema und View angelegt.");
        System.out.println();
        bericht(new ExcelImport(config, database).run());
        System.out.println();
        return pruefen(database);
    }

    /**
     * Fuehrt eine SQL-Datei in EINER Transaktion aus. PostgreSQL kann auch DDL
     * zurueckrollen - schlaegt eine Anweisung in der Mitte fehl, bleibt also
     * keine halb angelegte Tabelle zurueck, an der der naechste Lauf scheitert.
     */
    private static void skript(Database database, String resource) throws Exception {
        try (Connection connection = database.open()) {
            connection.setAutoCommit(false);
            try {
                SqlScriptRunner.run(connection, resource);
                connection.commit();
            } catch (Exception e) {
                connection.rollback();
                throw e;
            }
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

    /** Tabellen und Views im Schema {@code public} - fuer die Leer-Pruefung. */
    private static List<String> objekteImSchema(Database database) throws SQLException {
        String sql = "SELECT table_name FROM information_schema.tables "
                + "WHERE table_schema = 'public' ORDER BY table_name";
        List<String> namen = new ArrayList<>();
        try (Connection connection = database.open();
             Statement statement = connection.createStatement();
             ResultSet zeilen = statement.executeQuery(sql)) {
            while (zeilen.next()) {
                namen.add(zeilen.getString(1));
            }
        }
        return namen;
    }

    // ------------------------------------------------------------- Fehlerfall ---

    /**
     * Ein Abbruch soll lesbar sein und nicht als Stacktrace erscheinen. Fuer die
     * drei Fehler, die man beim Arbeiten wirklich trifft, steht der naechste
     * Schritt gleich dabei.
     */
    private static int abbruch(Exception e) {
        System.out.println();
        System.out.println("Abgebrochen: "
                + (e.getMessage() == null ? e.toString() : e.getMessage()));

        if (e instanceof NoSuchFileException) {
            System.out.println();
            System.out.println("Die Quelldatei wurde nicht gefunden. Startet das Programm im");
            System.out.println("Projektverzeichnis (dort, wo compose.yml liegt), oder setzt den Pfad:");
            System.out.println("  LETSMEET_EXCEL=\"/pfad/zu/Lets Meet DB Dump.xlsx\" java -jar ...");
        } else if (e instanceof SQLException sql) {
            String zustand = sql.getSQLState() == null ? "" : sql.getSQLState();
            if (zustand.isEmpty() || zustand.startsWith("08")) {
                System.out.println();
                System.out.println("Keine Verbindung zur Datenbank. Laeuft sie?");
                System.out.println("  Variante A: docker compose up -d");
                System.out.println("  Variante B: letsmeet up");
                System.out.println("Anderer Port? Dann PGPORT setzen, siehe AppConfig.");
            } else if ("42P07".equals(zustand) || "42710".equals(zustand)) {
                System.out.println();
                System.out.println("Das Objekt gibt es schon - vor einem Neuaufbau das Schema leeren:");
                System.out.println();
                System.out.println(LEEREN_A);
            }
        }

        if (System.getenv("LETSMEET_DEBUG") != null) {
            e.printStackTrace();
        } else {
            System.out.println();
            System.out.println("Vollstaendige Fehlerspur: LETSMEET_DEBUG=1 vor den Befehl setzen.");
        }
        return 1;
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
            List<String> objekte = objekteImSchema(database);
            if (objekte.isEmpty()) {
                System.out.println("  Schema 'public' ist leer - Startpunkt fuer 'build'.");
            } else {
                System.out.println("  im Schema 'public': " + String.join(", ", objekte));
                try (ResultSet zeilen = statement.executeQuery("SELECT count(*) FROM person")) {
                    if (zeilen.next()) {
                        System.out.println("  Personen im Bestand: " + zeilen.getInt(1));
                    }
                } catch (SQLException e) {
                    System.out.println("  Tabelle person gibt es noch nicht.");
                }
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
                Der Ablauf fuer den Akt-Abschluss steht in readme.md.
                """);
    }
}
