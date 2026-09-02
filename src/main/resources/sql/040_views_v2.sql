-- ============================================================================
-- Datenvertrag Akt 2 (V2) - die Schnittstelle zur Kundinnen-App.
-- Spaltennamen und -typen sind vorgegeben (readme.md) und muessen exakt stimmen.
-- Wie in 020_views_v1.sql: deutsche Tabellen, englische Views, Uebersetzung per AS.
--
-- Die E-Mail kommt in jeder View aus person.email (Excel-Schreibweise, Vertrag),
-- nie aus einer Like-/Nachrichtenzeile. Likes und Nachrichten sind gerichtet:
-- ausloesende Person / Absender links.
-- ============================================================================

DROP VIEW IF EXISTS migration_users;
DROP VIEW IF EXISTS migration_user_interests;
DROP VIEW IF EXISTS migration_user_hobbies;
DROP VIEW IF EXISTS migration_likes;
DROP VIEW IF EXISTS migration_messages;

-- V2 haengt phone und gender an den V1-Spaltensatz an.
CREATE VIEW migration_users AS
SELECT p.email        AS email,
       p.vorname      AS first_name,
       p.nachname     AS last_name,
       p.geburtsdatum AS birth_date,
       p.plz          AS postal_code,
       p.ort          AS city,
       p.telefon      AS phone,
       p.geschlecht   AS gender
FROM person p;

CREATE VIEW migration_user_interests AS
SELECT p.email AS email, pi.interesse_code AS interest_code
FROM person p
JOIN person_interesse pi ON pi.person_id = p.person_id;

CREATE VIEW migration_user_hobbies AS
SELECT p.email       AS email,
       h.bezeichnung AS hobby_name,
       ph.prioritaet AS priority,
       ph.quelle     AS source
FROM person p
JOIN person_hobby ph ON ph.person_id = p.person_id
JOIN hobby h         ON h.hobby_id   = ph.hobby_id;

CREATE VIEW migration_likes AS
SELECT vp.email AS liker_email, ap.email AS liked_email,
       l.status AS status, l.zeitpunkt AS liked_at
FROM person_like l
JOIN person vp ON vp.person_id = l.von_person_id
JOIN person ap ON ap.person_id = l.an_person_id;

CREATE VIEW migration_messages AS
SELECT sp.email          AS sender_email,
       rp.email          AS receiver_email,
       n.inhalt          AS body,
       n.gesendet_am     AS sent_at,
       n.konversation_id AS conversation_id
FROM nachricht n
JOIN person sp ON sp.person_id = n.sender_id
JOIN person rp ON rp.person_id = n.empfaenger_id;
