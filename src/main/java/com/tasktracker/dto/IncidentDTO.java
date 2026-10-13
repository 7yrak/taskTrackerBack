package com.tasktracker.dto;
import com.tasktracker.model.*;
import java.time.*;
import java.util.List;
public record IncidentDTO(Long id, String title, String description, IncidentEnvironment detectedEnvironment,
 TaskPriority priority, IncidentStatus status, String reporter, Long projectId, String projectName,
 Long taskId, String taskTitle, Long developerId, String developerName, Long qaId, String qaName,
 String reproductionSteps, String expectedResult, String actualResult, LocalDate dueDate,
 LocalDateTime createdAt, LocalDateTime updatedAt, LocalDateTime closedAt,
 List<Event> events, List<Attachment> attachments) {
 public record Event(Long id, String type, IncidentStatus fromStatus, IncidentStatus toStatus,
 String actor, String observation, String evidenceUrl, LocalDateTime createdAt) {}
 public record Attachment(Long id, String category, String filename, String contentType, long size, LocalDateTime createdAt) {}
}
