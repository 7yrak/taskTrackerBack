package com.tasktracker.service;

import com.tasktracker.dto.TaskImportResult;
import com.tasktracker.dto.TaskRequest;
import com.tasktracker.model.Project;
import com.tasktracker.model.TeamMember;
import com.tasktracker.repository.ProjectRepository;
import com.tasktracker.repository.TaskRepository;
import com.tasktracker.repository.TeamMemberRepository;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

@Service
@RequiredArgsConstructor
public class TaskImportService {

    private final TaskService         taskService;
    private final TaskRepository      taskRepository;
    private final ProjectRepository   projectRepository;
    private final TeamMemberRepository memberRepository;

    private static final Map<String, String> STATUS_MAP = Map.ofEntries(
        Map.entry("pendiente",    "TODO"),
        Map.entry("todo",         "TODO"),
        Map.entry("en progreso",  "IN_PROGRESS"),
        Map.entry("in_progress",  "IN_PROGRESS"),
        Map.entry("en revisión",  "IN_REVIEW"),
        Map.entry("en revision",  "IN_REVIEW"),
        Map.entry("in_review",    "IN_REVIEW"),
        Map.entry("completada",   "DONE"),
        Map.entry("done",         "DONE"),
        Map.entry("bloqueada",    "BLOCKED"),
        Map.entry("blocked",      "BLOCKED")
    );

    private static final Map<String, String> PRIORITY_MAP = Map.of(
        "baja",     "LOW",
        "low",      "LOW",
        "media",    "MEDIUM",
        "medium",   "MEDIUM",
        "alta",     "HIGH",
        "high",     "HIGH",
        "crítica",  "CRITICAL",
        "critica",  "CRITICAL",
        "critical", "CRITICAL"
    );

    // ── Columns (0-based) ─────────────────────────────────────────────────────
    // A: Título        B: Tarea padre    C: Descripción
    // D: Estado        E: Prioridad      F: Proyecto
    // G: Asignado a    H: Fecha inicio   I: Fecha vencimiento   J: % Avance

    // ── Import ────────────────────────────────────────────────────────────────

    public TaskImportResult importFromExcel(MultipartFile file) throws IOException {
        List<String> warnings = new ArrayList<>();
        int imported = 0;
        int skipped  = 0;

        List<Project>    allProjects = projectRepository.findAll();
        List<TeamMember> allMembers  = memberRepository.findAll();

        // Pre-populate title→id map with tasks already in DB (case-insensitive)
        Map<String, Long> titleToId = new HashMap<>();
        taskRepository.findAll().forEach(t -> titleToId.put(t.getTitle().trim().toLowerCase(), t.getId()));

        try (Workbook wb = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = wb.getSheetAt(0);

            for (int rowNum = 1; rowNum <= sheet.getLastRowNum(); rowNum++) {
                Row row = sheet.getRow(rowNum);
                if (row == null || isRowEmpty(row)) continue;

                try {
                    String title = getString(row, 0).trim();
                    if (title.isBlank()) {
                        warnings.add("Fila " + (rowNum + 1) + ": título vacío, se omitió.");
                        skipped++;
                        continue;
                    }

                    String parentTitle  = getString(row, 1);
                    String description  = getString(row, 2);
                    String statusRaw    = getString(row, 3);
                    String priorityRaw  = getString(row, 4);
                    String projectName  = getString(row, 5);
                    String assigneesRaw = getString(row, 6);
                    String startDateRaw = getString(row, 7);
                    String dueDateRaw   = getString(row, 8);
                    Integer progressRaw = getOptionalInt(row, 9);
                    String status       = resolveEnum(statusRaw, STATUS_MAP, "TODO");
                    int progress        = progressRaw != null
                        ? progressRaw
                        : ("DONE".equals(status) ? 100 : 0);

                    // Resolve parent task
                    Long parentId = null;
                    if (!parentTitle.isBlank()) {
                        parentId = titleToId.get(parentTitle.trim().toLowerCase());
                        if (parentId == null) {
                            warnings.add("Fila " + (rowNum + 1) + ": tarea padre \"" + parentTitle.trim() +
                                "\" no encontrada. Asegúrese de que la tarea padre esté en filas anteriores o ya exista en el sistema.");
                        }
                    }

                    // Resolve project
                    Long projectId = null;
                    if (!projectName.isBlank()) {
                        Optional<Project> proj = allProjects.stream()
                            .filter(p -> p.getName().equalsIgnoreCase(projectName.trim()))
                            .findFirst();
                        if (proj.isPresent()) {
                            projectId = proj.get().getId();
                        } else {
                            warnings.add("Fila " + (rowNum + 1) + ": proyecto \"" + projectName.trim() + "\" no encontrado.");
                        }
                    }

                    // Resolve assignees
                    List<Long> assigneeIds = new ArrayList<>();
                    if (!assigneesRaw.isBlank()) {
                        for (String name : assigneesRaw.split(";")) {
                            String trimmed = name.trim();
                            if (trimmed.isEmpty()) continue;
                            Optional<TeamMember> member = allMembers.stream()
                                .filter(m -> m.getName().equalsIgnoreCase(trimmed))
                                .findFirst();
                            if (member.isPresent()) {
                                assigneeIds.add(member.get().getId());
                            } else {
                                warnings.add("Fila " + (rowNum + 1) + ": miembro \"" + trimmed + "\" no encontrado, se ignoró.");
                            }
                        }
                    }

                    TaskRequest req = new TaskRequest(
                        title,
                        description.isBlank() ? null : description.trim(),
                        status,
                        resolveEnum(priorityRaw, PRIORITY_MAP, "MEDIUM"),
                        projectId,
                        assigneeIds.isEmpty() ? null : assigneeIds,
                        parseDate(startDateRaw),
                        parseDate(dueDateRaw),
                        Math.max(0, Math.min(100, progress)),
                        parentId,
                        null
                    );

                    var dto = taskService.create(req);
                    // Register in map so subsequent rows in the same file can use this as parent
                    titleToId.put(title.toLowerCase(), dto.id());
                    imported++;

                } catch (Exception e) {
                    warnings.add("Fila " + (rowNum + 1) + ": error — " + e.getMessage());
                    skipped++;
                }
            }
        }

        return new TaskImportResult(imported, skipped, warnings);
    }

    // ── Export ────────────────────────────────────────────────────────────────

    public byte[] exportToExcel() throws IOException {
        List<com.tasktracker.dto.TaskDTO> allTasks = taskService.findAll(null, null, null, null);

        // Build tree map: parentId → children list
        Map<Long, List<com.tasktracker.dto.TaskDTO>> childrenMap = new java.util.LinkedHashMap<>();
        List<com.tasktracker.dto.TaskDTO> roots = new ArrayList<>();
        Map<Long, String> idToTitle = new HashMap<>();

        for (var t : allTasks) idToTitle.put(t.id(), t.title());

        for (var t : allTasks) {
            if (t.parentId() == null) roots.add(t);
            else childrenMap.computeIfAbsent(t.parentId(), k -> new ArrayList<>()).add(t);
        }

        // Pre-order traversal so parents always appear before children
        List<com.tasktracker.dto.TaskDTO> ordered = new ArrayList<>();
        walkTree(roots, childrenMap, ordered);

        Map<String, String> STATUS_ES = Map.of(
            "TODO", "Pendiente", "IN_PROGRESS", "En progreso",
            "IN_REVIEW", "En revisión", "DONE", "Completada", "BLOCKED", "Bloqueada"
        );
        Map<String, String> PRIORITY_ES = Map.of(
            "LOW", "Baja", "MEDIUM", "Media", "HIGH", "Alta", "CRITICAL", "Crítica"
        );
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy");

        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            // Header style
            CellStyle hStyle = wb.createCellStyle();
            Font hFont = wb.createFont();
            hFont.setBold(true);
            hFont.setColor(IndexedColors.WHITE.getIndex());
            hStyle.setFont(hFont);
            hStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            hStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            // Parent row style
            CellStyle parentStyle = wb.createCellStyle();
            Font pFont = wb.createFont();
            pFont.setBold(true);
            parentStyle.setFont(pFont);

            // Done row style
            CellStyle doneStyle = wb.createCellStyle();
            Font dFont = wb.createFont();
            dFont.setStrikeout(true);
            dFont.setColor(IndexedColors.GREY_50_PERCENT.getIndex());
            doneStyle.setFont(dFont);

            Sheet sheet = wb.createSheet("Tareas exportadas");

            String[] headers = {
                "Título", "Tarea padre (título exacto, opcional)", "Descripción",
                "Estado", "Prioridad", "Proyecto",
                "Asignado a (separar con ;)",
                "Fecha inicio (DD/MM/YYYY)", "Fecha vencimiento (DD/MM/YYYY)",
                "% Avance"
            };
            Row hRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell c = hRow.createCell(i);
                c.setCellValue(headers[i]);
                c.setCellStyle(hStyle);
            }

            for (int r = 0; r < ordered.size(); r++) {
                var t = ordered.get(r);
                Row row = sheet.createRow(r + 1);

                boolean isDone   = "DONE".equals(t.status());
                boolean isParent = t.hasChildren();
                CellStyle rowStyle = isDone ? doneStyle : (isParent ? parentStyle : null);

                String parentTitle = t.parentId() != null ? idToTitle.getOrDefault(t.parentId(), "") : "";
                // For parent tasks, omit dates/assignees/progress (they're derived from children)
                String assignees  = !isParent && t.assigneeNames() != null ? String.join("; ", t.assigneeNames()) : "";
                String startDate  = !isParent && t.startDate() != null ? t.startDate().format(fmt) : "";
                String dueDate    = !isParent && t.dueDate()   != null ? t.dueDate().format(fmt)   : "";
                int    progress   = t.progressActual();

                Object[] cols = {
                    t.title(),
                    parentTitle,
                    t.description() != null ? t.description() : "",
                    STATUS_ES.getOrDefault(t.status(), t.status()),
                    PRIORITY_ES.getOrDefault(t.priority(), t.priority()),
                    t.projectName() != null ? t.projectName() : "",
                    assignees,
                    startDate,
                    dueDate,
                    progress
                };

                for (int i = 0; i < cols.length; i++) {
                    Cell c = row.createCell(i);
                    if (cols[i] instanceof Number n) c.setCellValue(n.doubleValue());
                    else c.setCellValue(cols[i].toString());
                    if (rowStyle != null) c.setCellStyle(rowStyle);
                }
            }

            int[] widths = {8000, 8500, 9000, 4500, 4000, 6500, 6500, 7000, 7000, 4000};
            for (int i = 0; i < widths.length; i++) sheet.setColumnWidth(i, widths[i]);

            sheet.createFreezePane(0, 1);

            wb.write(out);
            return out.toByteArray();
        }
    }

    private void walkTree(List<com.tasktracker.dto.TaskDTO> nodes,
                          Map<Long, List<com.tasktracker.dto.TaskDTO>> childrenMap,
                          List<com.tasktracker.dto.TaskDTO> result) {
        for (var node : nodes) {
            result.add(node);
            walkTree(childrenMap.getOrDefault(node.id(), List.of()), childrenMap, result);
        }
    }

    // ── Template ──────────────────────────────────────────────────────────────

    public byte[] generateTemplate() throws IOException {
        List<Project>    projects = projectRepository.findAll();
        List<TeamMember> members  = memberRepository.findAll();

        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            // ── Styles ──
            CellStyle hStyle = wb.createCellStyle();
            Font hFont = wb.createFont();
            hFont.setBold(true);
            hFont.setColor(IndexedColors.WHITE.getIndex());
            hStyle.setFont(hFont);
            hStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            hStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            CellStyle parentStyle = wb.createCellStyle();
            Font parentFont = wb.createFont();
            parentFont.setBold(true);
            parentStyle.setFont(parentFont);

            CellStyle childStyle = wb.createCellStyle();
            childStyle.setIndention((short) 2);

            // ── Sheet 1: Tareas ──
            Sheet data = wb.createSheet("Tareas");
            String[] headers = {
                "Título *",
                "Tarea padre (título exacto, opcional)",
                "Descripción",
                "Estado",
                "Prioridad",
                "Proyecto",
                "Asignado a (separar con ;)",
                "Fecha inicio (DD/MM/YYYY)",
                "Fecha vencimiento (DD/MM/YYYY)",
                "% Avance (0-100)"
            };
            Row hRow = data.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell c = hRow.createCell(i);
                c.setCellValue(headers[i]);
                c.setCellStyle(hStyle);
            }

            // Sample data: 1 parent + 2 children
            String sampleMember  = members.isEmpty()  ? "Nombre Persona" :  members.get(0).getName();
            String sampleMember2 = members.size() > 1 ? members.get(1).getName() : sampleMember;
            String sampleProject = projects.isEmpty() ? "Nombre del Proyecto" : projects.get(0).getName();
            String today      = LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
            String inTwoWeeks = LocalDate.now().plusWeeks(2).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
            String inAMonth   = LocalDate.now().plusMonths(1).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));

            // Row 1: parent task (no tarea padre column)
            Object[] parent = {
                "Módulo de autenticación", "", "Implementación completa del módulo de login",
                "En progreso", "Alta", sampleProject, "", today, inAMonth, 0
            };
            Row r1 = data.createRow(1);
            for (int i = 0; i < parent.length; i++) {
                Cell c = r1.createCell(i);
                if (parent[i] instanceof Number n) c.setCellValue(n.doubleValue());
                else c.setCellValue(parent[i].toString());
                if (i == 0) c.setCellStyle(parentStyle);
            }

            // Row 2: child task
            Object[] child1 = {
                "Diseño de formulario de login", "Módulo de autenticación",
                "Pantalla con validaciones y mensajes de error",
                "Completada", "Alta", sampleProject, sampleMember, today, inTwoWeeks, 100
            };
            Row r2 = data.createRow(2);
            for (int i = 0; i < child1.length; i++) {
                Cell c = r2.createCell(i);
                if (child1[i] instanceof Number n) c.setCellValue(n.doubleValue());
                else c.setCellValue(child1[i].toString());
                if (i == 0) c.setCellStyle(childStyle);
            }

            // Row 3: another child task
            Object[] child2 = {
                "Integración JWT", "Módulo de autenticación",
                "Generación y validación de tokens",
                "Pendiente", "Alta", sampleProject, sampleMember2, inTwoWeeks, inAMonth, 0
            };
            Row r3 = data.createRow(3);
            for (int i = 0; i < child2.length; i++) {
                Cell c = r3.createCell(i);
                if (child2[i] instanceof Number n) c.setCellValue(n.doubleValue());
                else c.setCellValue(child2[i].toString());
                if (i == 0) c.setCellStyle(childStyle);
            }

            int[] widths = {8000, 8500, 9000, 4500, 4000, 6500, 6500, 7000, 7000, 4500};
            for (int i = 0; i < widths.length; i++) data.setColumnWidth(i, widths[i]);

            // ── Sheet 2: Referencias ──
            CellStyle secStyle = wb.createCellStyle();
            Font secFont = wb.createFont();
            secFont.setBold(true);
            secStyle.setFont(secFont);

            Sheet ref = wb.createSheet("Referencias");

            // Note about parent tasks
            Row noteRow = ref.createRow(0);
            Cell noteCell = noteRow.createCell(0);
            noteCell.setCellValue("⚠ IMPORTANTE: las tareas padre deben ir ANTES que sus subtareas en el archivo.");
            CellStyle noteStyle = wb.createCellStyle();
            Font noteFont = wb.createFont();
            noteFont.setItalic(true);
            noteFont.setColor(IndexedColors.DARK_RED.getIndex());
            noteStyle.setFont(noteFont);
            noteCell.setCellStyle(noteStyle);
            ref.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(0, 0, 0, 6));

            // Headers row
            String[] refTitles = {
                "Estado (col. D)", "", "Prioridad (col. E)", "",
                "Proyectos (col. F)", "", "Miembros (col. G)"
            };
            Row refHeader = ref.createRow(1);
            for (int i = 0; i < refTitles.length; i++) {
                if (!refTitles[i].isEmpty()) {
                    Cell c = refHeader.createCell(i);
                    c.setCellValue(refTitles[i]);
                    c.setCellStyle(secStyle);
                }
            }

            String[][] statuses   = {{"Pendiente"}, {"En progreso"}, {"En revisión"}, {"Completada"}, {"Bloqueada"}};
            String[][] priorities = {{"Baja"}, {"Media"}, {"Alta"}, {"Crítica"}};

            int maxRows = Math.max(
                Math.max(statuses.length, priorities.length),
                Math.max(projects.size(), members.size())
            );

            for (int r = 0; r < maxRows; r++) {
                Row row = ref.createRow(r + 2);
                if (r < statuses.length)   row.createCell(0).setCellValue(statuses[r][0]);
                if (r < priorities.length) row.createCell(2).setCellValue(priorities[r][0]);
                if (r < projects.size())   row.createCell(4).setCellValue(projects.get(r).getName());
                if (r < members.size())    row.createCell(6).setCellValue(members.get(r).getName());
            }

            int[] refWidths = {5500, 500, 5000, 500, 7000, 500, 7000};
            for (int i = 0; i < refWidths.length; i++) ref.setColumnWidth(i, refWidths[i]);

            wb.write(out);
            return out.toByteArray();
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private boolean isRowEmpty(Row row) {
        for (int i = 0; i < 10; i++) {
            Cell c = row.getCell(i);
            if (c != null && c.getCellType() != CellType.BLANK && !getCellString(c).isBlank())
                return false;
        }
        return true;
    }

    private String getString(Row row, int col) {
        Cell c = row.getCell(col);
        return c == null ? "" : getCellString(c);
    }

    private String getCellString(Cell c) {
        return switch (c.getCellType()) {
            case STRING  -> c.getStringCellValue().trim();
            case NUMERIC -> DateUtil.isCellDateFormatted(c)
                ? c.getLocalDateTimeCellValue().toLocalDate()
                     .format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                : String.valueOf((long) c.getNumericCellValue());
            case BOOLEAN -> String.valueOf(c.getBooleanCellValue());
            case FORMULA -> switch (c.getCachedFormulaResultType()) {
                case NUMERIC -> String.valueOf((long) c.getNumericCellValue());
                default      -> c.getRichStringCellValue().getString().trim();
            };
            default -> "";
        };
    }

    private int getInt(Row row, int col, int def) {
        Cell c = row.getCell(col);
        if (c == null) return def;
        try {
            if (c.getCellType() == CellType.NUMERIC) return (int) c.getNumericCellValue();
            return Integer.parseInt(getCellString(c));
        } catch (NumberFormatException e) { return def; }
    }

    private Integer getOptionalInt(Row row, int col) {
        Cell c = row.getCell(col);
        if (c == null || c.getCellType() == CellType.BLANK || getCellString(c).isBlank()) return null;
        try {
            if (c.getCellType() == CellType.NUMERIC) return (int) c.getNumericCellValue();
            return Integer.parseInt(getCellString(c));
        } catch (NumberFormatException e) { return null; }
    }

    private String resolveEnum(String raw, Map<String, String> map, String fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        String key = raw.toLowerCase().trim();
        return map.getOrDefault(key, map.getOrDefault(raw.toUpperCase().trim(), fallback));
    }

    private LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank()) return null;
        for (String pattern : List.of("dd/MM/yyyy", "d/M/yyyy", "yyyy-MM-dd")) {
            try { return LocalDate.parse(raw.trim(), DateTimeFormatter.ofPattern(pattern)); }
            catch (DateTimeParseException ignored) {}
        }
        return null;
    }
}
