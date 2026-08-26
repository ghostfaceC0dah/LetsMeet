-- ============================================================================
-- LetsMeet - physisches Modell, Erweiterung fuer Akt 2
--
-- Setzt 010_schema.sql voraus. Begruendungen und ER-Diagramm: docs/datenmodell.md
--
-- Grundregel fuer CHECK-Constraints in diesem Modell:
--   Eingeschraenkt wird nur, was mit der Kundin VEREINBART ist (z.B. der
--   Prioritaetsbereich -100..100) oder was unser eigenes Vokabular ist (quelle,
--   art). Rohwerte aus den Quellen - geschlecht, interesse_code, status -
--   bekommen KEINEN CHECK: die Quelle darf morgen einen neuen Wert liefern, und
--   der Datenvertrag verlangt sie unveraendert. Plausibilitaet pruefen wir dort
--   per Abfrage (900_modelltest.sql), nicht per Constraint.
-- ============================================================================

-- ---------------------------------------------------------------------------
-- hobby - eigener Entitaetstyp, weil derselbe Hobbyname bei vielen Personen
-- vorkommt. Das ist die Aufloesung der 1NF-Verletzung in der Excel-Spalte
-- "Hobby1 %Prio1%; Hobby2 %Prio2%; ..." - eine Zelle mit bis zu fuenf Werten.
-- ---------------------------------------------------------------------------
CREATE TABLE hobby (
    hobby_id    bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    bezeichnung text NOT NULL UNIQUE
);

-- ---------------------------------------------------------------------------
-- person_hobby - m:n zwischen Person und Hobby.
--
-- prioritaet und quelle sind Beziehungsattribute: sie beschreiben nicht die
-- Person und nicht das Hobby, sondern deren Zusammentreffen (siehe
-- normalization.md, 2NF). Sie gehoeren also genau hierher.
--
-- Warum ist quelle Teil des Primaerschluessels?
--   Der Datenvertrag V2 sagt: "dasselbe Hobby aus derselben Quelle erscheint
--   nur einmal" - je Quelle also einmal, nicht insgesamt einmal. Dieselbe
--   Person darf "Angeln" aus Excel UND aus der XML-Nachlieferung haben.
--
-- prioritaet ist NULL, wenn die Quelle keine liefert (die XML-Datei liefert
-- keine). Eine erfundene 0 waere eine fachliche Aussage, die niemand gemacht
-- hat - und 0 liegt mitten im vereinbarten Wertebereich.
-- ---------------------------------------------------------------------------
CREATE TABLE person_hobby (
    person_id  bigint  NOT NULL REFERENCES person (person_id) ON DELETE CASCADE,
    hobby_id   bigint  NOT NULL REFERENCES hobby (hobby_id)   ON DELETE CASCADE,
    quelle     text    NOT NULL,
    prioritaet integer,
    PRIMARY KEY (person_id, hobby_id, quelle),
    CONSTRAINT person_hobby_quelle_bekannt CHECK (quelle IN ('excel', 'xml', 'mongo')),
    -- Fachlich mit der Kundin vereinbarter Bereich. Die aktuelle Lieferung
    -- schoepft ihn nicht aus (0..100) - modelliert wird der vereinbarte Bereich.
    CONSTRAINT person_hobby_prioritaet_bereich
        CHECK (prioritaet IS NULL OR prioritaet BETWEEN -100 AND 100)
);

-- ---------------------------------------------------------------------------
-- person_interesse - "Interessiert an" ist ein Mehrfachwert (Wert "mw" steht
-- 33x in der Quelle), also eine eigene Tabelle: eine Zeile je Interesse (1NF).
-- interesse_code bleibt der Rohwert - keine Uebersetzung, keine Codetabelle.
-- ---------------------------------------------------------------------------
CREATE TABLE person_interesse (
    person_id      bigint NOT NULL REFERENCES person (person_id) ON DELETE CASCADE,
    interesse_code text   NOT NULL,
    PRIMARY KEY (person_id, interesse_code)
);

-- ---------------------------------------------------------------------------
-- person_like - gerichtete Zuneigung: von_person -> an_person.
-- Rekursive m:n-Beziehung der Tabelle person auf sich selbst.
--
-- Gemessen an der MongoDB-Quelle (500 Likes):
--   * kein Like auf sich selbst          -> CHECK unten
--   * kein Paar zweimal in gleicher Richtung -> Primaerschluessel (von, an) traegt
--   * KEIN einziges Gegen-Like (a->b und b->a) -> die Gegenseitigkeit steckt
--     nicht in einer zweiten Zeile, sondern im status ("mutual" 158x)
--
-- status ist Rohwert (pending / mutual / declined in dieser Lieferung), deshalb
-- ohne CHECK. Offene Kundinnenfrage: ist "mutual" dasselbe wie eine
-- Freundschaft? Siehe docs/datenmodell.md.
-- ---------------------------------------------------------------------------
CREATE TABLE person_like (
    von_person_id bigint NOT NULL REFERENCES person (person_id) ON DELETE CASCADE,
    an_person_id  bigint NOT NULL REFERENCES person (person_id) ON DELETE CASCADE,
    status        text,
    zeitpunkt     timestamp,
    PRIMARY KEY (von_person_id, an_person_id),
    CONSTRAINT person_like_nicht_selbst CHECK (von_person_id <> an_person_id)
);

-- ---------------------------------------------------------------------------
-- nachricht - gerichtet: sender -> empfaenger. Eigene Entitaet mit technischem
-- Schluessel, weil zwei Nachrichten zwischen denselben Personen in derselben
-- Sekunde fachlich moeglich sind (sender, empfaenger, gesendet_am) also kein
-- verlaesslicher Schluessel ist.
--
-- Warum ist konversation_id nur eine Spalte und kein Fremdschluessel auf eine
-- Tabelle "konversation"? Gemessen an der Quelle (300 Nachrichten):
--   * 50 verschiedene konversation_id (Werte 1..50)
--   * 48 davon kommen bei MEHR ALS EINEM Teilnehmerpaar vor
--   * jedes der 300 Paare hat genau eine Nachricht
-- Eine Konversation zwischen genau zwei Personen kann die id also nicht
-- bezeichnen. Wir tragen sie unveraendert mit, wie der Datenvertrag es
-- verlangt, und deuten sie nicht. Was sie bedeutet, ist eine Kundinnenfrage.
-- ---------------------------------------------------------------------------
CREATE TABLE nachricht (
    nachricht_id    bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    sender_id       bigint NOT NULL REFERENCES person (person_id) ON DELETE CASCADE,
    empfaenger_id   bigint NOT NULL REFERENCES person (person_id) ON DELETE CASCADE,
    inhalt          text,
    gesendet_am     timestamp,
    konversation_id integer,
    CONSTRAINT nachricht_nicht_an_sich_selbst CHECK (sender_id <> empfaenger_id)
);

CREATE INDEX nachricht_sender_idx     ON nachricht (sender_id);
CREATE INDEX nachricht_empfaenger_idx ON nachricht (empfaenger_id);

-- ---------------------------------------------------------------------------
-- freundschaft - symmetrisch, eine Zeile je Paar.
--
-- Begruendung aus dem Anwendungsfalldiagramm: "sich gegenseitig nach
-- beiderseitiger Zustimmung in Freundeliste aufnehmen", Multiplizitaet 2 am
-- Akteur Anwender. Eine Freundschaft ist also keine Richtung, sondern ein Paar.
--
-- Der CHECK person_a_id < person_b_id erzwingt, dass jedes Paar nur EINMAL
-- gespeichert werden kann - (5,9) ist erlaubt, (9,5) nicht. Damit kann es keine
-- halbe Freundschaft und keine widerspruechliche Doppelzeile geben. Die
-- Alternative "zwei gerichtete Zeilen pro Freundschaft" waere billiger
-- abzufragen, muesste die Gegenzeile aber per Trigger erzwingen.
--
-- Achtung: die MongoDB-Lieferung enthaelt bei allen 1576 Dokumenten ein LEERES
-- friends-Array. Diese Tabelle bleibt nach dem Import also leer - modelliert
-- ist sie, weil der Auftrag Freundeslisten verlangt.
-- ---------------------------------------------------------------------------
CREATE TABLE freundschaft (
    person_a_id      bigint NOT NULL REFERENCES person (person_id) ON DELETE CASCADE,
    person_b_id      bigint NOT NULL REFERENCES person (person_id) ON DELETE CASCADE,
    befreundet_seit  date,
    PRIMARY KEY (person_a_id, person_b_id),
    CONSTRAINT freundschaft_einmal_je_paar CHECK (person_a_id < person_b_id)
);

-- ---------------------------------------------------------------------------
-- foto - ein direkt gespeichertes Profilbild sowie weitere hochgeladene oder
-- verlinkte Fotos (Auftrag Akt 2).
--
--   art = 'profil'  das eine Profilbild, Bild liegt in daten (bytea)
--   art = 'upload'  weiteres hochgeladenes Bild, liegt in daten
--   art = 'link'    verlinktes Bild, nur url
--
-- Der Teilindex unten erlaubt genau EIN Profilbild je Person, aber beliebig
-- viele weitere Fotos. Das ist der Anwendungsfall "Foto anfuegen, aendern,
-- loeschen" mit der Einschraenkung aus dem Wort "Profilbild".
--
-- Alternative, die wir verworfen haben: Profilbild als bytea-Spalte in person.
-- Dann waere jede Abfrage auf Stammdaten mit Bilddaten belastet, und die
-- Regel "hoechstens ein Profilbild" waere im Modell nicht sichtbar, sondern nur
-- ein Nebeneffekt der Spaltenzahl.
-- ---------------------------------------------------------------------------
CREATE TABLE foto (
    foto_id        bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    person_id      bigint NOT NULL REFERENCES person (person_id) ON DELETE CASCADE,
    art            text   NOT NULL,
    daten          bytea,
    url            text,
    mime_typ       text,
    hochgeladen_am timestamp,
    CONSTRAINT foto_art_bekannt  CHECK (art IN ('profil', 'upload', 'link')),
    CONSTRAINT foto_hat_inhalt   CHECK (daten IS NOT NULL OR url IS NOT NULL)
);

CREATE UNIQUE INDEX foto_ein_profilbild_je_person ON foto (person_id) WHERE art = 'profil';
