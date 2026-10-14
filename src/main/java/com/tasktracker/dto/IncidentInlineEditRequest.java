package com.tasktracker.dto;
import jakarta.validation.Valid;
public record IncidentInlineEditRequest(@Valid IncidentRequest data, @Valid IncidentTransitionRequest transition) {}
