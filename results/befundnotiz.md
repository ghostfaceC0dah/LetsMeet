# Befundnotiz LetsMeet-Datenmigration

Team: _Namen eintragen_

Ein Dokument durch das ganze Projekt. Neue Eintraege kommen **nach unten**, jeder
mit Datum. Vier Fragen tragen die Notiz (readme.md):

1. **Was ist uns an der Quelle aufgefallen?** Beobachtungen, keine Vermutungen.
2. **Was haben wir daraufhin entschieden, und warum?** Dazu die verworfene
   Alternative und was sie gekostet haette.
3. **Was haben wir nicht uebernommen, und warum?**
4. **Welche Daten sind besonders schuetzenswert, und was folgt daraus?**

ERD-Share-URL (Akt 2): _noch nicht registriert_

Alle Zahlen unten sind nachgemessen. Reproduzierbar mit:

```bash
java -jar target/letsmeet-migration.jar
```

```bash
docker exec -i lf8_lets_meet_postgres_container psql -U user -d lf8_lets_meet_db < src/main/resources/sql/920_datenpruefung.sql
```

---

## 26.08.2026 - Quellenanalyse Excel (Frage 1)

`Lets Meet DB Dump.xlsx`, **1576 Datenzeilen**, 8 Spalten, keine Spalte komplett leer.

### Formate und Trennzeichen

| Beobachtung | Zahl |
|---|---|
| Namenszelle `Nachname, Vorname` mit `", "` getrennt | 1576 von 1576 |
| Adresszelle mit **zwei** Kommas | 1573 |
| Adresszelle mit **drei** Kommas (Komma im Ortsnamen) | 3 - alle `17109 Demmin, Hansestadt` |
| Geburtsdatum als Text `dd.mm.yyyy` | 1576, kein Excel-Datumswert |
| Zeitraum der Geburtsdaten | 24.01.1958 bis 24.12.2002 |
| Verschiedene Postleitzahlen / Orte | 1202 / 834 |
| Telefonformate | mind. 4: `02372 8020`, `06221 / 98689`, `(07631) 67955`, `050 / 4571857` |

### Werte, die nicht zur Spaltenueberschrift passen

Die Spalte heisst `Geschlecht (m/w/nonbinary)`, die Werte sind:

| Wert | Anzahl |
|---|---|
| `m` | 918 |
| `w` | 620 |
| `nb` | 38 |

**`nb`, nicht `nonbinary`.** Die Ueberschrift ist eine Beschriftung, keine
Wertespezifikation. Gleiches Muster in `Interessiert an`: dort stehen `w` (908),
`m` (635) und **`mw` (33)** - ein Mehrfachwert ohne Trennzeichen. Die Spalte
gehoert zu Akt 2, die Beobachtung notieren wir jetzt.

### Zwei Auffaelligkeiten, die zusammen auftreten

**73 Personen haben ein Leerzeichen im Vor- oder Nachnamen.** Nur 6 davon
stammen von aeusseren Leerzeichen der Zelle; bei den anderen steht ein
Leerzeichen **vor** dem Komma: `"Stanislav , Petrov"`. Nach der Vertragsregel
"Komma + genau ein Leerzeichen trennt" wird daraus der Nachname `"Stanislav "`.

Bei genau diesen 73 Personen wirkt die Reihenfolge ausserdem **getauscht**:

| E-Mail | Nachname (Spalte 1) | Vorname (Spalte 2) |
|---|---|---|
| `petrov.stanislav@a-o-l.kom` | `Stanislav ` | `Petrov` |
| `vasilev.maksim@ge-em-ix.te` | `Maksim ` | `Vasilev` |
| `joyeux.jérôme@d-ohnline.te` | `Jérôme` | `Joyeux ` |

`Maksim` ist ein Vorname, `Vasilev` ein Nachname - in der Spalte
`Nachname, Vorname` stehen sie vertauscht. Dass beide Auffaelligkeiten
zusammen auftreten, deutet auf ein zweites Quellsystem hin, aus dem diese
Zeilen stammen. **Offene Frage an die Kundin.**

### Postleitzahlen: fehlende fuehrende Null

58 Personen haben eine **vierstellige** PLZ, 15 eine mit fuehrender Null. Der
Beweis, dass die vierstelligen eine verlorene Null haben und keine anderen
Postleitzahlen sind - dieselbe Stadt kommt in beiden Schreibweisen vor:

```
Chemnitz            09116, 9116
Annaberg-Buchholz   09456, 9456
Jena                07743, 7743
Bautzen             02625, 2625
```

Die ersten Stellen der vierstelligen Werte sind 1,2,3,4,6,7,8,9 - alle liegen
im Bereich `0xxxx`. Irgendwo vor unserer Lieferung hat ein Werkzeug die PLZ als
**Zahl** behandelt.

Ausserdem: `plz -> ort` ist **keine** funktionale Abhaengigkeit. 54
Postleitzahlen tragen mehrere Ortsschreibweisen (`17489` -> `Greifswald` und
`Greifswald Hansestadt`). Folge fuers Modell in
[../docs/datenmodell.md](../docs/datenmodell.md).

---

## 26.08.2026 - Importentscheidungen (Frage 2)

**Trennen, nicht putzen.** Der Import zerlegt und schreibt, er bereinigt nicht:
kein `trim()`, keine vereinheitlichten Telefonnummern, keine ergaenzten Nullen.
Grund: Der Datenvertrag verlangt fuer Akt 1 die Inhalte unveraendert,
"einschliesslich aeusserer Leerzeichen". Bereinigen ist ein eigener
Arbeitsschritt mit eigener Kundinnenentscheidung. Bestaetigt vom Pruefstand:
"Alle verglichenen Feldwerte stimmen zeichengenau (NFC, ungetrimmt) mit der
Quelle ueberein."

**Name: Schnitt an der ersten Fundstelle von `", "`.**
Aus `"Stanislav , Petrov"` wird `"Stanislav "` / `"Petrov"`, aus
`"van Deloo, Albert, jun."` wird `"van Deloo"` / `"Albert, jun."`.
_Verworfen:_ am letzten Komma schneiden - haette den zweiten Fall zerrissen.
_Verworfen:_ die 73 vertauschten Namen automatisch drehen - wir haben keine
Regel, die einen Vornamen von einem Nachnamen unterscheidet, nur einen Verdacht.
Raten waere schlimmer als uebernehmen.

**Adresse: Schnitt an den ersten zwei Fundstellen, Ort ist der Rest.**
Deshalb bleibt `Demmin, Hansestadt` ein Ort. _Verworfen:_ an jedem Komma
splitten und drei Teile erwarten - haette diese 3 Zeilen abgewiesen oder den
Ortsnamen halbiert.

**PLZ als `text`, vierstellige Werte unveraendert.**
_Verworfen:_ `integer` - haette die 15 fuehrenden Nullen zerstoert.
_Verworfen:_ fehlende Nullen ergaenzen. Das waere eine Korrektur der Quelle,
und ohne PLZ-Verzeichnis koennten wir eine echte vierstellige PLZ (Oesterreich,
Schweiz) nicht von einer verlorenen Null unterscheiden. Wir melden den Befund
statt ihn zu verwischen.

**Geburtsdatum strikt `dd.MM.uuuu`.**
`ResolverStyle.STRICT`, damit ein "31.02.1990" auffaellt statt auf den 28.02.
verschoben zu werden. Hier wird ausnahmsweise `strip()` angewandt: die
Zielspalte ist `date`, ein Leerzeichen am Rand kann den Zeitpunkt nicht
veraendern.

**E-Mail: Schreibweise der Quelle gespeichert, Eindeutigkeit ueber
`lower(email)`.** Erzwungen durch einen funktionalen UNIQUE-Index.
_Verworfen:_ eine zweite Spalte `email_key` - abgeleiteter, doppelt zu
pflegender Wert.

**Uebernommen werden alle Personenfelder, nicht nur die sechs der View.**
Telefon und Geschlecht stehen in derselben Zeile, gehoeren zur selben Person und
kosten nichts; in Akt 2 sind sie gefordert. _Verworfen:_ streng minimal nur die
sechs Vertragsspalten - haette bedeutet, den Import in Akt 2 nochmal zu
schreiben.

**Abweisen statt Platzhalter.** Eine Zeile, die sich nicht regelkonform
zerlegen laesst, wird nicht importiert und landet mit Zeilennummer und Grund in
`results/abgewiesen.csv`.

**Alles in einer Transaktion.** Entweder sind alle Personen drin oder keine -
sonst koennte ein Pruefstand auf einer halben Migration gruen werden.

---

## 26.08.2026 - Nicht uebernommen, und Grenzen des Imports (Frage 3, Auftrag 6)

**Nicht uebernommen: nichts.** Alle 1576 Zeilen sind importiert, 0 Abweisungen -
`results/abgewiesen.csv` enthaelt nur die Kopfzeile. Zaehlprobe des Pruefstands:
"Alle 1.576 Personen aus der Quelle sind im Bestand."

Nicht importiert, weil zu einem spaeteren Akt gehoerend: Hobbys (Spalte D),
`Interessiert an` (Spalte G), die XML-Nachlieferung und die MongoDB.

**Wo der Import an Grenzen stoesst:**

- **Er erkennt die vertauschten Namen nicht.** 73 Zeilen kommen mit hoher
  Wahrscheinlichkeit falsch herum in die Datenbank. Sichtbar wird das erst in
  der App ("Petrov, 25" - Petrov ist der Nachname).
- **Er kann eine verlorene fuehrende Null nicht von einer echten vierstelligen
  PLZ unterscheiden.** Dafuer braeuchte er ein PLZ-Verzeichnis; das ist nicht
  Teil der Lieferung.
- **Alles-oder-nichts je Zeile.** Ist nur die Adresszelle kaputt, wird die ganze
  Person abgewiesen - auch Name und E-Mail waeren brauchbar gewesen.
  _Alternative, die wir verworfen haben:_ Person mit leerer Adresse importieren.
  Das waere ein Datensatz, der vollstaendig aussieht und es nicht ist.
- **Ein unlesbares Geburtsdatum verwirft die ganze Person.**
  _Alternative:_ mit `birth_date = NULL` importieren. Verworfen, weil dann eine
  Person im Bestand steht, deren Angaben von der Quelle abweichen, ohne dass es
  jemand merkt. Betrifft in dieser Lieferung 0 Zeilen - die Entscheidung ist
  also billig, muss aber getroffen sein.
- **Dubletten erkennt er nur ueber die E-Mail.** Zwei Zeilen derselben Person
  mit verschiedenen Adressen waeren zwei Personen.
- **Bei einem Constraint-Verstoss nennt die Fehlermeldung die Excel-Zeile
  nicht.** Der Import schreibt gebuendelt (500 Zeilen je Runde); PostgreSQL
  meldet dann den Verstoss, nicht die Herkunftszeile. Bisher nie eingetreten -
  wenn doch, hilft `import` ohne Buendelung oder ein Blick in
  `920_datenpruefung.sql`.

---

## 26.08.2026 - Sichtpruefung in der Kundinnen-App (Auftrag 4)

Was von unseren Entscheidungen in der App sichtbar wird
([http://localhost:3611](http://localhost:3611), Suche nach Ort oder E-Mail):

| Suche | Anzeige | Was man sieht |
|---|---|---|
| `Chemnitz` | `09116 Chemnitz` und `9116 Chemnitz` | Die fehlende fuehrende Null steht direkt untereinander - der Quellfehler ist in der App sichtbar, genau wie gewollt |
| `Demmin` | `17109 Demmin, Hansestadt` (3x) | Die Adressregel greift, der Ort ist nicht am Komma abgeschnitten |
| `petrov.stanislav` | `Petrov, 25`, Initialen `PS` | Die App zeigt den **Vornamen** - hier also einen Nachnamen. Die Vertauschung ist sichtbar, die Zuordnung stimmt technisch |

Zwei Erkenntnisse aus der Sichtpruefung:

- **Das Alter wird aus dem Geburtsdatum berechnet.** Ein falsch geparstes Datum
  waere sofort als absurdes Alter aufgefallen. Aus dem Datumsbereich in der
  Datenbank (24.01.1958 bis 24.12.2002) ergeben sich Alter von 23 bis 68 - die
  angezeigten Werte passen dazu.
- **Leerzeichen am Wortrand sind in der App unsichtbar.** HTML kollabiert sie.
  `"Stanislav "` sieht in der App genauso aus wie `"Stanislav"`. Die App taugt
  also **nicht** als Pruefinstrument fuer Whitespace - dafuer braucht es die
  Datenbank oder unsere Pruefungen.

---

## 26.08.2026 - Datenmodell (Wasserfall)

Ausfuehrlich in [../docs/datenmodell.md](../docs/datenmodell.md) - Phasen,
ER-Diagramm, jede Entscheidung mit verworfener Alternative, Prueffragen.
Die vier Befunde, auf denen das Modell steht:

- **"PLZ bestimmt Ort" gilt nicht** - 54 Gegenbeispiele. Adresse bleibt bei der
  Person, keine Tabelle `plz -> ort`.
- **Kein einziges Gegen-Like** unter 500 Likes: die Gegenseitigkeit steckt im
  Feld `status` ("mutual"), nicht in einer zweiten Zeile.
- **`conversation_id` kann keine Zweierkonversation bezeichnen**: 48 der 50
  Werte kommen bei mehr als einem Teilnehmerpaar vor.
- **Freundschaft ist symmetrisch** - laut Anwendungsfalldiagramm
  ("gegenseitig ... beiderseitige Zustimmung", Multiplizitaet 2).

---

## 26.08.2026 - Schutzbedarf (Frage 4)

| Datenart | Einordnung | Folge fuer unseren Umgang |
|---|---|---|
| Name, Adresse, Geburtsdatum, Telefon, E-Mail | personenbezogen, Art. 4 Nr. 1 DSGVO | Datensparsamkeit, Loeschbarkeit |
| Geschlecht **+** `Interessiert an` | ergibt die **sexuelle Orientierung** -> besondere Kategorie, **Art. 9 DSGVO** | Verarbeitung nur mit ausdruecklicher Einwilligung (Art. 9 Abs. 2 lit. a); bei einer Partnervermittlung ist sie der Kern des Dienstes, muss aber dokumentiert sein |
| Nachrichteninhalte (Akt 2) | Kommunikationsinhalt | kein Export, kein Log |
| Passwort | Zugangsdaten | nur als Hash, die Spalte heisst deshalb `passwort_hash` |

Was wir daraus tun:

- **Kein zusaetzlicher Export.** Die Quelldateien liegen so im Projekt; wir
  erzeugen keine weiteren Kopien. `results/abgewiesen.csv` enthaelt Zeilennummer
  und Grund - keine vollstaendigen Personendatensaetze.
- **Zugangsdaten** `user`/`secret` sind Vorgabe der Uebungsumgebung und stehen
  im Klartext in `compose.yml`. In einer echten Umgebung waere das ein Befund;
  hier bleibt es die dokumentierte Vorgabe.
- **Loeschbarkeit** (Art. 17): alle Fremdschluessel auf `person` mit
  `ON DELETE CASCADE`. Ein `DELETE` entfernt Hobbys, Interessen, Likes,
  Nachrichten und Fotos mit - geprueft im Modelltest.
- **Datensparsamkeit** (Art. 5 Abs. 1 lit. c): keine Spalte ohne Anwendungsfall.
  `passwort_hash` und `rolle` stehen im Modell, weil das
  Anwendungsfalldiagramm Login und Rollen verlangt - bleiben sie dauerhaft leer,
  gehoeren sie entfernt.

---

## Offene Fragen an die Kundin

1. **Sind bei den 73 Personen mit Leerzeichen vor dem Komma Vor- und Nachname
   vertauscht?** Wenn ja: sollen wir sie drehen (Bereinigung) oder unveraendert
   lassen (Migration)?
2. **Sollen fehlende fuehrende Nullen in der PLZ ergaenzt werden?** 58 Personen
   betroffen, Beweislage eindeutig - aber es waere eine Korrektur der Quelle.
3. **Was soll mit einer Zeile passieren, bei der nur ein Feld unlesbar ist?**
   Ganz abweisen (heute) oder Person mit Luecke importieren?
4. Ist `mw` in `Interessiert an` ein Wert oder zwei? (Akt 2)
5. Welche Quelle gewinnt bei widersprechenden Stammdaten? (Akt 2)
6. Ist eine Freundschaft dasselbe wie ein Like mit `status = 'mutual'`? (Akt 2)

---

## Stand der Pruefungen (26.08.2026)

| Lauf | Ergebnis |
|---|---|
| Eigene Unit-Tests (`mvn test`) | 16 Tests, gruen |
| Modelltest (`900_modelltest.sql`) | 20 von 20 bestanden |
| Eigene Datenpruefungen (`check`) | 12 von 12 gruen |
| **Pruefstand V1 nach frischem Aufbau** | **GATE GRUEN, Exit-Code 0** |

Hinweis des Pruefstands, ohne Beanstandung: "15 Postleitzahlen mit fuehrender
Null (Quelle: 15). 58 Personen mit vierstelliger Postleitzahl (Quelle: 58)."
Die Zahlen stimmen mit der Quelle ueberein - der `text`-Datentyp haelt, und der
Befund ist ein Quellbefund, kein Importfehler.
