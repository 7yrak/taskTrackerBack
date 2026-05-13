package com.tasktracker.dto;

import java.util.List;
import java.util.Map;

public record StatsDTO(
    long totalTasks,
    long todoTasks,
    long inProgressTasks,
    long inReviewTasks,
    long doneTasks,
    long blockedTasks,
    long totalProjects,
    long totalMembers,
    long overdueTasks,
    List<ProjectTaskCount> tasksByProject,
    Map<String, Long> tasksByPriority
) {
    public record ProjectTaskCount(String projectName, String color, long count, long doneCount) {}
}
