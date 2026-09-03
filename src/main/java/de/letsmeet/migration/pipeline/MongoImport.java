package de.letsmeet.migration.pipeline;

import de.letsmeet.migration.config.AppConfig;
import de.letsmeet.migration.db.Database;
import de.letsmeet.migration.db.SqlScriptRunner;
import de.letsmeet.migration.source.mongo.MongoUserReader;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

/**
 * Die MongoDB-Nachlieferung: die Rohdokumente landen in {@code mongo_raw(doc
 * jsonb)}, den Rest erledigt {@code 050_import_mongo.sql} (Stammdaten-Abgleich,
 * Likes, Nachrichten). Alles in einer Transaktion.
 */
public final class MongoImport {

    private final AppConfig config;
    private final Database database;
    private List<String> docs;

    public MongoImport(AppConfig config, Database database) {
        this.config = config;
        this.database = database;
    }

    /** Liest die Quelle. Wirft, wenn die MongoDB nicht laeuft - fuer den
     *  Vorab-Check in {@code build}, bevor am Schema etwas passiert. */
    public void pruefeQuelle() {
        docs = new MongoUserReader(config.mongoUri(), config.mongoDatabase()).readJson();
    }

    public ImportReport run() throws Exception {
        if (docs == null) {
            pruefeQuelle();
        }
        ImportReport bericht = new ImportReport("MongoDB-Import");
        bericht.count("Dokumente in der Quelle", docs.size());

        try (Connection connection = database.open()) {
            connection.setAutoCommit(false);
            try {
                try (Statement statement = connection.createStatement()) {
                    statement.execute("CREATE TABLE mongo_raw (doc jsonb)");
                }
                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO mongo_raw (doc) VALUES (?::jsonb)")) {
                    for (String json : docs) {
                        statement.setString(1, json);
                        statement.addBatch();
                    }
                    statement.executeBatch();
                }
                SqlScriptRunner.run(connection, "sql/050_import_mongo.sql");
                bericht.count("Likes geschrieben", zaehle(connection, "person_like"));
                bericht.count("Nachrichten geschrieben", zaehle(connection, "nachricht"));
                connection.commit();
            } catch (Exception e) {
                connection.rollback();
                throw e;
            }
        }
        return bericht;
    }

    private static int zaehle(Connection connection, String tabelle) throws Exception {
        try (Statement statement = connection.createStatement();
             ResultSet zeilen = statement.executeQuery("SELECT count(*) FROM " + tabelle)) {
            return zeilen.next() ? zeilen.getInt(1) : 0;
        }
    }
}
