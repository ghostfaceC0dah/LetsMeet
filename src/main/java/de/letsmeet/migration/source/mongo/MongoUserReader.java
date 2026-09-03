package de.letsmeet.migration.source.mongo;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.MongoException;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import de.letsmeet.migration.parse.SourceDataException;
import org.bson.Document;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Liest die Sammlung {@code users} und gibt die Dokumente als JSON zurueck. Die
 * Auswertung passiert danach in SQL ({@code 050_import_mongo.sql}).
 */
public final class MongoUserReader {

    private final MongoClientSettings settings;
    private final String datenbank;

    public MongoUserReader(String uri, String datenbank) {
        // 5s Server-Auswahl: laeuft die MongoDB nicht, bricht der Import mit
        // Meldung ab statt lange zu haengen.
        this.settings = MongoClientSettings.builder()
                .applyConnectionString(new ConnectionString(uri))
                .applyToClusterSettings(b -> b.serverSelectionTimeout(5, TimeUnit.SECONDS))
                .build();
        this.datenbank = datenbank;
    }

    /** @return ein JSON-String je Dokument der Sammlung {@code users} */
    public List<String> readJson() {
        List<String> docs = new ArrayList<>();
        try (MongoClient client = MongoClients.create(settings)) {
            for (Document doc : client.getDatabase(datenbank).getCollection("users").find()) {
                docs.add(doc.toJson());
            }
        } catch (MongoException e) {
            throw new SourceDataException("MongoDB nicht erreichbar (" + e.getMessage() + ")");
        }
        return docs;
    }
}
