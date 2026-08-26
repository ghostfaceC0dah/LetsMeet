-- ============================================================================
-- Datenvertrag Akt 1 (V1) - die Schnittstelle zur Kundinnen-App.
--
-- Spaltennamen und -typen sind vorgegeben und muessen exakt stimmen:
--   migration_users(email text, first_name text, last_name text,
--                   birth_date date, postal_code text, city text)
--
-- Genau hier zeigt sich, wofuer eine View gut ist: unsere Tabellen heissen
-- deutsch und sind nach unserem Modell geschnitten, die App erwartet englische
-- Spaltennamen in fester Reihenfolge. Die View uebersetzt zwischen beidem -
-- unser Modell kann sich aendern, ohne dass die App etwas merkt.
--
-- Regeln des Vertrags und wo sie erfuellt werden:
--   * eine Zeile pro migrierter Person   -> kein JOIN, keine Vervielfachung
--   * email eindeutig und nicht leer     -> Tabelle person: UNIQUE-Index auf
--                                           lower(email) + CHECK auf nicht leer
--   * keine Platzhalter fuer misslungene Zeilen -> Aufgabe des Imports:
--                                           abweisen statt erfinden
-- ============================================================================

DROP VIEW IF EXISTS migration_users;

CREATE VIEW migration_users AS
SELECT p.email        AS email,
       p.vorname      AS first_name,
       p.nachname     AS last_name,
       p.geburtsdatum AS birth_date,
       p.plz          AS postal_code,
       p.ort          AS city
FROM person p;

COMMENT ON VIEW migration_users IS 'Datenvertrag V1 - Spaltennamen und Typen sind vorgegeben, nicht aendern';
