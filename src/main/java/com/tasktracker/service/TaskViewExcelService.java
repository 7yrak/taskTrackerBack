package com.tasktracker.service;

import com.tasktracker.dto.TaskViewExportRow;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.io.*;
import java.util.*;

@Service @RequiredArgsConstructor
public class TaskViewExcelService {
    private final TaskService tasks;

    @Transactional(readOnly = true)
    public byte[] export(List<TaskViewExportRow> selected) throws IOException {
        if (selected.isEmpty()) throw new IllegalArgumentException("No hay tareas para exportar");
        try (var book = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            var sheet = book.createSheet("Tareas");
            String[] headers = {"Título", "Proyecto", "Asignado a", "Estado", "Prioridad", "Inicio", "Vencimiento", "Avance real", "Avance esperado"};
            int[] widths = {60, 30, 32, 20, 16, 16, 16, 18, 18};
            var header = sheet.createRow(0);
            for (int c = 0; c < headers.length; c++) {
                header.createCell(c).setCellValue(headers[c]); sheet.setColumnWidth(c, widths[c] * 256);
            }
            var date = book.createCellStyle(); date.setDataFormat(book.createDataFormat().getFormat("dd/mm/yyyy"));
            var percent = book.createCellStyle(); percent.setDataFormat(book.createDataFormat().getFormat("0%"));
            int index = 1;
            for (var entry : selected) {
                var task = tasks.findById(entry.id()); var row = sheet.createRow(index++);
                row.createCell(0).setCellValue(task.title()); row.createCell(1).setCellValue(task.projectName());
                row.createCell(2).setCellValue(String.join(", ", task.assigneeNames()));
                row.createCell(3).setCellValue(status(task.status())); row.createCell(4).setCellValue(priority(task.priority()));
                if (task.startDate() != null) { row.createCell(5).setCellValue(task.startDate()); row.getCell(5).setCellStyle(date); }
                if (task.dueDate() != null) { row.createCell(6).setCellValue(task.dueDate()); row.getCell(6).setCellStyle(date); }
                row.createCell(7).setCellValue(task.progressActual() / 100.0); row.getCell(7).setCellStyle(percent);
                if (task.progressExpected() != null) { row.createCell(8).setCellValue(task.progressExpected() / 100.0); row.getCell(8).setCellStyle(percent); }
            }
            ExcelReportStyle.apply(book, sheet, headers.length, "Vista de tareas · " + selected.size() + " registros", -1);
            Map<String, CellStyle> indents = new HashMap<>();
            for (int r = 1; r <= selected.size(); r++) {
                var cell = sheet.getRow(r).getCell(0); int level = Math.min(15, selected.get(r - 1).level() + 1);
                String key = cell.getCellStyle().getIndex() + ":" + level;
                CellStyle style = indents.computeIfAbsent(key, ignored -> {
                    var value = book.createCellStyle(); value.cloneStyleFrom(cell.getCellStyle()); value.setIndention((short)level); return value;
                });
                cell.setCellStyle(style);
            }
            sheet.createFreezePane(1, 1);
            sheet.setAutoFilter(new CellRangeAddress(0, selected.size(), 0, headers.length - 1));
            book.write(output); return output.toByteArray();
        }
    }

    private String status(String value) { return switch (value) {
        case "TODO" -> "Pendiente"; case "IN_PROGRESS" -> "En progreso"; case "IN_REVIEW" -> "En revisión";
        case "DONE" -> "Completada"; case "BLOCKED" -> "Bloqueada"; case "STOPPED" -> "Detenido"; default -> value;
    }; }
    private String priority(String value) { return switch (value) {
        case "LOW" -> "Baja"; case "MEDIUM" -> "Media"; case "HIGH" -> "Alta"; case "CRITICAL" -> "Crítica"; default -> value;
    }; }
}
