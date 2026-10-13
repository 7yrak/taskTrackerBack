package com.tasktracker.dto;
import jakarta.validation.constraints.*;
public record IncidentCommentRequest(@NotBlank @Size(max=255) String author, @NotBlank String text) {}
