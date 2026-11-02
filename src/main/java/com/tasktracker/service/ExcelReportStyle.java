package com.tasktracker.service;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.*;

/** Visual treatment only: preserve row positions and names used by the importers. */
final class ExcelReportStyle {
    private ExcelReportStyle() {}

    static XSSFCellStyle cell(XSSFWorkbook book, String fill, boolean bold) {
        XSSFCellStyle style = book.createCellStyle();
        XSSFFont font = book.createFont(); font.setFontName("Calibri");
        font.setFontHeightInPoints((short)11); font.setBold(bold);
        font.setColor(color(bold ? "FFFFFF" : "0C2E42")); style.setFont(font);
        style.setFillForegroundColor(color(fill)); style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setWrapText(true); style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setIndention((short)1);
        return style;
    }

    static XSSFColor color(String hex) {
        return new XSSFColor(java.util.HexFormat.of().parseHex(hex), new DefaultIndexedColorMap());
    }

    static void apply(XSSFWorkbook book, Sheet sheet, int columns, String title, int totalRow) {
        CellStyle header = cell(book, "0369A1", true);
        CellStyle plain = cell(book, "FFFFFF", false), stripe = cell(book, "F0F9FF", false);
        CellStyle total = cell(book, "0C2E42", true);
        // Cache variants by format and stripe, never create a new style for each cell.
        java.util.Map<String, CellStyle> formats = new java.util.HashMap<>();
        java.util.Map<String, CellStyle> states = new java.util.HashMap<>();
        for (Row row : sheet) {
            row.setHeightInPoints(34);
            int maxLines = 1;
            for (int c = 0; c < columns; c++) {
                Cell entry = row.getCell(c, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                CellStyle base = row.getRowNum() == 0 ? header : row.getRowNum() == totalRow ? total
                    : row.getRowNum() % 2 == 0 ? stripe : plain;
                short format = entry.getCellStyle().getDataFormat();
                String key = base.getIndex() + ":" + format;
                CellStyle styled = formats.computeIfAbsent(key, ignored -> {
                    CellStyle value = book.createCellStyle(); value.cloneStyleFrom(base); value.setDataFormat(format);
                    if (format != 0) value.setAlignment(HorizontalAlignment.RIGHT);
                    return value;
                });
                entry.setCellStyle(styled);
                boolean stateColumn = sheet.getSheetName().equals("Tareas") && (c == 3 || c == 4)
                    || sheet.getSheetName().equals("Incidentes") && (c == 4 || c == 16);
                if (row.getRowNum() > 0 && stateColumn && entry.getCellType() == CellType.STRING) {
                    String text = entry.getStringCellValue();
                    String stateColor = switch (text) {
                        case "Bloqueada", "Crítica" -> "B91C1C";
                        case "Completada", "Desplegado · cerrado" -> "166534";
                        case "Alta" -> "92400E";
                        default -> null;
                    };
                    if (stateColor != null) {
                        String stateKey = styled.getIndex() + ":" + stateColor;
                        CellStyle stateStyle = states.computeIfAbsent(stateKey, ignored -> {
                            var value = book.createCellStyle(); value.cloneStyleFrom(styled);
                            var font = book.createFont(); font.setFontName("Calibri"); font.setFontHeightInPoints((short)11);
                            font.setBold(true); font.setColor(color(stateColor)); value.setFont(font); return value;
                        });
                        entry.setCellStyle(stateStyle);
                    }
                }
                if (entry.getCellType() == CellType.STRING) {
                    int chars = Math.max(8, sheet.getColumnWidth(c) / 256 - 4);
                    int lines = java.util.Arrays.stream(entry.getStringCellValue().split("\n", -1))
                        .mapToInt(line -> Math.max(1, (int)Math.ceil((double)line.length() / chars))).sum();
                    maxLines = Math.max(maxLines, lines);
                }
            }
            if (row.getRowNum() > 0) row.setHeightInPoints(Math.min(409, Math.max(34, maxLines * 15 + 10)));
        }
        sheet.setDisplayGridlines(false); sheet.setPrintGridlines(false);
        sheet.setFitToPage(true); sheet.setHorizontallyCenter(true);
        sheet.getPrintSetup().setLandscape(true); sheet.getPrintSetup().setPaperSize(PrintSetup.A4_PAPERSIZE);
        sheet.getPrintSetup().setFitWidth((short)(columns > 10 ? 2 : 1)); sheet.getPrintSetup().setFitHeight((short)0);
        sheet.setRepeatingRows(new CellRangeAddress(0, 0, -1, -1));
        sheet.getHeader().setLeft("TaskTracker"); sheet.getHeader().setRight(title);
        sheet.getFooter().setLeft("&D"); sheet.getFooter().setRight("Página &P de &N");
        book.setPrintArea(book.getSheetIndex(sheet), 0, columns - 1, 0, sheet.getLastRowNum());
        ((XSSFSheet)sheet).setTabColor(color("0369A1"));
    }
}
