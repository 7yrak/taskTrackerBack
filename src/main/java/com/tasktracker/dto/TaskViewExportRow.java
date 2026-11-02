package com.tasktracker.dto;

import jakarta.validation.constraints.*;

public record TaskViewExportRow(@NotNull @Positive Long id, @Min(0) @Max(100) int level) {}
