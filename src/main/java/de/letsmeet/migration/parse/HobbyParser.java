package de.letsmeet.migration.parse;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Zerlegt die Excel-Spalte "Hobby1 %Prio1%; Hobby2 %Prio2%; ..." in einzelne
 * Hobbys mit Prioritaet. Getrennt wird an {@code ;}; ein doppelter Name in einer
 * Zelle wird nur einmal zurueckgegeben (Datenvertrag: "dasselbe Hobby aus
 * derselben Quelle nur einmal").
 */
public final class HobbyParser {

    private static final Pattern EINTRAG = Pattern.compile("^(.*?)\\s*%\\s*(-?\\d+)\\s*%$");

    private HobbyParser() {
    }

    public record Hobby(String name, int prioritaet) {
    }

    public static List<Hobby> parse(String cell) {
        Map<String, Hobby> nachName = new LinkedHashMap<>();
        if (cell == null || cell.isBlank()) {
            return List.of();
        }
        for (String teil : cell.split(";")) {
            String eintrag = teil.strip();
            if (eintrag.isEmpty()) {
                continue;
            }
            Matcher m = EINTRAG.matcher(eintrag);
            if (!m.matches()) {
                throw new SourceDataException(
                        "Hobby-Eintrag nicht im Format \"Name %Prio%\": \"" + eintrag + "\"");
            }
            String name = m.group(1).strip();
            if (name.isEmpty()) {
                throw new SourceDataException("Hobby ohne Namen: \"" + eintrag + "\"");
            }
            nachName.putIfAbsent(name, new Hobby(name, Integer.parseInt(m.group(2))));
        }
        return new ArrayList<>(nachName.values());
    }
}
