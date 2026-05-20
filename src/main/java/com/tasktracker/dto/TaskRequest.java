package com.tasktracker.dto;

import com.tasktracker.model.TaskPriority;
import com.tasktracker.model.TaskStatus;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;
import java.util.List;

public record TaskRequest(
    @NotBlank String title,
    String description,
    TaskStatus status, // Cambiado de String a TaskStatus
    TaskPriority priority, // Cambiado de String a TaskPriority
    Long projectId,
    List<Long> assigneeIds,
    LocalDate startDate,
    LocalDate dueDate,
    Integer progressActual,
    Long parentId,
    List<CommentDTO> comments
) {}