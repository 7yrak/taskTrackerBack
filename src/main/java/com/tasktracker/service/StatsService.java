package com.tasktracker.service;

import com.tasktracker.dto.StatsDTO;
import com.tasktracker.model.Comment;
import com.tasktracker.model.Project;
import com.tasktracker.model.ProjectStatus;
import com.tasktracker.model.Task;
import com.tasktracker.model.TaskPriority;
import com.tasktracker.model.TaskStatus;
import com.tasktracker.model.TeamMember;
import com.tasktracker.repository.ProjectRepository;
import com.tasktracker.repository.TaskRepository;
import com.tasktracker.repository.TeamMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Comparator;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StatsService {

    private final TaskRepository taskRepository;
    private final ProjectRepository projectRepository;
    private final TeamMemberRepository memberRepository;

    public StatsDTO getStats() {
        Map<Long, ProjectStatus> projectStatusMap = projectRepository.findAll().stream()
            .collect(Collectors.toMap(Project::getId, p -> p.getStatus() != null ? p.getStatus() : ProjectStatus.INITIATED));

        List<Task> tasks = taskRepository.findAll().stream()
            .filter(task -> isOperationalTask(task, projectStatusMap))
            .toList();
        LocalDateTime now = LocalDateTime.now();

        long total = tasks.size();
        long todo = tasks.stream().filter(t -> t.getStatus() == TaskStatus.TODO).count();
        long inProgress = tasks.stream().filter(t -> t.getStatus() == TaskStatus.IN_PROGRESS).count();
        long inReview = tasks.stream().filter(t -> t.getStatus() == TaskStatus.IN_REVIEW).count();
        long done = tasks.stream().filter(t -> t.getStatus() == TaskStatus.DONE).count();
        long blocked = tasks.stream().filter(t -> t.getStatus() == TaskStatus.BLOCKED).count();
        long stopped = 0;
        long totalProjects = projectRepository.count();
        long totalMembers = memberRepository.count();
        long overdue = tasks.stream().filter(task -> isOverdue(task)).count();

        long stale7 = 0;
        long stale14 = 0;
        long stale30 = 0;
        long blockedAgeSum = 0;
        long blockedAgeMax = 0;

        Map<TaskStatus, AgeBucket> cycleByStatus = new EnumMap<>(TaskStatus.class);
        Map<Long, MemberBucket> memberBuckets = new HashMap<>();
        memberRepository.findAll().forEach(member -> memberBuckets.put(member.getId(), new MemberBucket(member.getName())));
        Map<Long, ProjectBucket> projectBuckets = new HashMap<>();
        Map<TaskPriority, Long> priorityBuckets = new EnumMap<>(TaskPriority.class);
        for (TaskPriority priority : TaskPriority.values()) {
            priorityBuckets.put(priority, 0L);
        }

        for (Task task : tasks) {
            long ageDays = ageInDays(task.getUpdatedAt(), now);
            long cycleDays = cycleDays(task, now);

            if (task.getStatus() != TaskStatus.DONE) {
                if (ageDays >= 7) stale7++;
                if (ageDays >= 14) stale14++;
                if (ageDays >= 30) stale30++;
            }

            cycleByStatus.computeIfAbsent(task.getStatus(), s -> new AgeBucket())
                .add(cycleDays);

            if (task.getStatus() == TaskStatus.BLOCKED) {
                blockedAgeSum += ageDays;
                blockedAgeMax = Math.max(blockedAgeMax, ageDays);
            }

            priorityBuckets.put(task.getPriority(), priorityBuckets.getOrDefault(task.getPriority(), 0L) + 1);

            if (task.getProject() != null) {
                Project project = task.getProject();
                ProjectBucket bucket = projectBuckets.computeIfAbsent(project.getId(),
                    id -> new ProjectBucket(project.getName(), project.getColor()));
                bucket.total++;
                if (task.getStatus() == TaskStatus.DONE) {
                    bucket.completed++;
                    if (isOnTime(task)) {
                        bucket.onTime++;
                    }
                }
            }

            List<TeamMember> assignees = task.getAssignees();
            if (assignees != null) {
                for (TeamMember member : assignees) {
                    MemberBucket bucket = memberBuckets.computeIfAbsent(member.getId(),
                        id -> new MemberBucket(member.getName()));
                    bucket.total++;
                    bucket.sumAge += ageDays;
                    if (task.getStatus() == TaskStatus.BLOCKED) {
                        bucket.blocked++;
                    }
                    if (task.getStatus() == TaskStatus.DONE && isOnTime(task)) {
                        bucket.onTime++;
                    }
                    if (task.getStatus() == TaskStatus.DONE) {
                        bucket.completed++;
                    }
                    if (task.getStatus() == TaskStatus.BLOCKED || ageDays >= 14) {
                        bucket.overdue++;
                    }
                }
            }
        }

        List<StatsDTO.ProjectTaskCount> tasksByProject = projectRepository.findAll().stream()
            .map(p -> {
                ProjectBucket bucket = projectBuckets.get(p.getId());
                long count = bucket != null ? bucket.total : 0;
                long doneCount = bucket != null ? bucket.completed : 0;
                return new StatsDTO.ProjectTaskCount(p.getName(), p.getColor(), count, doneCount);
            })
            .toList();

        Map<String, Long> tasksByPriority = Map.of(
            "LOW", priorityBuckets.getOrDefault(TaskPriority.LOW, 0L),
            "MEDIUM", priorityBuckets.getOrDefault(TaskPriority.MEDIUM, 0L),
            "HIGH", priorityBuckets.getOrDefault(TaskPriority.HIGH, 0L),
            "CRITICAL", priorityBuckets.getOrDefault(TaskPriority.CRITICAL, 0L)
        );

        List<StatsDTO.StatusCycleCount> cycleTimeByStatus = cycleByStatus.entrySet().stream()
            .map(entry -> new StatsDTO.StatusCycleCount(
                entry.getKey().name(),
                entry.getValue().average(),
                entry.getValue().count
            ))
            .sorted(Comparator.comparingLong(StatsDTO.StatusCycleCount::averageDays).reversed())
            .toList();

        List<StatsDTO.MemberLoadCount> memberLoad = memberBuckets.values().stream()
            .map(bucket -> new StatsDTO.MemberLoadCount(
                bucket.memberName,
                bucket.total,
                bucket.blocked,
                bucket.overdue,
                bucket.total > 0 ? Math.round((double) bucket.sumAge / bucket.total) : 0
            ))
            .sorted(Comparator.comparingLong(StatsDTO.MemberLoadCount::taskCount).reversed())
            .toList();

        List<StatsDTO.ProjectSlaCount> slaByProject = projectBuckets.values().stream()
            .map(bucket -> new StatsDTO.ProjectSlaCount(
                bucket.projectName,
                bucket.color,
                bucket.completed,
                bucket.onTime,
                bucket.completed > 0 ? Math.round((bucket.onTime * 1000.0 / bucket.completed)) / 10.0 : 0.0
            ))
            .sorted(Comparator.comparingDouble(StatsDTO.ProjectSlaCount::slaPercent).reversed())
            .toList();

        List<StatsDTO.MemberSlaCount> slaByMember = memberBuckets.values().stream()
            .map(bucket -> new StatsDTO.MemberSlaCount(
                bucket.memberName,
                bucket.completed,
                bucket.onTime,
                bucket.completed > 0 ? Math.round((bucket.onTime * 1000.0 / bucket.completed)) / 10.0 : 0.0
            ))
            .sorted(Comparator.comparingDouble(StatsDTO.MemberSlaCount::slaPercent).reversed())
            .toList();

        return new StatsDTO(total, todo, inProgress, inReview, done, blocked,stopped,
            totalProjects, totalMembers, overdue, stale7, stale14, stale30,
            blockedAgeSum > 0 && blocked > 0 ? Math.round((double) blockedAgeSum / blocked) : 0,
            blockedAgeMax,
            tasksByProject, tasksByPriority, cycleTimeByStatus, memberLoad, slaByProject, slaByMember);
    }

    private boolean isOperationalTask(Task task, Map<Long, ProjectStatus> projectStatusMap) {
        if (task.getStatus() == TaskStatus.STOPPED) {
            return false;
        }

        if (task.getProject() == null || task.getProject().getId() == null) {
            return true;
        }

        ProjectStatus status = projectStatusMap.get(task.getProject().getId());
        return status == null || (status != ProjectStatus.ON_HOLD && status != ProjectStatus.COMPLETED && status != ProjectStatus.CLOSED);
    }

    private boolean isOverdue(Task task) {
        return task.getDueDate() != null
            && task.getStatus() != TaskStatus.DONE
            && task.getStatus() != TaskStatus.STOPPED
            && task.getDueDate().isBefore(LocalDate.now());
    }

    private long ageInDays(LocalDateTime timestamp, LocalDateTime now) {
        if (timestamp == null) {
            return 0;
        }
        return Math.max(0, Duration.between(timestamp, now).toDays());
    }

    private long cycleDays(Task task, LocalDateTime now) {
        LocalDateTime start = task.getCreatedAt();
        LocalDateTime end = task.getStatus() == TaskStatus.DONE && task.getUpdatedAt() != null
            ? task.getUpdatedAt()
            : now;
        if (start == null || end == null) {
            return 0;
        }
        return Math.max(0, Duration.between(start, end).toDays());
    }

    private boolean isOnTime(Task task) {
        if (task.getDueDate() == null || task.getUpdatedAt() == null) {
            return false;
        }
        return !task.getUpdatedAt().toLocalDate().isAfter(task.getDueDate());
    }

    private static class AgeBucket {
        private long count;
        private long sum;

        void add(long days) {
            count++;
            sum += days;
        }

        long average() {
            return count > 0 ? Math.round((double) sum / count) : 0;
        }
    }

    private static class MemberBucket {
        private final String memberName;
        private long total;
        private long blocked;
        private long overdue;
        private long completed;
        private long onTime;
        private long sumAge;

        private MemberBucket(String memberName) {
            this.memberName = memberName;
        }
    }

    private static class ProjectBucket {
        private final String projectName;
        private final String color;
        private long total;
        private long completed;
        private long onTime;

        private ProjectBucket(String projectName, String color) {
            this.projectName = projectName;
            this.color = color;
        }
    }
}
