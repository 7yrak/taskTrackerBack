package com.tasktracker.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record TaskDTO(
    Long id,
    String title,
    String description,
    String status,
    String priority,
    Long projectId,
    String projectName,
    String projectColor,
    List<Long> assigneeIds,
    List<String> assigneeNames,
    LocalDate startDate,
    LocalDate dueDate,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    int progressActual,
    Integer progressExpected,
    Long parentId,
    boolean hasChildren,
    List<CommentDTO> comments
) {}
