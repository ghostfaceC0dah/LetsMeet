-- ============================================================================
-- LetsMeet - physisches Modell, Kern (Akt 1)
--
-- Entstanden nach Wasserfall: Analyse -> konzeptueller Entwurf -> logischer
-- Entwurf -> physischer Entwurf. Die Begruendung zu JEDER Entscheidung hier
-- steht in docs/datenmodell.md; dort auch das ER-Diagramm.
--
-- Diese Datei ist der Kern, den der Datenvertrag V1 braucht. Die Tabellen fuer
-- Akt 2 (Hobbys, Interessen, Likes, Nachrichten, Freundschaften, Fotos) liegen
-- in 030_schema_akt2.sql - fuer Akt 1 muesst ihr die nicht anlegen.
--
-- Reihenfolge:
--   010_schema.sql        Kern            <- diese Datei
--   020_views_v1.sql      Datenvertrag V1
--   030_schema_akt2.sql   Erweiterung Akt 2
--   040_views_v2.sql      Datenvertrag V2 (noch offen)
--   900_modelltest.sql    prueft, ob das Modell abweist, was es abweisen muss
--
-- Ausfuehren:
--   docker compose exec -T postgres_for_lf8_starter \
--     psql -U user -d lf8_lets_meet_db < src/main/resources/sql/010_schema.sql
-- ============================================================================

-- ---------------------------------------------------------------------------
-- person - eine Person mit ihren Stammdaten.
--
-- Warum eine technische ID und nicht die E-Mail als Primaerschluessel?
--   Die E-Mail ist ein Schluesselkandidat (eindeutig, nicht leer - so steht es
--   im Datenvertrag). Sie ist aber lang, aenderbar und wird in Akt 2 aus zwei
--   Quellen in unterschiedlicher Schreibweise geliefert. Ein Fremdschluessel
--   auf eine veraenderliche E-Mail muesste bei jeder Adressaenderung in sechs
--   Tabellen mitwandern. Deshalb: person_id als Surrogatschluessel, die E-Mail
--   bleibt als eindeutiger Schluesselkandidat daneben.
--
-- Warum kein Feld "email_key" mit lower(email)?
--   Das waere abgeleiteter Wert - er haengt von email ab, nicht vom Schluessel,
--   und muesste bei jeder Aenderung mitgepflegt werden. Ein funktionaler
--   UNIQUE-Index auf lower(email) leistet dasselbe ohne zweite Spalte.
--
-- Warum liegen Strasse, PLZ und Ort in dieser Tabelle?
--   Weil "PLZ bestimmt Ort" in DIESEN Daten nicht gilt: 54 Postleitzahlen
--   tragen mehrere Ortsschreibweisen (z.B. 17489 -> "Greifswald" und
--   "Greifswald Hansestadt"). Es gibt also keine transitive Abhaengigkeit, die
--   man aufloesen koennte - eine Tabelle plz -> ort waere Datenverlust.
--   Nachzaehlen nach dem Import:
--     SELECT plz, count(DISTINCT ort) FROM person GROUP BY plz HAVING count(DISTINCT ort) > 1;
-- ---------------------------------------------------------------------------
CREATE TABLE person (
    person_id     bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    -- Schreibweise der Quelle, unveraendert (Datenvertrag Akt 1)
    email         text NOT NULL,

    nachname      text NOT NULL,
    vorname       text NOT NULL,
    geburtsdatum  date,

    strasse       text,
    plz           text,        -- text, nicht integer: 58 Personen haben eine
    ort           text,        -- vierstellige PLZ, 15 eine mit fuehrender Null

    telefon       text,
    geschlecht    text,        -- Rohwert der Quelle, nicht uebersetzt

    -- Aus dem Anwendungsfalldiagramm, ohne Quelldaten: der Anwendungsfall
    -- "Login / am System anmelden" und die Rollen Administrator/Nutzer/Anwender
    -- brauchen diese beiden Spalten. Ein Passwort steht NIE im Klartext hier.
    rolle         text NOT NULL DEFAULT 'anwender',
    passwort_hash text,

    -- Aus der MongoDB-Quelle. Nicht Teil eines Datenvertrags, aber der einzige
    -- Hinweis darauf, welche Quelle die neuere Angabe hat - und damit Material
    -- fuer die offene Kundinnenfrage zu widersprechenden Stammdaten.
    angelegt_am   timestamp,
    geaendert_am  timestamp,

    CONSTRAINT person_email_nicht_leer CHECK (btrim(email) <> ''),
    CONSTRAINT person_rolle_bekannt    CHECK (rolle IN ('anwender', 'nutzer', 'administrator'))
);

-- Der Datenvertrag sagt: email eindeutig. Akt 2 ergaenzt: Gross- und
-- Kleinschreibung macht keinen Unterschied. Genau das erzwingt dieser Index -
-- "Martin.Forster@web.ork" und "martin.forster@web.ork" sind eine Person.
CREATE UNIQUE INDEX person_email_eindeutig ON person (lower(email));

COMMENT ON TABLE  person            IS 'Person mit Stammdaten; Quelle Akt 1: Lets Meet DB Dump.xlsx';
COMMENT ON COLUMN person.email      IS 'Schreibweise der Excel-Quelle; Eindeutigkeit ueber lower(email)';
COMMENT ON COLUMN person.geschlecht IS 'Rohwert der Quelle, keine Uebersetzung, keine Codetabelle';
COMMENT ON COLUMN person.plz        IS 'text - vierstellige PLZ und fuehrende Nullen muessen erhalten bleiben';
