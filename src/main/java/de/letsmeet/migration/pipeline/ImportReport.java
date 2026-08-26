package de.letsmeet.migration.pipeline;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Was der Import getan hat: gezaehlte Mengen und abgewiesene Zeilen.
 *
 * <p>Die Zahlen sind das Material fuer die Befundnotiz. Die Abweisungen landen
 * zusaetzlich als CSV unter {@code results/} - "was nicht importiert ist, fehlt
 * sichtbar" heisst: mit Zeilennummer und Grund nachlesbar, nicht verschwunden.
 */
public final class ImportReport {

    /** Eine nicht uebernommene Zeile. */
    public record Rejection(int zeile, String grund) {

        String toCsvLine() {
            return zeile + ";\"" + grund.replace("\"", "\"\"") + "\"";
        }
    }

    private final String schritt;
    private final Map<String, Integer> zaehler = new LinkedHashMap<>();
    private final List<Rejection> abgewiesen = new ArrayList<>();

    public ImportReport(String schritt) {
        this.schritt = schritt;
    }

    public void count(String was, int wieViele) {
        zaehler.merge(was, wieViele, Integer::sum);
    }

    public void reject(int zeile, String grund) {
        abgewiesen.add(new Rejection(zeile, grund));
    }

    public int rejectedCount() {
        return abgewiesen.size();
    }

    public void printTo(Appendable out) throws IOException {
        out.append("== ").append(schritt).append(" ==\n");
        for (Map.Entry<String, Integer> eintrag : zaehler.entrySet()) {
            out.append("  ").append(eintrag.getKey()).append(": ")
                    .append(String.valueOf(eintrag.getValue())).append('\n');
        }
        out.append("  abgewiesene Zeilen: ").append(String.valueOf(abgewiesen.size())).append('\n');
        int gezeigt = Math.min(abgewiesen.size(), 5);
        for (int i = 0; i < gezeigt; i++) {
            out.append("    - Zeile ").append(String.valueOf(abgewiesen.get(i).zeile()))
                    .append(": ").append(abgewiesen.get(i).grund()).append('\n');
        }
        if (abgewiesen.size() > gezeigt) {
            out.append("    ... vollstaendig in results/abgewiesen.csv\n");
        }
    }

    /** Schreibt alle Abweisungen nach {@code results/abgewiesen.csv}. */
    public void writeRejections(Path verzeichnis) throws IOException {
        Files.createDirectories(verzeichnis);
        List<String> zeilen = new ArrayList<>();
        zeilen.add("zeile;grund");
        abgewiesen.forEach(r -> zeilen.add(r.toCsvLine()));
        Files.write(verzeichnis.resolve("abgewiesen.csv"), zeilen, StandardCharsets.UTF_8);
    }
}
