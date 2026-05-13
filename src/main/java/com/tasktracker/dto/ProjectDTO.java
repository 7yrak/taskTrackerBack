package com.tasktracker.dto;

import java.time.LocalDateTime;

public record ProjectDTO(
    Long id,
    String name,
    String description,
    String color,
    LocalDateTime createdAt,
    long totalTasks,
    long doneTasks,
    long inProgressTasks,
    long memberCount,
    Integer progressActual,
    Integer progressExpected
) {}
