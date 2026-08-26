-- ============================================================================
-- Je Anwendungsfall eine beispielhafte Abfrage (Anforderung aus Akt 2).
--
-- Zweck ist doppelt: sie belegen, dass das Modell die Anwendungsfaelle aus
-- images/use-case.png beantworten KANN, und sie sind die Vorlage fuer die
-- Abfragen der Kundinnen-App. Die Platzhalter :email usw. setzt psql ein:
--   psql -v email="'martin.forster@web.ork'" -f 910_anwendungsfall_abfragen.sql
--
-- Auf leeren Tabellen liefern sie 0 Zeilen - gepruefte Syntax, noch keine Daten.
-- Wenn Akt 2 offen ist, erweitert das hier und schreibt dazu, welche Abfrage zu
-- welchem Anwendungsfall gehoert.
-- ============================================================================

\set ON_ERROR_STOP on
\if :{?email}
\else
  \set email '\'martin.forster@web.ork\''
\endif

-- --- Anwendungsfall "Login / am System anmelden" --------------------------
-- Die Anmeldung braucht Kennung, Passwort-Pruefwert und Rolle. Der Vergleich
-- laeuft ueber lower(email), damit die Schreibweise keine Rolle spielt - genau
-- dafuer liegt der UNIQUE-Index auf lower(email).
SELECT person_id, email, rolle, passwort_hash IS NOT NULL AS hat_passwort
FROM person
WHERE lower(email) = lower(:email);

-- --- Anwendungsfall "eigene Stammdaten bearbeiten" ------------------------
-- Alle Stammdaten einer Person in einer Zeile, ohne JOIN. Dass das ohne JOIN
-- geht, ist das Ergebnis der Modellentscheidung, die Adresse bei der Person zu
-- lassen.
SELECT vorname, nachname, geburtsdatum, strasse, plz, ort, telefon, geschlecht
FROM person
WHERE lower(email) = lower(:email);

-- --- Anwendungsfall "Hobbies bearbeiten, ergaenzen, priorisieren" ---------
-- Hobbys einer Person, wichtigstes zuerst. NULLS LAST, weil eine fehlende
-- Prioritaet (XML-Quelle) nicht als "sehr wichtig" gelten darf.
SELECT h.bezeichnung, ph.prioritaet, ph.quelle
FROM person p
JOIN person_hobby ph ON ph.person_id = p.person_id
JOIN hobby h         ON h.hobby_id   = ph.hobby_id
WHERE lower(p.email) = lower(:email)
ORDER BY ph.prioritaet DESC NULLS LAST, h.bezeichnung;

-- --- Anwendungsfall "Name und Hobbies eines ausgewaehlten Nutzers ausgeben"
-- Eine Zeile je Person, Hobbys zusammengefasst - so wie eine Anzeige es braucht.
SELECT p.vorname, p.nachname, string_agg(h.bezeichnung, ', ' ORDER BY h.bezeichnung) AS hobbys
FROM person p
LEFT JOIN person_hobby ph ON ph.person_id = p.person_id
LEFT JOIN hobby h         ON h.hobby_id   = ph.hobby_id
WHERE lower(p.email) = lower(:email)
GROUP BY p.person_id, p.vorname, p.nachname;

-- --- Anwendungsfall "Nutzer mit aehnlichen Interessen finden" -------------
-- Zaehlt gemeinsame Hobbys. DISTINCT ist noetig, weil dasselbe Hobby aus
-- mehreren Quellen kommen darf und ein Treffer sonst doppelt zaehlt - das ist
-- die Kehrseite von quelle im Primaerschluessel.
SELECT andere.email,
       andere.vorname,
       andere.nachname,
       count(DISTINCT ph2.hobby_id) AS gemeinsame_hobbys
FROM person ich
JOIN person_hobby ph1 ON ph1.person_id = ich.person_id
JOIN person_hobby ph2 ON ph2.hobby_id  = ph1.hobby_id
JOIN person andere    ON andere.person_id = ph2.person_id
WHERE lower(ich.email) = lower(:email)
  AND andere.person_id <> ich.person_id
GROUP BY andere.person_id, andere.email, andere.vorname, andere.nachname
ORDER BY gemeinsame_hobbys DESC
LIMIT 10;

-- --- Anwendungsfall "andere Teilnehmer kontaktieren" ----------------------
-- Der Nachrichtenverlauf mit einer Person, beide Richtungen in einer Liste.
SELECT n.gesendet_am,
       s.email AS von,
       e.email AS an,
       n.inhalt,
       n.konversation_id
FROM nachricht n
JOIN person s ON s.person_id = n.sender_id
JOIN person e ON e.person_id = n.empfaenger_id
WHERE lower(s.email) = lower(:email) OR lower(e.email) = lower(:email)
ORDER BY n.gesendet_am;

-- --- Anwendungsfall "gegenseitig in Freundeliste aufnehmen" ---------------
-- Freundesliste. Weil eine Freundschaft nur EINMAL gespeichert ist (CHECK
-- person_a_id < person_b_id), muessen hier beide Spalten geprueft werden - das
-- ist der Preis der symmetrischen Speicherung.
SELECT freund.email, freund.vorname, freund.nachname, f.befreundet_seit
FROM person ich
JOIN freundschaft f ON ich.person_id IN (f.person_a_id, f.person_b_id)
JOIN person freund  ON freund.person_id = CASE
                          WHEN f.person_a_id = ich.person_id THEN f.person_b_id
                          ELSE f.person_a_id
                       END
WHERE lower(ich.email) = lower(:email)
ORDER BY freund.nachname, freund.vorname;

-- Offene Likes, die auf eine Zustimmung warten - der Weg zur Freundschaft
-- laut Anwendungsfall ("nach beiderseitiger Zustimmung").
SELECT von.email AS moechte_mich, l.status, l.zeitpunkt
FROM person_like l
JOIN person von ON von.person_id = l.von_person_id
JOIN person ich ON ich.person_id = l.an_person_id
WHERE lower(ich.email) = lower(:email)
  AND l.status = 'pending'
ORDER BY l.zeitpunkt DESC;

-- --- Anwendungsfall "Foto anfuegen, aendern, loeschen" --------------------
-- Profilbild und weitere Fotos einer Person. length(daten) statt daten: die
-- Bilddaten selbst will man in einer Uebersicht nicht laden.
SELECT f.art, f.mime_typ, length(f.daten) AS bytes, f.url, f.hochgeladen_am
FROM person p
JOIN foto f ON f.person_id = p.person_id
WHERE lower(p.email) = lower(:email)
ORDER BY (f.art = 'profil') DESC, f.hochgeladen_am;

-- --- Anwendungsfall "alle Daten bearbeiten" (Administrator) ---------------
-- Wer darf das? Genau die Rolle, die das Anwendungsfalldiagramm dafuer vorsieht.
SELECT email, rolle FROM person WHERE rolle = 'administrator' ORDER BY email;
