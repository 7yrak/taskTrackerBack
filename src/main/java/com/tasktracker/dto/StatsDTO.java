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
    long stoppedTasks,
    long totalProjects,
    long totalMembers,
    long overdueTasks,
    long staleTasks7Days,
    long staleTasks14Days,
    long staleTasks30Days,
    long blockedAverageAgeDays,
    long blockedMaxAgeDays,
    List<ProjectTaskCount> tasksByProject,
    Map<String, Long> tasksByPriority,
    List<StatusCycleCount> cycleTimeByStatus,
    List<MemberLoadCount> memberLoad,
    List<ProjectSlaCount> slaByProject,
    List<MemberSlaCount> slaByMember
) {
    public record ProjectTaskCount(String projectName, String color, long count, long doneCount) {}
    public record StatusCycleCount(String status, long averageDays, long taskCount) {}
    public record MemberLoadCount(String memberName, long taskCount, long blockedTasks, long overdueTasks, long averageAgeDays) {}
    public record ProjectSlaCount(String projectName, String color, long completedTasks, long onTimeTasks, double slaPercent) {}
    public record MemberSlaCount(String memberName, long completedTasks, long onTimeTasks, double slaPercent) {}
}
