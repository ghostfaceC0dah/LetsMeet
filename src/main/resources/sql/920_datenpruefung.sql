-- ============================================================================
-- Eigene Datenpruefungen NACH dem Import (Auftrag Akt 1, Punkt 5).
--
-- Der Kundinnen-Checker ergaenzt diese, ersetzt sie aber nicht: er prueft den
-- Datenvertrag, wir pruefen unsere eigenen Annahmen ueber die Quelle.
--
-- Jede Abfrage MUSS 0 Zeilen liefern. Liefert sie welche, ist das ein Befund -
-- entweder ist der Import falsch oder unsere Annahme war falsch. Beides gehoert
-- in die Befundnotiz.
--
-- Aufruf: java -jar target/letsmeet-migration.jar check
-- Trennzeichen zwischen den Pruefungen: eine Zeile "-- @check <Name>".
-- ============================================================================

-- @check Anzahl Personen entspricht der Quelle (1576 Datenzeilen, 0 Abweisungen)
-- Diese Zahl ist eine Annahme ueber DIESE Lieferung. Kommt eine neue Datei,
-- muss sie angepasst werden - dann faellt die Aenderung wenigstens auf.
SELECT count(*) AS ist, 1576 AS soll FROM person HAVING count(*) <> 1576;

-- @check email ist nicht leer
SELECT person_id FROM person WHERE btrim(email) = '';

-- @check email ist eindeutig, Gross-/Kleinschreibung egal
SELECT lower(email), count(*) FROM person GROUP BY 1 HAVING count(*) > 1;

-- @check Vor- und Nachname sind gefuellt
SELECT email FROM person WHERE btrim(nachname) = '' OR btrim(vorname) = '';

-- @check Adresse ist vollstaendig zerlegt (drei Teile, keiner leer)
SELECT email FROM person
WHERE strasse IS NULL OR plz IS NULL OR ort IS NULL
   OR btrim(strasse) = '' OR btrim(plz) = '' OR btrim(ort) = '';

-- @check Geburtsdatum ist gefuellt und liegt nicht in der Zukunft
SELECT email, geburtsdatum FROM person
WHERE geburtsdatum IS NULL OR geburtsdatum > current_date;

-- @check Geburtsdatum ist plausibel (keine Person aelter als 1900)
SELECT email, geburtsdatum FROM person WHERE geburtsdatum < DATE '1900-01-01';

-- @check Postleitzahl besteht nur aus Ziffern
-- Kein CHECK-Constraint, weil die PLZ ein Rohwert ist - Plausibilitaet gehoert
-- in diese Abfrage, nicht ins Modell (siehe docs/datenmodell.md, Phase 4).
SELECT email, plz FROM person WHERE plz !~ '^[0-9]+$';

-- @check Postleitzahl ist vier- oder fuenfstellig
SELECT email, plz FROM person WHERE length(plz) NOT IN (4, 5);

-- @check Geschlecht enthaelt nur die drei bekannten Rohwerte
-- Auffaellig, nicht zwangslaeufig falsch: ein neuer Wert bedeutet, dass die
-- Quellenanalyse nachgezogen werden muss.
SELECT geschlecht, count(*) FROM person
WHERE geschlecht IS NOT NULL AND geschlecht NOT IN ('m', 'w', 'nb')
GROUP BY 1;

-- @check Die View zeigt genau so viele Zeilen wie die Tabelle
SELECT 1 WHERE (SELECT count(*) FROM migration_users) <> (SELECT count(*) FROM person);

-- @check Kein Ort ist versehentlich am Komma abgeschnitten worden
-- Gegenprobe zur Regel "der Ort ist alles nach dem zweiten Komma": nach dem
-- Import muss es die drei Personen mit "Demmin, Hansestadt" geben.
SELECT 1 WHERE (SELECT count(*) FROM person WHERE ort LIKE '%,%') = 0;
