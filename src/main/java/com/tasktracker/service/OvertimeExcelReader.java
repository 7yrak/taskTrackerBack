package com.tasktracker.service;

import com.tasktracker.dto.OvertimeRequest;
import com.tasktracker.model.Project;
import com.tasktracker.repository.ProjectRepository;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.*;

@Component
@RequiredArgsConstructor
public class OvertimeExcelReader {
    private final ProjectRepository projects;
    private final Validator validator;
    private static final String[] HEADERS = {"Fecha", "Persona", "Horas", "Tema/Motivo", "Cliente", "Proyecto"};

    public List<OvertimeRequest> read(MultipartFile file) {
        if (file.isEmpty()) throw new IllegalArgumentException("El archivo Excel está vacío");
        List<Project> available = projects.findAll();
        try (var input = file.getInputStream(); var book = new XSSFWorkbook(input)) {
            Sheet sheet = book.getSheet("Horas extras");
            if (sheet == null) throw new IllegalArgumentException("Falta la hoja Horas extras del Excel descargado");
            var formatter = new DataFormatter(Locale.forLanguageTag("es-CL"));
            var evaluator = book.getCreationHelper().createFormulaEvaluator();
            for (int c = 0; c < HEADERS.length; c++) {
                if (!HEADERS[c].equalsIgnoreCase(text(sheet.getRow(0), c, formatter, evaluator)))
                    throw new IllegalArgumentException("Columna " + (c + 1) + ": se esperaba " + HEADERS[c]);
            }
            String statusHeader = text(sheet.getRow(0), 6, formatter, evaluator);
            if (!statusHeader.isEmpty() && !statusHeader.equalsIgnoreCase("Enviado a jefatura"))
                throw new IllegalArgumentException("La séptima columna debe ser Enviado a jefatura");
            List<OvertimeRequest> result = new ArrayList<>();
            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                String[] values = new String[7];
                for (int c = 0; c < values.length; c++) values[c] = text(row, c, formatter, evaluator);
                if (Arrays.stream(values).allMatch(String::isEmpty)) continue;
                // The downloaded total is metadata, even when users append rows below it.
                if (values[0].isEmpty() && values[1].equals("Total horas")
                        && values[3].isEmpty() && values[4].isEmpty() && values[5].isEmpty() && values[6].isEmpty()) continue;
                try {
                    var matching = available.stream().filter(p -> p.getName().trim().equalsIgnoreCase(values[5])).toList();
                    if (matching.size() != 1)
                        throw new IllegalArgumentException("Proyecto inexistente o ambiguo: " + values[5]);
                    var request = new OvertimeRequest(matching.get(0).getId(), date(row.getCell(0), values[0]),
                            values[1], values[3], hours(row.getCell(2), values[2], evaluator), values[4], sent(values[6]));
                    var violations = validator.validate(request);
                    if (!violations.isEmpty()) throw new IllegalArgumentException(violations.stream()
                            .map(v -> v.getPropertyPath() + ": " + v.getMessage()).sorted().findFirst().orElseThrow());
                    result.add(request);
                } catch (RuntimeException e) {
                    throw new IllegalArgumentException("Fila " + (r + 1) + ": " + e.getMessage(), e);
                }
            }
            return result;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("No se pudo leer el archivo .xlsx", e);
        }
    }

    private String text(Row row, int column, DataFormatter formatter, FormulaEvaluator evaluator) {
        return row == null ? "" : formatter.formatCellValue(row.getCell(column), evaluator).trim();
    }

    private LocalDate date(Cell cell, String value) {
        if (cell != null && cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell))
            return cell.getLocalDateTimeCellValue().toLocalDate();
        for (String pattern : List.of("uuuu-MM-dd", "d/M/uuuu", "d-M-uuuu")) {
            try { return LocalDate.parse(value, DateTimeFormatter.ofPattern(pattern).withResolverStyle(ResolverStyle.STRICT)); }
            catch (java.time.format.DateTimeParseException ignored) { }
        }
        throw new IllegalArgumentException("Fecha inválida: " + value);
    }

    private BigDecimal hours(Cell cell, String value, FormulaEvaluator evaluator) {
        if (cell != null && cell.getCellType() == CellType.NUMERIC)
            return BigDecimal.valueOf(cell.getNumericCellValue()).stripTrailingZeros();
        if (cell != null && cell.getCellType() == CellType.FORMULA) {
            CellValue evaluated = evaluator.evaluate(cell);
            if (evaluated.getCellType() == CellType.NUMERIC)
                return BigDecimal.valueOf(evaluated.getNumberValue()).stripTrailingZeros();
        }
        try { return new BigDecimal(value.replace(',', '.')).stripTrailingZeros(); }
        catch (NumberFormatException e) { throw new IllegalArgumentException("Horas inválidas: " + value); }
    }

    private boolean sent(String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "sí", "si", "true", "enviado", "1" -> true;
            case "", "no", "false", "pendiente", "0" -> false;
            default -> throw new IllegalArgumentException("Enviado a jefatura debe ser Sí o No");
        };
    }
}
