package de.letsmeet.migration.db;

import de.letsmeet.migration.config.AppConfig;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Verbindung zur PostgreSQL-Datenbank.
 *
 * <p>Absichtlich klein: eine Methode, die eine Verbindung aufmacht. Alles
 * weitere (Statements, Transaktionen, eigene Repository-Klassen) baut ihr so,
 * wie es zu eurem Import passt.
 *
 * <p>Hinweis fuer spaeter: Standardmaessig schreibt JDBC jede Anweisung sofort
 * fest (Autocommit). Wenn euer Import bei einem Fehler nicht halb fertig in der
 * Datenbank stehen bleiben soll, ist {@code connection.setAutoCommit(false)}
 * plus {@code commit()} / {@code rollback()} der naechste Schritt.
 */
public final class Database {

    private final AppConfig config;

    public Database(AppConfig config) {
        this.config = config;
    }

    public Connection open() throws SQLException {
        return DriverManager.getConnection(config.jdbcUrl(), config.dbUser(), config.dbPassword());
    }
}
