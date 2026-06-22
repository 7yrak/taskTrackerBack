package com.tasktracker.controller;

import com.tasktracker.dto.StatusUpdateRequest;
import com.tasktracker.dto.TaskDTO;
import com.tasktracker.dto.TaskImportResult;
import com.tasktracker.dto.TaskRequest;
import com.tasktracker.model.TaskPriority;
import com.tasktracker.model.TaskStatus;
import com.tasktracker.service.TaskImportService;
import com.tasktracker.service.TaskService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;
    private final TaskImportService taskImportService;

    @GetMapping
    public List<TaskDTO> getAll(
        @RequestParam(required = false) List<Long>   projectId,
        @RequestParam(required = false) List<String> status,
        @RequestParam(required = false) List<String> priority,
        @RequestParam(required = false) List<Long>   assigneeId
    ) {
        List<TaskStatus> statuses = null;
        if (status != null && !status.isEmpty()) {
            statuses = new ArrayList<>();
            for (String s : status) {
                try {
                    statuses.add(TaskStatus.valueOf(s.trim().toUpperCase()));
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("Invalid status value provided: '" + s + "'. Allowed values are: " + Arrays.toString(TaskStatus.values()));
                }
            }
        }

        List<TaskPriority> priorities = null;
        if (priority != null && !priority.isEmpty()) {
            priorities = new ArrayList<>();
            for (String p : priority) {
                try {
                    priorities.add(TaskPriority.valueOf(p.trim().toUpperCase()));
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("Invalid priority value provided: '" + p + "'. Allowed values are: " + Arrays.toString(TaskPriority.values()));
                }
            }
        }

        return taskService.findAll(projectId, statuses, priorities, assigneeId);
    }

    @GetMapping("/{id}")
    public TaskDTO getById(@PathVariable Long id) {
        return taskService.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TaskDTO create(@Valid @RequestBody TaskRequest req) {
        return taskService.create(req);
    }

    @PutMapping("/{id}")
    public TaskDTO update(@PathVariable Long id, @Valid @RequestBody TaskRequest req) {
        return taskService.update(id, req);
    }

    @PatchMapping("/{id}/status")
    public TaskDTO updateStatus(@PathVariable Long id, @Valid @RequestBody StatusUpdateRequest req) {
        TaskStatus taskStatus;
        try {
            taskStatus = TaskStatus.valueOf(req.status().trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Estado de tarea no válido: '" + req.status() + "'. Los valores permitidos son: " + Arrays.toString(TaskStatus.values()));
        }
        return taskService.updateStatus(id, taskStatus);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        taskService.delete(id);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAll() {
        taskService.deleteAll();
    }

    @PostMapping("/import")
    public ResponseEntity<TaskImportResult> importExcel(
            @RequestParam("file") MultipartFile file) throws IOException {
        return ResponseEntity.ok(taskImportService.importFromExcel(file));
    }

    @GetMapping("/import/template")
    public ResponseEntity<byte[]> downloadTemplate() throws IOException {
        byte[] bytes = taskImportService.generateTemplate();
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=\"plantilla_tareas.csv\"")
            .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
            .body(bytes);
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportExcel() throws IOException {
        byte[] bytes = taskImportService.exportToCsv();
        String filename = "tareas_" + java.time.LocalDate.now() + ".csv";
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=\"" + filename + "\"")
            .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
            .body(bytes);
    }
}
