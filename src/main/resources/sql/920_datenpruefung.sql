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

-- ============================================================================
-- Akt 2 - Mengen, Eindeutigkeit, Referenzen, Transformationsregeln.
-- Die Zeilen aus der MongoDB pruefen "entweder 0 oder vollstaendig": beim
-- V1-Aufbau (build v1, ohne MongoDB) sind Likes/Nachrichten leer, das ist ok.
-- Pruefungen auf noch fehlende Views ueberspringt der SqlCheckRunner.
-- ============================================================================

-- @check Hobby-Woerterbuch: 220 Namen aus Excel, keiner leer
-- Annahme ueber DIESE Lieferung - eine neue Datei aendert die Zahl, dann faellt es auf.
SELECT count(*) AS ist, 220 AS soll FROM hobby HAVING count(*) <> 220;

-- @check Kein Hobbyname ist leer oder hat Randleerzeichen
SELECT hobby_id, bezeichnung FROM hobby
WHERE bezeichnung = '' OR bezeichnung <> btrim(bezeichnung);

-- @check Hobbyzuordnungen: 4828 Zeilen aus Excel
SELECT count(*) AS ist, 4828 AS soll FROM person_hobby HAVING count(*) <> 4828;

-- @check Jede Hobbyzuordnung aus Excel hat eine Prioritaet (Excel liefert immer eine)
SELECT person_id, hobby_id FROM person_hobby WHERE quelle = 'excel' AND prioritaet IS NULL;

-- @check In Akt 2 ist jede Hobby-Quelle 'excel' (die XML-Nachlieferung gehoert spaeter dazu)
SELECT DISTINCT quelle FROM person_hobby WHERE quelle <> 'excel';

-- @check Prioritaeten der Lieferung liegen in 0..100 (vereinbart ist -100..100)
SELECT person_id, hobby_id, prioritaet FROM person_hobby
WHERE prioritaet < 0 OR prioritaet > 100;

-- @check Kein Hobby doppelt bei derselben Person aus derselben Quelle
-- (der Primaerschluessel erzwingt das schon - diese Pruefung macht es sichtbar)
SELECT person_id, hobby_id, quelle, count(*) FROM person_hobby
GROUP BY 1, 2, 3 HAVING count(*) > 1;

-- @check Interessen: 1609 Zeilen (1576 Personen, "mw" 33x als zwei Zeilen)
SELECT count(*) AS ist, 1609 AS soll FROM person_interesse HAVING count(*) <> 1609;

-- @check Interessen-Codes sind nur die aufgeloesten Rohwerte m und w
-- "mw" darf nach der Aufloesung nicht mehr vorkommen.
SELECT interesse_code, count(*) FROM person_interesse
WHERE interesse_code NOT IN ('m', 'w') GROUP BY 1;

-- @check Jede Person hat mindestens ein Interesse (Quelle: alle 1576 Zeilen gefuellt)
SELECT p.person_id FROM person p
LEFT JOIN person_interesse pi ON pi.person_id = p.person_id
WHERE pi.person_id IS NULL;

-- @check Likes: entweder 0 (V1-Aufbau) oder vollstaendig 500 (MongoDB)
SELECT count(*) AS ist FROM person_like HAVING count(*) NOT IN (0, 500);

-- @check Kein Like auf sich selbst (CHECK erzwingt es - Pruefung macht es sichtbar)
SELECT von_person_id FROM person_like WHERE von_person_id = an_person_id;

-- @check Jeder Like hat einen Zeitpunkt
SELECT von_person_id, an_person_id FROM person_like WHERE zeitpunkt IS NULL;

-- @check Kein Gegen-Like als zweite Zeile (Gegenseitigkeit steckt im status 'mutual')
SELECT a.von_person_id, a.an_person_id FROM person_like a
JOIN person_like b ON b.von_person_id = a.an_person_id AND b.an_person_id = a.von_person_id;

-- @check Nachrichten: entweder 0 (V1-Aufbau) oder vollstaendig 300 (MongoDB)
SELECT count(*) AS ist FROM nachricht HAVING count(*) NOT IN (0, 300);

-- @check Jede Nachricht hat Inhalt, Zeitpunkt und eine konversation_id in 1..50
SELECT nachricht_id FROM nachricht
WHERE inhalt IS NULL OR btrim(inhalt) = '' OR gesendet_am IS NULL
   OR konversation_id IS NULL OR konversation_id NOT BETWEEN 1 AND 50;

-- @check conversation_id bezeichnet keine Zweierkonversation
-- Gegenprobe zur Modellentscheidung E3: die id kommt bei mehreren
-- Teilnehmerpaaren vor, ist also kein Fremdschluessel auf eine Konversation.
SELECT 1 WHERE (SELECT count(*) FROM nachricht) > 0 AND (
    SELECT count(*) FROM (
        SELECT konversation_id
        FROM (SELECT DISTINCT konversation_id,
                     least(sender_id, empfaenger_id)    AS a,
                     greatest(sender_id, empfaenger_id) AS b
              FROM nachricht) paare
        GROUP BY konversation_id HAVING count(*) > 1
    ) mehrdeutig
) < 40;

-- @check angelegt_am / geaendert_am: entweder bei keiner oder bei allen Personen
-- gefuellt (V1-Aufbau laesst sie leer, die MongoDB fuellt sie fuer alle)
SELECT person_id FROM person
WHERE (angelegt_am IS NULL OR geaendert_am IS NULL)
  AND EXISTS (SELECT 1 FROM person WHERE angelegt_am IS NOT NULL);

-- @check geaendert_am liegt nicht vor angelegt_am
SELECT person_id, angelegt_am, geaendert_am FROM person WHERE geaendert_am < angelegt_am;

-- @check freundschaft ist leer - das friends-Array der MongoDB ist bei allen Dokumenten leer
SELECT person_a_id, person_b_id FROM freundschaft;

-- @check Jede V2-View zeigt so viele Zeilen wie ihre Basistabelle
SELECT 'migration_user_interests' WHERE
    (SELECT count(*) FROM migration_user_interests) <> (SELECT count(*) FROM person_interesse);
-- (weitere Views analog)

-- @check migration_likes ist gerichtet: liker_email ist die ausloesende Person
-- Stichprobe gegen person_like: jede View-Zeile muss eine Tabellenzeile mit
-- passender Richtung haben.
SELECT l.liker_email, l.liked_email FROM migration_likes l
LEFT JOIN (
    SELECT vp.email AS liker, ap.email AS liked
    FROM person_like pl
    JOIN person vp ON vp.person_id = pl.von_person_id
    JOIN person ap ON ap.person_id = pl.an_person_id
) t ON t.liker = l.liker_email AND t.liked = l.liked_email
WHERE t.liker IS NULL;
