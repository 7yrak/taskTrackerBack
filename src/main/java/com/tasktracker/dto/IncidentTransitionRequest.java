package com.tasktracker.dto;
import jakarta.validation.constraints.*;
public record IncidentTransitionRequest(@NotBlank String action, @Size(max=255) String actor,
 String observation, String evidenceUrl) {}
