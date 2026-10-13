package com.tasktracker.service;

import com.tasktracker.dto.*;
import com.tasktracker.model.*;
import com.tasktracker.repository.*;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.text.Normalizer;
import java.time.*;
import java.time.format.*;
import java.util.*;

@Service @RequiredArgsConstructor
public class IncidentExcelService {
    public static final String[] HEADERS = {"Proyecto", "Título", "Descripción", "Ambiente", "Prioridad", "Reportante",
        "Desarrollo (email)", "QA (email)", "Tarea ID", "Fecha objetivo", "Pasos reproducción", "Resultado esperado",
        "Resultado obtenido", "Estado reportado", "Fecha entrega QA", "Observaciones", "Estado actual (referencia)"};
    private static final int MAX_ROWS = 2000;
    private final IncidentRepository incidents;
    private final IncidentAttachmentRepository attachments;
    private final ProjectRepository projects;
    private final TeamMemberRepository members;
    private final TaskRepository tasks;
    private final IncidentService incidentService;
    private final Validator validator;

    @Transactional(readOnly = true)
    public IncidentImportPreview preview(MultipartFile file, Long projectId, String environment, String reporter) {
        Project project = project(projectId);
        Parsed parsed = parse(file, project, environment, reporter);
        List<Incident> current = incidents.findByProjectIdOrderByCreatedAtDesc(projectId);
        return new IncidentImportPreview(parsed.requests().size(), current.size(), project.getName(), fingerprint(current),
            parsed.rows(), parsed.errors(), List.of("Todos los incidentes nuevos comenzarán en Registrado. Los estados reportados son solo referencia.",
            "El reemplazo elimina los incidentes, comentarios, historial y adjuntos anteriores de este proyecto. El Excel no incluye archivos adjuntos."));
    }

    @Transactional
    public IncidentImportResult replace(MultipartFile file, Long projectId, String environment, String reporter,
        boolean confirmed, String expectedFingerprint) {
        if (!confirmed) throw new IllegalArgumentException("Debe confirmar el reemplazo de los incidentes del proyecto");
        Project project = projects.findLockedById(projectId).orElseThrow(() -> new IllegalArgumentException("Proyecto no encontrado"));
        Parsed parsed = parse(file, project, environment, reporter);
        if (!parsed.errors().isEmpty()) throw new IllegalArgumentException(String.join("\n", parsed.errors()));
        List<Incident> current = incidents.findLockedByProjectId(projectId);
        if (expectedFingerprint == null || !expectedFingerprint.equals(fingerprint(current)))
            throw new IllegalArgumentException("Los incidentes del proyecto cambiaron. Vuelve a validar el archivo antes de reemplazar.");
        // Validate every row first, then remove and recreate inside this same transaction.
        attachments.deleteByIncidentProjectId(projectId);
        attachments.flush();
        for (Incident incident : current) incident.getAttachments().clear();
        incidents.deleteAll(current);
        incidents.flush();
        for (IncidentRequest request : parsed.requests()) incidentService.create(request);
        return new IncidentImportResult(parsed.requests().size(), current.size(), project.getName());
    }

    @Transactional(readOnly = true)
    public byte[] export(Long projectId) throws IOException {
        Project project = project(projectId);
        List<Incident> list = incidents.findByProjectIdOrderByCreatedAtDesc(projectId);
        try (XSSFWorkbook book = workbook(project.getName()); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = book.getSheet("Incidentes");
            CellStyle body = bodyStyle(book);
            CellStyle dateStyle = book.createCellStyle(); dateStyle.cloneStyleFrom(body);
            dateStyle.setDataFormat(book.createDataFormat().getFormat("dd/mm/yyyy"));
            int rowIndex = 1;
            for (Incident i : list) {
                Row row = sheet.createRow(rowIndex++); row.setHeightInPoints(78);
                Object[] values = {project.getName(), i.getTitle(), i.getDescription(), i.getDetectedEnvironment().name(),
                    priorityLabel(i.getPriority()), i.getReporter(), i.getDeveloper() == null ? null : i.getDeveloper().getEmail(),
                    i.getQa() == null ? null : i.getQa().getEmail(), i.getTask() == null ? null : i.getTask().getId(), i.getDueDate(),
                    i.getReproductionSteps(), i.getExpectedResult(), i.getActualResult(), i.getReportedStatus(), i.getQaDeliveryDate(),
                    i.getImportNotes(), statusLabel(i.getStatus())};
                for (int col = 0; col < values.length; col++) write(row.createCell(col), values[col], body, dateStyle);
            }
            sheet.setAutoFilter(new CellRangeAddress(0, Math.max(1, list.size()), 0, HEADERS.length - 1));
            book.write(output); return output.toByteArray();
        }
    }

    @Transactional(readOnly = true)
    public byte[] template(Long projectId) throws IOException {
        try (XSSFWorkbook book = workbook(project(projectId).getName()); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            book.write(output); return output.toByteArray();
        }
    }

    private Project project(Long id) {
        if (id == null) throw new IllegalArgumentException("Seleccione un proyecto");
        return projects.findById(id).orElseThrow(() -> new IllegalArgumentException("Proyecto no encontrado"));
    }

    private Parsed parse(MultipartFile file, Project project, String defaultEnvironment, String defaultReporter) {
        List<IncidentRequest> requests = new ArrayList<>();
        List<IncidentImportPreview.PreviewRow> previewRows = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        if (file == null || file.isEmpty() || file.getSize() > 10 * 1024 * 1024)
            throw new IllegalArgumentException("Seleccione un Excel .xlsx de hasta 10 MB");
        if (!Optional.ofNullable(file.getOriginalFilename()).orElse("").toLowerCase(Locale.ROOT).endsWith(".xlsx"))
            throw new IllegalArgumentException("El archivo debe estar en formato Excel .xlsx");
        List<TeamMember> people = members.findAll();
        try (XSSFWorkbook book = openWorkbook(file)) {
            Sheet sheet = book.getSheet("Incidentes");
            if (sheet == null) throw new IllegalArgumentException("El archivo debe contener la hoja Incidentes");
            if (sheet.getLastRowNum() > MAX_ROWS) throw new IllegalArgumentException("El archivo permite hasta " + MAX_ROWS + " filas de incidentes");
            Row header = sheet.getRow(0);
            if (header == null) throw new IllegalArgumentException("Faltan los encabezados en la primera fila");
            Map<String, Integer> columns = new HashMap<>();
            for (Cell cell : header) {
                String key = normalize(text(cell));
                if (!key.isBlank() && columns.putIfAbsent(key, cell.getColumnIndex()) != null)
                    throw new IllegalArgumentException("Encabezado repetido: " + text(cell));
            }
            for (String required : List.of("Título", "Descripción", "Ambiente", "Prioridad", "Reportante"))
                if (!columns.containsKey(normalize(required))) throw new IllegalArgumentException("Falta la columna " + required);
            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;
                final int number = r + 1;
                try {
                    if (empty(row)) continue;
                    for (Cell cell : row) if (cell.getCellType() == CellType.FORMULA)
                        throw new IllegalArgumentException("No se admiten fórmulas; pega los datos como valores");
                    String suppliedProject = value(row, columns, "Proyecto");
                    if (!suppliedProject.isBlank() && !normalize(suppliedProject).equals(normalize(project.getName())))
                        throw new IllegalArgumentException("El proyecto de la fila no coincide con el proyecto seleccionado");
                    String env = fallback(value(row, columns, "Ambiente"), defaultEnvironment);
                    String title = value(row, columns, "Título"), description = value(row, columns, "Descripción");
                    String reporter = fallback(value(row, columns, "Reportante"), defaultReporter);
                    TaskPriority priority = parsePriority(value(row, columns, "Prioridad"));
                    IncidentEnvironment environment;
                    try { environment = IncidentEnvironment.valueOf(env.toUpperCase(Locale.ROOT)); }
                    catch (IllegalArgumentException e) { throw new IllegalArgumentException("Ambiente requerido: DESA, CERT, UAT o PROD (completa la celda o el valor por defecto)"); }
                    Long developerId = memberId(value(row, columns, "Desarrollo (email)"), people);
                    Long qaId = memberId(value(row, columns, "QA (email)"), people);
                    Long taskId = taskId(value(row, columns, "Tarea ID"), project.getId());
                    IncidentRequest request = new IncidentRequest(title, description, environment, priority, reporter, project.getId(), taskId,
                        developerId, qaId, value(row, columns, "Pasos reproducción"), value(row, columns, "Resultado esperado"),
                        value(row, columns, "Resultado obtenido"), date(row, columns, "Fecha objetivo"), value(row, columns, "Estado reportado"),
                        date(row, columns, "Fecha entrega QA"), notes(row, columns));
                    var violations = validator.validate(request);
                    if (!violations.isEmpty()) throw new IllegalArgumentException(violations.stream().map(v -> v.getPropertyPath() + ": " + v.getMessage()).sorted().reduce((a,b) -> a + "; " + b).orElse("Datos no válidos"));
                    requests.add(request);
                    previewRows.add(new IncidentImportPreview.PreviewRow(number, title, environment.name(), priorityLabel(priority), reporter, request.reportedStatus()));
                } catch (IllegalArgumentException e) { errors.add("Fila " + number + ": " + e.getMessage()); }
            }
            if (requests.isEmpty() && errors.isEmpty()) errors.add("El Excel no contiene incidentes. No se reemplazará el listado con un archivo vacío.");
        } catch (IOException | org.apache.poi.ooxml.POIXMLException e) {
            throw new IllegalArgumentException("No se pudo leer el Excel. Descarga la plantilla estándar y vuelve a intentarlo.", e);
        }
        return new Parsed(requests, previewRows, errors);
    }

    private XSSFWorkbook openWorkbook(MultipartFile file) {
        try { return new XSSFWorkbook(file.getInputStream()); }
        catch (IOException | RuntimeException e) { throw new IllegalArgumentException("El archivo no es un Excel .xlsx válido. Usa la plantilla estándar.", e); }
    }

    private String notes(Row row, Map<String, Integer> columns) {
        String notes = value(row, columns, "Observaciones").replaceAll("(?m)^Estado al exportar:.*(?:\\n|$)", "").trim();
        String state = value(row, columns, "Estado actual (referencia)");
        // Keep the last exported stage as context without restoring its approvals.
        if (!state.isBlank()) notes = (notes.isBlank() ? "" : notes + "\n") + "Estado al exportar: " + state;
        return notes;
    }
    private Long memberId(String email, List<TeamMember> people) {
        if (email.isBlank()) return null;
        return people.stream().filter(m -> m.getEmail().equalsIgnoreCase(email)).findFirst().map(TeamMember::getId)
            .orElseThrow(() -> new IllegalArgumentException("Responsable no encontrado por email: " + email));
    }
    private Long taskId(String text, Long projectId) {
        if (text.isBlank()) return null;
        try {
            long id = Long.parseLong(text);
            Task task = tasks.findById(id).orElseThrow(() -> new IllegalArgumentException("Tarea no encontrada: " + id));
            if (task.getProject() == null || !projectId.equals(task.getProject().getId())) throw new IllegalArgumentException("La tarea debe pertenecer al proyecto seleccionado");
            return id;
        } catch (NumberFormatException e) { throw new IllegalArgumentException("Tarea ID debe ser un número entero"); }
    }
    private LocalDate date(Row row, Map<String, Integer> columns, String column) {
        Integer index = columns.get(normalize(column)); Cell cell = index == null ? null : row.getCell(index);
        if (cell == null || text(cell).isBlank()) return null;
        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) return cell.getLocalDateTimeCellValue().toLocalDate();
        for (String pattern : List.of("uuuu-MM-dd", "dd/MM/uuuu", "dd-MM-uuuu")) {
            try { return LocalDate.parse(text(cell), DateTimeFormatter.ofPattern(pattern).withResolverStyle(ResolverStyle.STRICT)); }
            catch (DateTimeParseException ignored) { }
        }
        throw new IllegalArgumentException(column + " debe ser una fecha válida (dd/mm/aaaa o aaaa-mm-dd)");
    }
    private TaskPriority parsePriority(String value) {
        return switch (normalize(value)) {
            case "", "media", "medium" -> TaskPriority.MEDIUM;
            case "baja", "low" -> TaskPriority.LOW;
            case "alta", "high" -> TaskPriority.HIGH;
            case "critica", "critical" -> TaskPriority.CRITICAL;
            default -> throw new IllegalArgumentException("Prioridad debe ser Baja, Media, Alta o Crítica");
        };
    }
    private String value(Row row, Map<String, Integer> columns, String header) {
        Integer index = columns.get(normalize(header)); return index == null ? "" : text(row.getCell(index));
    }
    private static String text(Cell cell) {
        if (cell == null || cell.getCellType() == CellType.BLANK) return "";
        if (cell.getCellType() == CellType.ERROR) throw new IllegalArgumentException("La celda contiene un error de Excel");
        return new DataFormatter(Locale.ROOT).formatCellValue(cell).trim();
    }
    private boolean empty(Row row) { for (Cell cell : row) if (cell.getCellType() != CellType.BLANK && !text(cell).isBlank()) return false; return true; }
    private String fallback(String value, String defaultValue) { return value.isBlank() ? Optional.ofNullable(defaultValue).orElse("").trim() : value; }
    private static String normalize(String text) { return Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "").trim().toLowerCase(Locale.ROOT); }
    private String fingerprint(List<Incident> list) {
        String value = list.stream().sorted(Comparator.comparing(Incident::getId)).map(i -> i.getId() + ":" + i.getVersion()).reduce("", (a,b) -> a + ";" + b);
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private record Parsed(List<IncidentRequest> requests, List<IncidentImportPreview.PreviewRow> rows, List<String> errors) {}

    private XSSFWorkbook workbook(String projectName) {
        XSSFWorkbook book = new XSSFWorkbook(); Sheet sheet = book.createSheet("Incidentes");
        sheet.setDisplayGridlines(false); sheet.createFreezePane(2, 1);
        CellStyle headerStyle = book.createCellStyle(); headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex()); headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        Font headerFont = book.createFont(); headerFont.setFontName("Arial"); headerFont.setFontHeightInPoints((short)10); headerFont.setBold(true); headerFont.setColor(IndexedColors.WHITE.getIndex());
        headerStyle.setFont(headerFont); headerStyle.setWrapText(true); headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        Row header = sheet.createRow(0); header.setHeightInPoints(36);
        for (int c=0;c<HEADERS.length;c++) { Cell cell=header.createCell(c); cell.setCellValue(HEADERS[c]); cell.setCellStyle(headerStyle); sheet.setColumnWidth(c, (c==1 || c==2 || c>=10 ? 40 : 24)*256); }
        sheet.setAutoFilter(new CellRangeAddress(0, 1, 0, HEADERS.length-1));
        var helper = sheet.getDataValidationHelper();
        for (int column : List.of(3,4)) {
            String[] allowed = column==3 ? new String[]{"DESA","CERT","UAT","PROD"} : new String[]{"Baja","Media","Alta","Crítica"};
            var validation = helper.createValidation(helper.createExplicitListConstraint(allowed), new org.apache.poi.ss.util.CellRangeAddressList(1, MAX_ROWS, column, column));
            validation.setShowErrorBox(true); validation.setEmptyCellAllowed(true); validation.createErrorBox("Valor no válido", "Seleccione un valor de la lista"); sheet.addValidationData(validation);
        }
        Sheet guide=book.createSheet("Instrucciones"); guide.setDisplayGridlines(false); guide.setColumnWidth(0,100*256);
        String[] instructions={"Carga de incidentes: " + projectName,
            "Completa una fila por incidente en la hoja Incidentes. Conserva los encabezados de la primera fila.",
            "Selecciona el proyecto al subir. Proyecto puede quedar vacío; si se completa debe coincidir con el seleccionado.",
            "Título y Descripción son obligatorios. Ambiente y Reportante se pueden completar aquí o al subir el archivo.",
            "Ambientes: DESA, CERT, UAT y PROD. Prioridades: Baja, Media, Alta y Crítica. Una prioridad vacía se carga como Media.",
            "Responsables opcionales: escribe el email de un miembro ya registrado. Tarea ID debe pertenecer al proyecto.",
            "Fechas: usa fechas de Excel o dd/mm/aaaa, dd-mm-aaaa o aaaa-mm-dd. Fecha entrega QA es solo referencia.",
            "Estado reportado, Observaciones y Estado actual (referencia) conservan contexto. No otorgan aprobaciones.",
            "La carga reemplaza todos los incidentes del proyecto, con sus comentarios, historial y adjuntos. Otros proyectos se conservan.",
            "Todos comienzan en Registrado y requieren certificar QA y aprobar cliente nuevamente.",
            "No se importan adjuntos ni historial anterior. El Excel es un intercambio de datos, no un respaldo completo.",
            "No uses fórmulas. Máximo 2000 incidentes y 10 MB por archivo. Un Excel vacío o con errores no reemplaza nada."};
        CellStyle body=bodyStyle(book);
        for(int r=0;r<instructions.length;r++){ Row row=guide.createRow(r); row.setHeightInPoints(r==0?30:42); Cell cell=row.createCell(0); cell.setCellValue(instructions[r]); cell.setCellStyle(body); }
        return book;
    }
    private CellStyle bodyStyle(XSSFWorkbook book) {
        CellStyle style=book.createCellStyle(); style.setWrapText(true); style.setVerticalAlignment(VerticalAlignment.TOP);
        Font font=book.createFont(); font.setFontName("Arial"); font.setFontHeightInPoints((short)10); style.setFont(font); return style;
    }
    private void write(Cell cell,Object value,CellStyle body,CellStyle dateStyle) {
        cell.setCellStyle(body);
        if(value==null) return;
        if(value instanceof LocalDate date){ cell.setCellValue(date); cell.setCellStyle(dateStyle); }
        else if(value instanceof Number n) cell.setCellValue(n.doubleValue());
        else { String text=value.toString(); if(text.length()>32767) throw new IllegalArgumentException("Una descripción u observación supera el límite de texto de Excel (32767 caracteres)"); cell.setCellValue(text); }
    }
    private static String priorityLabel(TaskPriority value){return switch(value){case LOW->"Baja";case MEDIUM->"Media";case HIGH->"Alta";case CRITICAL->"Crítica";};}
    private static String statusLabel(IncidentStatus value){return switch(value){case REGISTERED->"Registrado";case DEVELOPMENT->"En desarrollo · DESA";case CERT->"En certificación · CERT";case QA_APPROVED->"Certificado por QA";case UAT->"Validación del cliente · UAT";case CLIENT_APPROVED->"Aprobado · pendiente PROD";case CLOSED->"Desplegado · cerrado";case CANCELLED->"Cancelado";};}
}
