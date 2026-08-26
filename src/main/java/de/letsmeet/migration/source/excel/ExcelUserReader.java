package de.letsmeet.migration.source.excel;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Liest "Lets Meet DB Dump.xlsx" ein.
 *
 * <p>Wichtig: die Zellen werden hier <strong>nicht</strong> bereinigt. Kein
 * trim(), keine Normalisierung - der Datenvertrag fuer Akt 1 verlangt die
 * Inhalte unveraendert. Wo euch das noch begegnet, findet ihr beim Profilieren
 * der Quelle heraus.
 */
public final class ExcelUserReader {

    private static final int HEADER_ROWS = 1;
    private static final DataFormatter FORMATTER = new DataFormatter();

    /** @return alle Datenzeilen des ersten Tabellenblatts in Dateireihenfolge */
    public List<ExcelUserRow> read(Path file) throws IOException {
        List<ExcelUserRow> rows = new ArrayList<>();
        try (InputStream in = Files.newInputStream(file);
             Workbook workbook = new XSSFWorkbook(in)) {

            Sheet sheet = workbook.getSheetAt(0);
            for (Row row : sheet) {
                if (row.getRowNum() < HEADER_ROWS) {
                    continue;
                }
                if (isEmpty(row)) {
                    continue;
                }
                rows.add(new ExcelUserRow(
                        row.getRowNum() + 1,
                        cell(row, 0),
                        cell(row, 1),
                        cell(row, 2),
                        cell(row, 3),
                        cell(row, 4),
                        cell(row, 5),
                        cell(row, 6),
                        cell(row, 7)));
            }
        }
        return rows;
    }

    private static boolean isEmpty(Row row) {
        for (int i = 0; i < 8; i++) {
            if (!cell(row, i).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Zellinhalt als Text. Textzellen werden direkt uebernommen; alles andere
     * (Zahl, Datum) formatiert POI so, wie Excel es anzeigt.
     */
    private static String cell(Row row, int column) {
        Cell cell = row.getCell(column);
        if (cell == null) {
            return "";
        }
        if (cell.getCellType() == CellType.STRING) {
            return cell.getStringCellValue();
        }
        return FORMATTER.formatCellValue(cell);
    }
}
