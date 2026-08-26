-- ============================================================================
-- Modelltest - prueft die Zusagen des physischen Modells.
--
-- Nicht die Daten werden hier geprueft, sondern das Modell selbst: Nimmt es an,
-- was erlaubt ist? Weist es ab, was verboten ist? Jeder Test legt Testdaten an,
-- am Ende wird alles zurueckgerollt - die Datenbank bleibt, wie sie war.
--
-- Ausfuehren:
--   docker compose exec -T postgres_for_lf8_starter \
--     psql -U user -d lf8_lets_meet_db -v ON_ERROR_STOP=1 \
--     < src/main/resources/sql/900_modelltest.sql
--
-- Exit-Code 0 = alle Pruefungen bestanden.
--
-- Wie es funktioniert: jeder Negativtest steht in einem eigenen BEGIN ...
-- EXCEPTION-Block. Schlaegt das INSERT fehl, ist das der Erfolgsfall und der
-- Block faengt den Fehler. Geht es durch, zaehlen wir einen Fehler.
-- ============================================================================

BEGIN;

DO $$
DECLARE
    p_a      bigint;
    p_b      bigint;
    h_angeln bigint;
    fehler   integer := 0;
    uebrig   integer;
BEGIN
    ------------------------------------------------------------------ person ---
    INSERT INTO person (email, nachname, vorname, geburtsdatum, plz, ort)
    VALUES ('Test.Eins@example.te', 'Eins', 'Test', DATE '1990-01-01', '01067', 'Dresden')
    RETURNING person_id INTO p_a;
    RAISE NOTICE 'OK    gueltige Person wird angenommen';

    INSERT INTO person (email, nachname, vorname)
    VALUES ('test.zwei@example.te', 'Zwei', 'Test')
    RETURNING person_id INTO p_b;

    BEGIN
        INSERT INTO person (email, nachname, vorname)
        VALUES ('test.eins@example.te', 'X', 'Y');
        RAISE WARNING 'FEHLER E-Mail in anderer Schreibweise wurde ANGENOMMEN';
        fehler := fehler + 1;
    EXCEPTION WHEN others THEN
        RAISE NOTICE 'OK    gleiche E-Mail in anderer Schreibweise wird abgewiesen';
    END;

    BEGIN
        INSERT INTO person (email, nachname, vorname) VALUES ('   ', 'X', 'Y');
        RAISE WARNING 'FEHLER leere E-Mail wurde ANGENOMMEN';
        fehler := fehler + 1;
    EXCEPTION WHEN others THEN
        RAISE NOTICE 'OK    leere E-Mail wird abgewiesen';
    END;

    BEGIN
        INSERT INTO person (email, nachname, vorname, rolle)
        VALUES ('test.drei@example.te', 'Drei', 'Test', 'chefin');
        RAISE WARNING 'FEHLER unbekannte Rolle wurde ANGENOMMEN';
        fehler := fehler + 1;
    EXCEPTION WHEN others THEN
        RAISE NOTICE 'OK    unbekannte Rolle wird abgewiesen';
    END;

    ------------------------------------------------------------- person_hobby ---
    INSERT INTO hobby (bezeichnung) VALUES ('Angeln') RETURNING hobby_id INTO h_angeln;

    INSERT INTO person_hobby (person_id, hobby_id, quelle, prioritaet)
    VALUES (p_a, h_angeln, 'excel', 78);
    RAISE NOTICE 'OK    Hobbyzuordnung mit Prioritaet wird angenommen';

    INSERT INTO person_hobby (person_id, hobby_id, quelle, prioritaet)
    VALUES (p_a, h_angeln, 'xml', NULL);
    RAISE NOTICE 'OK    dasselbe Hobby aus anderer Quelle wird angenommen';

    BEGIN
        INSERT INTO person_hobby (person_id, hobby_id, quelle, prioritaet)
        VALUES (p_a, h_angeln, 'excel', 12);
        RAISE WARNING 'FEHLER dasselbe Hobby zweimal aus derselben Quelle wurde ANGENOMMEN';
        fehler := fehler + 1;
    EXCEPTION WHEN others THEN
        RAISE NOTICE 'OK    dasselbe Hobby zweimal aus derselben Quelle wird abgewiesen';
    END;

    INSERT INTO person_hobby (person_id, hobby_id, quelle, prioritaet)
    VALUES (p_b, h_angeln, 'excel', -100);
    RAISE NOTICE 'OK    Prioritaet -100 (Randwert des vereinbarten Bereichs) wird angenommen';

    BEGIN
        INSERT INTO person_hobby (person_id, hobby_id, quelle, prioritaet)
        VALUES (p_b, h_angeln, 'xml', 101);
        RAISE WARNING 'FEHLER Prioritaet 101 wurde ANGENOMMEN';
        fehler := fehler + 1;
    EXCEPTION WHEN others THEN
        RAISE NOTICE 'OK    Prioritaet ausserhalb -100..100 wird abgewiesen';
    END;

    -------------------------------------------------------------- person_like ---
    INSERT INTO person_like (von_person_id, an_person_id, status, zeitpunkt)
    VALUES (p_a, p_b, 'pending', TIMESTAMP '2024-03-02 19:03:01');
    RAISE NOTICE 'OK    gerichteter Like wird angenommen';

    BEGIN
        INSERT INTO person_like (von_person_id, an_person_id, status)
        VALUES (p_a, p_a, 'mutual');
        RAISE WARNING 'FEHLER Like auf sich selbst wurde ANGENOMMEN';
        fehler := fehler + 1;
    EXCEPTION WHEN others THEN
        RAISE NOTICE 'OK    Like auf sich selbst wird abgewiesen';
    END;

    ------------------------------------------------------------- freundschaft ---
    INSERT INTO freundschaft (person_a_id, person_b_id, befreundet_seit)
    VALUES (least(p_a, p_b), greatest(p_a, p_b), DATE '2024-06-01');
    RAISE NOTICE 'OK    Freundschaft als Paar (kleinere ID zuerst) wird angenommen';

    BEGIN
        INSERT INTO freundschaft (person_a_id, person_b_id)
        VALUES (greatest(p_a, p_b), least(p_a, p_b));
        RAISE WARNING 'FEHLER Gegenrichtung derselben Freundschaft wurde ANGENOMMEN';
        fehler := fehler + 1;
    EXCEPTION WHEN others THEN
        RAISE NOTICE 'OK    Gegenrichtung derselben Freundschaft wird abgewiesen (keine Doppelzeile)';
    END;

    ---------------------------------------------------------------- nachricht ---
    INSERT INTO nachricht (sender_id, empfaenger_id, inhalt, gesendet_am, konversation_id)
    VALUES (p_a, p_b, 'Hallo!', TIMESTAMP '2024-09-20 13:17:09', 27);
    RAISE NOTICE 'OK    gerichtete Nachricht wird angenommen';

    BEGIN
        INSERT INTO nachricht (sender_id, empfaenger_id, inhalt) VALUES (p_a, p_a, 'Notiz');
        RAISE WARNING 'FEHLER Nachricht an sich selbst wurde ANGENOMMEN';
        fehler := fehler + 1;
    EXCEPTION WHEN others THEN
        RAISE NOTICE 'OK    Nachricht an sich selbst wird abgewiesen';
    END;

    --------------------------------------------------------------------- foto ---
    INSERT INTO foto (person_id, art, daten, mime_typ)
    VALUES (p_a, 'profil', '\x89504e47'::bytea, 'image/png');
    RAISE NOTICE 'OK    Profilbild als bytea wird angenommen';

    INSERT INTO foto (person_id, art, url) VALUES (p_a, 'link', 'https://example.te/bild.jpg');
    RAISE NOTICE 'OK    weiteres Foto als Link wird angenommen';

    BEGIN
        INSERT INTO foto (person_id, art, daten) VALUES (p_a, 'profil', '\xffd8ff'::bytea);
        RAISE WARNING 'FEHLER zweites Profilbild wurde ANGENOMMEN';
        fehler := fehler + 1;
    EXCEPTION WHEN others THEN
        RAISE NOTICE 'OK    zweites Profilbild derselben Person wird abgewiesen';
    END;

    BEGIN
        INSERT INTO foto (person_id, art) VALUES (p_a, 'upload');
        RAISE WARNING 'FEHLER Foto ohne Bild und ohne URL wurde ANGENOMMEN';
        fehler := fehler + 1;
    EXCEPTION WHEN others THEN
        RAISE NOTICE 'OK    Foto ohne Bild und ohne URL wird abgewiesen';
    END;

    ------------------------------------------------ Loeschen (Art. 17 DSGVO) ---
    DELETE FROM person WHERE person_id = p_a;
    SELECT (SELECT count(*) FROM person_hobby WHERE person_id = p_a)
         + (SELECT count(*) FROM person_like  WHERE von_person_id = p_a OR an_person_id = p_a)
         + (SELECT count(*) FROM nachricht    WHERE sender_id = p_a OR empfaenger_id = p_a)
         + (SELECT count(*) FROM foto         WHERE person_id = p_a)
         + (SELECT count(*) FROM freundschaft WHERE person_a_id = p_a OR person_b_id = p_a)
    INTO uebrig;
    IF uebrig = 0 THEN
        RAISE NOTICE 'OK    Loeschen einer Person entfernt alle abhaengigen Zeilen mit';
    ELSE
        RAISE WARNING 'FEHLER nach dem Loeschen bleiben % abhaengige Zeilen stehen', uebrig;
        fehler := fehler + 1;
    END IF;

    --------------------------------------------------------------- Ergebnis ---
    IF fehler > 0 THEN
        RAISE EXCEPTION 'Modelltest: % Pruefung(en) fehlgeschlagen', fehler;
    END IF;
    RAISE NOTICE '---';
    RAISE NOTICE 'Modelltest: alle Pruefungen bestanden';
END $$;

ROLLBACK;
