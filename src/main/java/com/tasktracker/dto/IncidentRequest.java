package com.tasktracker.dto;
import com.tasktracker.model.*;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
public record IncidentRequest(
 @NotBlank @Size(max=255) String title, @NotBlank String description,
 @NotNull IncidentEnvironment detectedEnvironment, @NotNull TaskPriority priority,
 @NotBlank @Size(max=255) String reporter, @NotNull Long projectId, Long taskId,
 Long developerId, Long qaId, String reproductionSteps, String expectedResult,
 String actualResult, LocalDate dueDate, @Size(max=255) String reportedStatus, LocalDate qaDeliveryDate, String importNotes) {}
