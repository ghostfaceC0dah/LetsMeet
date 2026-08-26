# Startpunkt fuer die Entwicklung

Der fachliche Auftrag steht in [readme.md](readme.md) - **die ist verbindlich,
diese Datei erklaert nur das Geruest.** Beobachtungen und Entscheidungen gehoeren
in [results/befundnotiz.md](results/befundnotiz.md).

## Losfahren

```bash
docker compose up -d
```

```bash
mvn package
```

```bash
java -jar target/letsmeet-migration.jar
```

Der letzte Befehl importiert nichts. Er zeigt, ob die Datenbank erreichbar ist,
wie viele Personen im Bestand sind, und druckt die erste Excel-Zeile
**unveraendert**. Damit sieht man auf einen Blick, ob die Umgebung steht.

Der Neuaufbau - Schema, View, Import und eigene Pruefungen in einem Befehl:

```bash
java -jar target/letsmeet-migration.jar build
```

In IntelliJ genauso moeglich: `Main` oeffnen und ausfuehren, Maven-Fenster fuer
`package` und `test`.

## Was schon da ist, und was nicht

**Akt 1 ist fertig und gruen** (Pruefstand V1, Exit-Code 0). Was da ist und was
noch fehlt:

| Fertig | Offen |
|---|---|
| Datenmodell samt DDL, View V1, Modelltest | Die fuenf Views fuer Akt 2 (`040_views_v2.sql`) |
| Zerlegeregeln in `RowSplitter` + 16 Tests | Import der XML-Nachlieferung (spaeterer Akt) |
| Excel-Import mit Abweisungsprotokoll | Import der MongoDB (Akt 2) |
| 12 eigene Datenpruefungen (`check`) | Antworten auf die offenen Kundinnenfragen |
| Befundnotiz mit Quellenanalyse und Entscheidungen | ERD-Share-URL registrieren (Akt 2) |

Die Tests in `RowSplitterTest` sind die Aufgabenliste der Zerlegeregeln - vier
davon sind die Stolperstellen aus dem Datenvertrag, an denen ein naives
`split(", ")` scheitert. Wenn ihr eine Regel aendert, aendert zuerst den Test.

## Der Ablauf fuer den Akt-Abschluss

Fuer den Abschluss zaehlt nicht der Zustand der Datenbank, sondern dass die
Skripte ihn aus dem Nichts erzeugen. Immer diese drei Schritte, in dieser
Reihenfolge - **leeren, importieren, pruefen**:

```bash
docker compose exec postgres_for_lf8_starter psql -U user -d lf8_lets_meet_db -c "DROP SCHEMA public CASCADE; CREATE SCHEMA public;"
```

```bash
java -jar target/letsmeet-migration.jar build
```

```bash
docker compose run --rm -e CONTRACT_VERSION=V1 kundinnen_app node server/dist/cli.js
```

Beide muessen mit Exit-Code 0 enden. Wenn ihr am Schema etwas aendert, fahrt
zusaetzlich den Modelltest:

```bash
docker exec -i lf8_lets_meet_postgres_container psql -U user -d lf8_lets_meet_db -v ON_ERROR_STOP=1 < src/main/resources/sql/900_modelltest.sql
```

## Wo was liegt

```
src/main/java/de/letsmeet/migration/
  Main.java              Einstiegspunkt und Befehle (build, import, check, ...)
  config/AppConfig.java  Zugangsdaten und Pfade (ueber Umgebungsvariablen aenderbar)
  db/                    Verbindung, SQL-Skripte ausfuehren, Personen schreiben, Pruefungen
  source/excel/          Excel lesen - roh, ungeprueft
  parse/RowSplitter.java die Zerlegeregeln des Datenvertrags
  model/Person.java      eine migrierte Person
  pipeline/              lesen -> zerlegen -> schreiben, mit Abweisungsprotokoll

src/main/resources/sql/  Struktur der Datenbank, versioniert und ohne Java lesbar
  010_schema.sql         Kern: Tabelle person
  020_views_v1.sql       Datenvertrag V1
  030_schema_akt2.sql    Erweiterung fuer Akt 2
  040_views_v2.sql       Datenvertrag V2 - noch offen
  900_modelltest.sql     prueft, ob das Modell abweist, was es abweisen muss
  910_anwendungsfall_abfragen.sql   je Anwendungsfall eine Abfrage
  920_datenpruefung.sql  eigene Pruefungen nach dem Import
docs/datenmodell.md      Entwurf des Datenmodells mit Begruendungen
src/test/java/...        eure Tests
results/befundnotiz.md   das durchlaufende Dokument
results/abgewiesen.csv   nicht importierte Zeilen mit Grund
```

Verbindlich ist nicht diese Ordnerstruktur, sondern dass ein anderes Team eure
Entscheidungen nachvollziehen und den Aufbau wiederholen kann. Verschiebt also
ruhig, wenn euch etwas anderes besser dient - aber schreibt in die Befundnotiz,
warum.

## Umgebung

Standardwerte passen zu `compose.yml`. Abweichungen (z.B. anderer Port aus einer
`.env`) ueber Umgebungsvariablen, ohne Codeaenderung:

```bash
PGHOST PGPORT PGDATABASE PGUSER PGPASSWORD LETSMEET_EXCEL
```

Der erste `mvn`-Lauf braucht Internet (Abhaengigkeiten laden), danach laeuft auch
`mvn -o package`.
