package com.tasktracker.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;
import java.util.List;

public record TaskRequest(
    @NotBlank String title,
    String description,
    String status,
    String priority,
    Long projectId,
    List<Long> assigneeIds,
    LocalDate startDate,
    LocalDate dueDate,
    Integer progressActual,
    Long parentId,
    List<CommentDTO> comments
) {}
