package com.tasktracker.dto;

import jakarta.validation.constraints.NotBlank;

public record ProjectRequest(
    @NotBlank String name,
    String description,
    String color,
    String status
) {}
