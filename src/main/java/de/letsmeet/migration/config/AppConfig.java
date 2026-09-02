package de.letsmeet.migration.config;

import java.nio.file.Path;

/**
 * Zugangsdaten und Dateipfade an einer Stelle.
 *
 * <p>Die Standardwerte passen zu {@code compose.yml}. Wenn bei euch etwas
 * anderes gilt (z.B. ein anderer Port aus einer {@code .env}), setzt die
 * passende Umgebungsvariable - dann muss niemand Code aendern:
 * {@code PGHOST PGPORT PGDATABASE PGUSER PGPASSWORD LETSMEET_EXCEL LETSMEET_MONGO_URI}
 *
 * <p>Die MongoDB laeuft im Compose-Netz als {@code mongodb_for_lf8}; von aussen
 * (der Import laeuft auf dem Host) ist sie unter {@code localhost:27017}
 * erreichbar. Die Datenbank heisst {@code LetsMeet} und steckt in der URI.
 */
public record AppConfig(String jdbcUrl, String dbUser, String dbPassword,
                        Path excelFile, String mongoUri, String mongoDatabase) {

    public static AppConfig fromEnvironment() {
        String host = env("PGHOST", "localhost");
        String port = env("PGPORT", "5432");
        String database = env("PGDATABASE", "lf8_lets_meet_db");

        return new AppConfig(
                "jdbc:postgresql://" + host + ":" + port + "/" + database,
                env("PGUSER", "user"),
                env("PGPASSWORD", "secret"),
                Path.of(env("LETSMEET_EXCEL", "Lets Meet DB Dump.xlsx")),
                env("LETSMEET_MONGO_URI", "mongodb://localhost:27017"),
                env("LETSMEET_MONGO_DB", "LetsMeet"));
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return (value == null || value.isBlank()) ? fallback : value;
    }
}
