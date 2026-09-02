package de.letsmeet.migration.parse;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Locale;

/**
 * Zerlegt die zusammengesetzten Excel-Zellen.
 *
 * <p>Diese Regeln sind der Kern von Akt 1. Sie stehen in einer eigenen Klasse,
 * weil sie sich ohne Datenbank und ohne Excel-Datei testen lassen (siehe
 * {@code RowSplitterTest}) und weil eine geaenderte Kundinnenregel dann genau
 * eine Datei betrifft.
 *
 * <p><strong>Grundsatz:</strong> hier wird getrennt, nicht geputzt. Kein
 * {@code trim()}, keine Vereinheitlichung. Der Datenvertrag verlangt die Inhalte
 * unveraendert, "einschliesslich aeusserer Leerzeichen".
 *
 * <p>Der Rueckgabetyp {@code String[]} ist der einfachste Anfang. Wenn es
 * unhandlich wird, ersetzt ihn durch eigene Typen (z.B.
 * {@code record PersonName(String nachname, String vorname)}) - das ist eure
 * Modellentscheidung.
 */
public final class RowSplitter {

    /** Komma + genau EIN Leerzeichen. Jedes weitere Leerzeichen gehoert zum Wert. */
    static final String SEPARATOR = ", ";

    /**
     * In der Quelle stehen alle 1576 Geburtsdaten als Text im Format
     * {@code dd.MM.uuuu}. {@link ResolverStyle#STRICT} ist Absicht: ein
     * "31.02.1990" soll auffallen und abgewiesen werden, nicht stillschweigend
     * auf den 28.02. verschoben werden.
     */
    private static final DateTimeFormatter DEUTSCHES_DATUM = DateTimeFormatter
            .ofPattern("dd.MM.uuuu", Locale.GERMANY)
            .withResolverStyle(ResolverStyle.STRICT);

    private RowSplitter() {
    }

    /**
     * Zerlegt die Spalte "Nachname, Vorname".
     *
     * <p>Getrennt wird an der <strong>ersten</strong> Fundstelle von
     * {@code ", "}. Danach wird nicht getrimmt - deshalb wird aus
     * {@code "Stanislav , Petrov"} der Nachname {@code "Stanislav "} mit
     * Leerzeichen, und aus {@code "van Deloo, Albert, jun."} der Vorname
     * {@code "Albert, jun."}.
     *
     * <p>Fehlt das Trennzeichen, wird die Zeile abgewiesen statt geraten: ein
     * "Forster Martin" koennte "Forster" als Vor- oder als Nachnamen meinen, und
     * eine falsche Zuordnung ist schlimmer als eine fehlende Zeile.
     *
     * @return zwei Elemente: {@code [Nachname, Vorname]}
     */
    public static String[] splitName(String cell) {
        if (cell == null) {
            throw new SourceDataException("Namenszelle fehlt");
        }
        int separator = cell.indexOf(SEPARATOR);
        if (separator < 0) {
            throw new SourceDataException("Namenszelle ohne Trennzeichen \", \": \"" + cell + "\"");
        }
        String nachname = cell.substring(0, separator);
        String vorname = cell.substring(separator + SEPARATOR.length());
        if (nachname.isBlank() || vorname.isBlank()) {
            throw new SourceDataException("Vor- oder Nachname leer: \"" + cell + "\"");
        }
        return new String[]{nachname, vorname};
    }

    /**
     * Zerlegt die Spalte "Strasse Nr, PLZ Ort".
     *
     * <p>Die Zelle besteht aus genau drei Teilen. Getrennt wird an den ersten
     * <strong>zwei</strong> Fundstellen von {@code ", "}; der Ort ist der ganze
     * Rest. Ein Komma im Ortsnamen gehoert damit zum Ort:
     * {@code "Boniverstr. 25, 17109, Demmin, Hansestadt"} ergibt den Ort
     * {@code "Demmin, Hansestadt"}. In der Quelle betrifft das 3 Zeilen.
     *
     * @return drei Elemente: {@code [Strasse, PLZ, Ort]}
     */
    public static String[] splitAddress(String cell) {
        if (cell == null) {
            throw new SourceDataException("Adresszelle fehlt");
        }
        int erster = cell.indexOf(SEPARATOR);
        int zweiter = erster < 0 ? -1 : cell.indexOf(SEPARATOR, erster + SEPARATOR.length());
        if (erster < 0 || zweiter < 0) {
            throw new SourceDataException("Adresszelle hat nicht drei Teile: \"" + cell + "\"");
        }
        String strasse = cell.substring(0, erster);
        String plz = cell.substring(erster + SEPARATOR.length(), zweiter);
        String ort = cell.substring(zweiter + SEPARATOR.length());
        if (strasse.isBlank() || plz.isBlank() || ort.isBlank()) {
            throw new SourceDataException("Adressteil leer: \"" + cell + "\"");
        }
        return new String[]{strasse, plz, ort};
    }

    /**
     * Liest das Geburtsdatum im Format {@code dd.MM.yyyy}.
     *
     * <p>Hier wird ausnahmsweise {@code strip()} angewandt: ein Datum ist ein
     * Zeitpunkt, kein Text. Anders als bei Namen und Adressen gibt es keinen
     * Wert, den ein Leerzeichen am Rand veraendern koennte - und die Zielspalte
     * ist {@code date}, nicht {@code text}.
     */
    public static LocalDate parseBirthDate(String cell) {
        if (cell == null || cell.isBlank()) {
            throw new SourceDataException("Geburtsdatum fehlt");
        }
        try {
            return LocalDate.parse(cell.strip(), DEUTSCHES_DATUM);
        } catch (DateTimeParseException e) {
            throw new SourceDataException("Geburtsdatum nicht im Format dd.MM.yyyy: \"" + cell + "\"");
        }
    }

    /**
     * Zerlegt die Spalte "Interessiert an". Werte in der Quelle: {@code m},
     * {@code w}, {@code mw}. {@code mw} sind zwei Interessen in einer Zelle
     * (1NF), jedes Zeichen ein Rohcode - keine Uebersetzung.
     *
     * @return die Codes ohne Dubletten; leere Liste bei leerer Zelle
     */
    public static java.util.List<String> splitInterests(String cell) {
        java.util.LinkedHashSet<String> codes = new java.util.LinkedHashSet<>();
        if (cell != null) {
            for (String zeichen : cell.strip().split("")) {
                if (!zeichen.isBlank()) {
                    codes.add(zeichen);
                }
            }
        }
        return new java.util.ArrayList<>(codes);
    }

    /**
     * Der quellenuebergreifende Schluessel: die E-Mail in Kleinschreibung.
     *
     * <p>Der Datenvertrag verlangt eindeutige E-Mails; ab Akt 2 gilt zusaetzlich
     * "Gross- und Kleinschreibung macht keinen Unterschied". Gespeichert wird die
     * Schreibweise der Quelle, verglichen wird ueber diesen Schluessel - genau
     * das macht auch der UNIQUE-Index auf {@code lower(email)}.
     *
     * <p>{@link Locale#ROOT} ist Absicht: mit tuerkischer Locale wuerde aus "I"
     * ein "&#x131;", und derselbe Import liefe auf zwei Rechnern anders.
     */
    public static String emailKey(String email) {
        if (email == null || email.isBlank()) {
            throw new SourceDataException("E-Mail fehlt");
        }
        return email.strip().toLowerCase(Locale.ROOT);
    }
}
