package com.tasktracker.service;

import com.tasktracker.dto.StatsDTO;
import com.tasktracker.model.Project;
import com.tasktracker.model.TaskPriority;
import com.tasktracker.model.TaskStatus;
import com.tasktracker.repository.ProjectRepository;
import com.tasktracker.repository.TaskRepository;
import com.tasktracker.repository.TeamMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StatsService {

    private final TaskRepository taskRepository;
    private final ProjectRepository projectRepository;
    private final TeamMemberRepository memberRepository;

    public StatsDTO getStats() {
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

        return new StatsDTO(total, todo, inProgress, inReview, done, blocked,stopped,
            totalProjects, totalMembers, overdue, tasksByProject, tasksByPriority);
    }
}
