package com.tasktracker.controller;

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
import java.util.List;
import java.util.Map;

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
        List<TaskStatus>   statuses   = status   != null ? status.stream().map(TaskStatus::valueOf).toList()   : null;
        List<TaskPriority> priorities = priority != null ? priority.stream().map(TaskPriority::valueOf).toList() : null;
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
    public TaskDTO updateStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return taskService.updateStatus(id, body.get("status"));
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
                    "attachment; filename=\"plantilla_tareas.xlsx\"")
            .contentType(MediaType.parseMediaType(
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .body(bytes);
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportExcel() throws IOException {
        byte[] bytes = taskImportService.exportToExcel();
        String filename = "tareas_" + java.time.LocalDate.now() + ".xlsx";
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=\"" + filename + "\"")
            .contentType(MediaType.parseMediaType(
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .body(bytes);
    }
}
