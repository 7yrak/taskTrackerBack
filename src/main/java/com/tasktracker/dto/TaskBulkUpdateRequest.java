package com.tasktracker.dto;

import com.tasktracker.model.TaskPriority;
import com.tasktracker.model.TaskStatus;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.List;

public record TaskBulkUpdateRequest(
    @NotEmpty @Size(max = 500) List<@NotNull @Positive Long> taskIds,
    TaskStatus status, TaskPriority priority,
    boolean updateAssignees, @Size(max = 500) List<@NotNull @Positive Long> assigneeIds,
    boolean updateStartDate, LocalDate startDate,
    boolean updateDueDate, LocalDate dueDate
) {}
