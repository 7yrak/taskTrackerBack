package com.tasktracker.dto;

import jakarta.validation.constraints.NotBlank;

public record StatusUpdateRequest(
    @NotBlank(message = "El estado no puede estar vacío")
    String status
) {}