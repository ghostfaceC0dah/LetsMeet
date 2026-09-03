-- ============================================================================
-- MongoDB-Nachlieferung (Akt 2).
--
-- Die Rohdokumente der Sammlung "users" stehen als JSON in mongo_raw(doc jsonb)
-- (gefuellt von MongoImport.java). Alles Weitere passiert hier in SQL, verbunden
-- wird ueber lower(email).
--
-- Regeln aus dem Datenvertrag / der Befundnotiz:
--   * Stammdaten-Konflikt Excel <-> MongoDB: die MongoDB gewinnt (juengere
--     Lieferung). Ausnahme: Platzhalter phone = '0' und Leerwerte.
--   * Likes und Nachrichten sind gerichtet: ausloesende Person / Absender links.
--   * conversation_id wird unveraendert mitgefuehrt, nicht gedeutet.
-- ============================================================================

-- Zeitpunkte: die Quelle mischt ISO (2024-04-17 19:49:47, auch mit 'T') und
-- deutsch (25.12.2023 13:57:19 - zwei Faelle).
CREATE FUNCTION mongo_ts(t text) RETURNS timestamp
LANGUAGE sql IMMUTABLE AS $$
    SELECT CASE
        WHEN t ~ '^[0-9]{4}-' THEN replace(t, 'T', ' ')::timestamp
        ELSE to_timestamp(t, 'DD.MM.YYYY HH24:MI:SS')::timestamp
    END
$$;

UPDATE person p SET
    nachname     = left(m.doc->>'name', position(', ' in m.doc->>'name') - 1),
    vorname      = substr(m.doc->>'name', position(', ' in m.doc->>'name') + 2),
    telefon      = CASE WHEN coalesce(m.doc->>'phone', '') IN ('', '0')
                        THEN p.telefon ELSE m.doc->>'phone' END,
    angelegt_am  = (m.doc->>'createdAt')::timestamp,
    geaendert_am = (m.doc->>'updatedAt')::timestamp
FROM mongo_raw m
WHERE lower(p.email) = lower(m.doc->>'_id');

INSERT INTO person_like (von_person_id, an_person_id, status, zeitpunkt)
SELECT vp.person_id, ap.person_id, l->>'status', mongo_ts(l->>'timestamp')
FROM mongo_raw m
CROSS JOIN LATERAL jsonb_array_elements(coalesce(m.doc->'likes', '[]'::jsonb)) AS l
JOIN person vp ON lower(vp.email) = lower(m.doc->>'_id')
JOIN person ap ON lower(ap.email) = lower(l->>'liked_email');

INSERT INTO nachricht (sender_id, empfaenger_id, inhalt, gesendet_am, konversation_id)
SELECT sp.person_id, rp.person_id, n->>'message', mongo_ts(n->>'timestamp'),
       (n->>'conversation_id')::int
FROM mongo_raw m
CROSS JOIN LATERAL jsonb_array_elements(coalesce(m.doc->'messages', '[]'::jsonb)) AS n
JOIN person sp ON lower(sp.email) = lower(m.doc->>'_id')
JOIN person rp ON lower(rp.email) = lower(n->>'receiver_email');

DROP FUNCTION mongo_ts(text);
DROP TABLE mongo_raw;
