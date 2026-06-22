package com.tasktracker.service;

import com.tasktracker.dto.StatsDTO;
import com.tasktracker.model.Comment;
import com.tasktracker.model.Project;
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StatsService {

    private final TaskRepository taskRepository;
    private final ProjectRepository projectRepository;
    private final TeamMemberRepository memberRepository;

    public StatsDTO getStats() {
        List<Task> tasks = taskRepository.findAll();
        LocalDateTime now = LocalDateTime.now();

        long total = taskRepository.count();
        long todo = taskRepository.countByStatus(TaskStatus.TODO);
        long inProgress = taskRepository.countByStatus(TaskStatus.IN_PROGRESS);
        long inReview = taskRepository.countByStatus(TaskStatus.IN_REVIEW);
        long done = taskRepository.countByStatus(TaskStatus.DONE);
        long blocked = taskRepository.countByStatus(TaskStatus.BLOCKED);
        long stopped = taskRepository.countByStatus(TaskStatus.STOPPED);
        long totalProjects = projectRepository.count();
        long totalMembers = memberRepository.count();
        long overdue = taskRepository.countOverdue(LocalDate.now());

        long stale7 = 0;
        long stale14 = 0;
        long stale30 = 0;
        long blockedAgeSum = 0;
        long blockedAgeMax = 0;

        Map<TaskStatus, AgeBucket> cycleByStatus = new HashMap<>();
        Map<Long, MemberBucket> memberBuckets = new HashMap<>();
        memberRepository.findAll().forEach(member -> memberBuckets.put(member.getId(), new MemberBucket(member.getName())));
        Map<Long, ProjectBucket> projectBuckets = new HashMap<>();

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

        List<StatsDTO.ProjectTaskCount> tasksByProject = projectRepository.findAll()
            .stream().map(p -> new StatsDTO.ProjectTaskCount(
                p.getName(),
                p.getColor(),
                taskRepository.countByProjectId(p.getId()),
                taskRepository.countByProjectIdAndStatus(p.getId(), TaskStatus.DONE)
            )).toList();

        Map<String, Long> tasksByPriority = Map.of(
            "LOW", taskRepository.countByPriority(TaskPriority.LOW),
            "MEDIUM", taskRepository.countByPriority(TaskPriority.MEDIUM),
            "HIGH", taskRepository.countByPriority(TaskPriority.HIGH),
            "CRITICAL", taskRepository.countByPriority(TaskPriority.CRITICAL)
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
