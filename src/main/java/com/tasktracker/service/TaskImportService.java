package com.tasktracker.service;

import com.tasktracker.dto.TaskImportResult;
import com.tasktracker.dto.TaskRequest;
import com.tasktracker.model.Project;
import com.tasktracker.model.TeamMember;
import com.tasktracker.model.TaskPriority; // Importar TaskPriority
import com.tasktracker.model.TaskStatus;   // Importar TaskStatus
import com.tasktracker.repository.ProjectRepository;
import com.tasktracker.repository.TaskRepository;
import com.tasktracker.repository.TeamMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

@Service
@RequiredArgsConstructor
public class TaskImportService {

    private final TaskService taskService;
    private final TaskRepository taskRepository;
    private final ProjectRepository projectRepository;
    private final TeamMemberRepository memberRepository;

    public TaskImportResult importFromExcel(MultipartFile file) throws IOException {
        // This method now imports a CSV file with '|' as a separator, not an Excel file.
        // The name is kept to avoid breaking the controller signature.
        return importFromCsv(file);
    }

    private TaskImportResult importFromCsv(MultipartFile file) throws IOException {
        List<String> warnings = new ArrayList<>();
        int imported = 0;
        int skipped = 0;

        // Caches for resolving names/emails to IDs
        Map<String, Project> projectCache = new HashMap<>();
        projectRepository.findAll().forEach(p -> projectCache.put(p.getName().toLowerCase(), p));

        Map<String, TeamMember> memberCache = new HashMap<>();
        memberRepository.findAll().forEach(m -> {
            if (m.getEmail() != null) {
                memberCache.put(m.getEmail().toLowerCase(), m);
            }
        });

        // This map will store the mapping from the OLD ID (from the file) to the NEW Task entity created in the DB.
        Map<Long, com.tasktracker.model.Task> oldIdToNewTask = new HashMap<>();

        // We read all rows first to handle parent-child relationships correctly.
        List<Map<String, String>> rows = new ArrayList<>();
        List<String> headers;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            // Skip BOM if present
            reader.mark(1);
            if (reader.read() != 0xFEFF) {
                reader.reset();
            }

            String headerLine = reader.readLine();
            if (headerLine == null || headerLine.trim().isEmpty()) {
                throw new IOException("El archivo CSV está vacío o no tiene cabecera.");
            }
            headers = Arrays.asList(headerLine.split("\\|"));

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                List<String> values = parseCsvLine(line, '|');
                Map<String, String> rowMap = new HashMap<>();
                for (int i = 0; i < headers.size() && i < values.size(); i++) {
                    rowMap.put(headers.get(i), values.get(i));
                }
                rows.add(rowMap);
            }
        }

        // First pass: create all tasks without setting the parent.
        // NOTE: This approach creates NEW tasks and does not preserve IDs from the file.
        // It's a structural restore, not a literal one.
        for (int i = 0; i < rows.size(); i++) {
            Map<String, String> row = rows.get(i);
            int rowNum = i + 2; // 1-based index, +1 for header

            try {
                String title = row.getOrDefault("Title", "").trim();
                if (title.isBlank()) {
                    warnings.add("Fila " + rowNum + ": título vacío, se omitió.");
                    skipped++;
                    continue;
                }

                Long oldId = getLong(row.get("ID"));
                if (oldId == null) {
                    warnings.add("Fila " + rowNum + ": ID no válido o faltante, se omitió.");
                    skipped++;
                    continue;
                }

                String description = row.getOrDefault("Description", "");
                String statusStr = row.getOrDefault("Status", "TODO"); // Renombrado para evitar conflicto
                String priorityStr = row.getOrDefault("Priority", "MEDIUM"); // Renombrado para evitar conflicto
                String projectName = row.getOrDefault("Project", "");
                String assigneesRaw = row.getOrDefault("Assignees (emails)", "");
                String startDateRaw = row.getOrDefault("Start_Date", "");
                String dueDateRaw = row.getOrDefault("Due_Date", "");
                Integer progress = getInteger(row.getOrDefault("Progress_Actual", "0"));

                Long projectId = null;
                if (!projectName.isBlank()) {
                    Project p = projectCache.get(projectName.toLowerCase());
                    if (p != null) {
                        projectId = p.getId();
                    } else {
                        warnings.add("Fila " + rowNum + " (Tarea '" + title + "'): proyecto '" + projectName + "' no encontrado, se ignoró.");
                    }
                }

                List<Long> assigneeIds = new ArrayList<>();
                if (!assigneesRaw.isBlank()) {
                    for (String email : assigneesRaw.split(";")) {
                        String trimmedEmail = email.trim().toLowerCase();
                        if (trimmedEmail.isEmpty()) continue;
                        TeamMember member = memberCache.get(trimmedEmail);
                        if (member != null) {
                            assigneeIds.add(member.getId());
                        } else {
                            warnings.add("Fila " + rowNum + " (Tarea '" + title + "'): miembro con email '" + email.trim() + "' no encontrado, se ignoró.");
                        }
                    }
                }

                // Convertir String a Enum con manejo de errores
                TaskStatus taskStatus = null;
                try {
                    taskStatus = TaskStatus.valueOf(statusStr.trim().toUpperCase());
                } catch (IllegalArgumentException e) {
                    warnings.add("Fila " + rowNum + " (Tarea '" + title + "'): estado '" + statusStr + "' no válido, se usará TODO.");
                    taskStatus = TaskStatus.TODO;
                }

                TaskPriority taskPriority = null;
                try {
                    taskPriority = TaskPriority.valueOf(priorityStr.trim().toUpperCase());
                } catch (IllegalArgumentException e) {
                    warnings.add("Fila " + rowNum + " (Tarea '" + title + "'): prioridad '" + priorityStr + "' no válida, se usará MEDIUM.");
                    taskPriority = TaskPriority.MEDIUM;
                }


                TaskRequest req = new TaskRequest(
                        title,
                        description.isBlank() ? null : description,
                        taskStatus, // Ahora es TaskStatus
                        taskPriority, // Ahora es TaskPriority
                        projectId,
                        assigneeIds.isEmpty() ? null : assigneeIds,
                        parseDate(startDateRaw),
                        parseDate(dueDateRaw),
                        progress,
                        null, // Parent will be set in the second pass
                        null
                );

                com.tasktracker.model.Task newTask = taskService.createAndReturnTask(req);
                oldIdToNewTask.put(oldId, newTask);
                imported++;

            } catch (Exception e) {
                warnings.add("Fila " + rowNum + ": error — " + e.getMessage());
                skipped++;
            }
        }

        // Second pass: set parent relationships
        for (Map<String, String> row : rows) {
            Long oldId = getLong(row.get("ID"));
            Long oldParentId = getLong(row.get("Parent_ID"));

            if (oldId == null || oldParentId == null) {
                continue;
            }

            com.tasktracker.model.Task childTask = oldIdToNewTask.get(oldId);
            com.tasktracker.model.Task parentTask = oldIdToNewTask.get(oldParentId);

            if (childTask != null && parentTask != null) {
                childTask.setParent(parentTask);
                taskRepository.save(childTask);
            } else if (childTask != null) {
                warnings.add("Tarea '" + childTask.getTitle() + "': no se pudo encontrar la tarea padre con el ID original " + oldParentId + " en el archivo importado.");
            }
        }

        return new TaskImportResult(imported, skipped, warnings);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private List<String> parseCsvLine(String line, char separator) {
        List<String> result = new ArrayList<>();
        if (line == null || line.isEmpty()) {
            return result;
        }
        StringBuilder curVal = new StringBuilder();
        boolean inQuotes = false;
        char[] chars = line.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            char ch = chars[i];
            if (inQuotes) {
                if (ch == '"') {
                    if (i + 1 < chars.length && chars[i + 1] == '"') {
                        curVal.append('"');
                        i++; // Skip next quote
                    } else {
                        inQuotes = false;
                    }
                } else {
                    curVal.append(ch);
                }
            } else {
                if (ch == '"') {
                    inQuotes = true;
                } else if (ch == separator) {
                    result.add(curVal.toString());
                    curVal = new StringBuilder();
                } else {
                    curVal.append(ch);
                }
            }
        }
        result.add(curVal.toString());
        return result;
    }

    private Long getLong(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer getInteger(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank()) return null;
        // The frontend now exports as yyyy-MM-dd
        for (String pattern : List.of("yyyy-MM-dd", "dd/MM/yyyy", "d/M/yyyy")) {
            try {
                return LocalDate.parse(raw.trim(), DateTimeFormatter.ofPattern(pattern));
            } catch (DateTimeParseException ignored) {
            }
        }
        return null;
    }

    public byte[] generateTemplate() {
        String template = "ID|Parent_ID|Title|Description|Project|Assignees (emails)|Status|Priority|Start_Date|Due_Date|Progress_Actual\n";
        return template.getBytes(StandardCharsets.UTF_8);
    }

    public byte[] exportToCsv() {
        String data = "ID|Parent_ID|Title|Description|Project|Assignees (emails)|Status|Priority|Start_Date|Due_Date|Progress_Actual\n";
        return data.getBytes(StandardCharsets.UTF_8);
    }
}
