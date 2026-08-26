package de.letsmeet.migration.config;

import java.nio.file.Path;

/**
 * Zugangsdaten und Dateipfade an einer Stelle.
 *
 * <p>Die Standardwerte passen zu {@code compose.yml}. Wenn bei euch etwas
 * anderes gilt (z.B. ein anderer Port aus einer {@code .env}), setzt die
 * passende Umgebungsvariable - dann muss niemand Code aendern:
 * {@code PGHOST PGPORT PGDATABASE PGUSER PGPASSWORD LETSMEET_EXCEL}
 */
public record AppConfig(String jdbcUrl, String dbUser, String dbPassword, Path excelFile) {

    public static AppConfig fromEnvironment() {
        String host = env("PGHOST", "localhost");
        String port = env("PGPORT", "5432");
        String database = env("PGDATABASE", "lf8_lets_meet_db");

        return new AppConfig(
                "jdbc:postgresql://" + host + ":" + port + "/" + database,
                env("PGUSER", "user"),
                env("PGPASSWORD", "secret"),
                Path.of(env("LETSMEET_EXCEL", "Lets Meet DB Dump.xlsx")));
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return (value == null || value.isBlank()) ? fallback : value;
    }
}
