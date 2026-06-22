package com.tasktracker.service;

import com.tasktracker.dto.ProjectDTO;
import com.tasktracker.dto.ProjectRequest;
import com.tasktracker.model.Project;
import com.tasktracker.model.Task;
import com.tasktracker.model.TaskStatus;
import com.tasktracker.model.ProjectStatus;
import com.tasktracker.model.TeamMember;
import com.tasktracker.repository.ProjectRepository;
import com.tasktracker.repository.TaskRepository;
import com.tasktracker.repository.TeamMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
@Transactional
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final TeamMemberRepository memberRepository;
    private final TaskRepository taskRepository;

    @Transactional(readOnly = true)
    public List<ProjectDTO> findAll() {
        return projectRepository.findAll().stream().map(this::toDTO).toList();
    }

    @Transactional(readOnly = true)
    public ProjectDTO findById(Long id) {
        return projectRepository.findById(id).map(this::toDTO)
            .orElseThrow(() -> new NoSuchElementException("Proyecto no encontrado: " + id));
    }

    public ProjectDTO create(ProjectRequest req) {
        Project project = Project.builder()
            .name(req.name())
            .description(req.description())
            .color(req.color())
            .status(parseStatus(req.status()))
            .build();
        return toDTO(projectRepository.save(project));
    }

    public ProjectDTO update(Long id, ProjectRequest req) {
        Project project = projectRepository.findById(id)
            .orElseThrow(() -> new NoSuchElementException("Proyecto no encontrado: " + id));
        project.setName(req.name());
        project.setDescription(req.description());
        if (req.color() != null) project.setColor(req.color());
        if (req.status() != null) project.setStatus(parseStatus(req.status()));
        return toDTO(projectRepository.save(project));
    }

    public void delete(Long id) {
        if (!projectRepository.existsById(id))
            throw new NoSuchElementException("Proyecto no encontrado: " + id);
        projectRepository.deleteById(id);
    }

    public ProjectDTO addMember(Long projectId, Long memberId) {
        Project project = projectRepository.findById(projectId)
            .orElseThrow(() -> new NoSuchElementException("Proyecto no encontrado: " + projectId));
        TeamMember member = memberRepository.findById(memberId)
            .orElseThrow(() -> new NoSuchElementException("Miembro no encontrado: " + memberId));
        project.getMembers().add(member);
        return toDTO(projectRepository.save(project));
    }

    public ProjectDTO removeMember(Long projectId, Long memberId) {
        Project project = projectRepository.findById(projectId)
            .orElseThrow(() -> new NoSuchElementException("Proyecto no encontrado: " + projectId));
        project.getMembers().removeIf(m -> m.getId().equals(memberId));
        return toDTO(projectRepository.save(project));
    }

    private ProjectDTO toDTO(Project p) {
        long total = taskRepository.countByProjectId(p.getId());
        long done = taskRepository.countByProjectIdAndStatus(p.getId(), TaskStatus.DONE);
        long inProgress = taskRepository.countByProjectIdAndStatus(p.getId(), TaskStatus.IN_PROGRESS);
        
        // Calcular progreso real y esperado
        Integer progressActual = null;
        Integer progressExpected = null;
        
        if (total > 0) {
            List<Task> tasks = taskRepository.findByProjectId(p.getId());

            // Solo usamos tareas hoja (sin subtareas) para el promedio, igual que el dashboard
            java.util.Set<Long> parentIds = new java.util.HashSet<>(taskRepository.findParentIdsByProjectId(p.getId()));
            List<Task> leafTasks = tasks.stream()
                .filter(t -> !parentIds.contains(t.getId()))
                .toList();

            if (!leafTasks.isEmpty()) {
                double sumActual = leafTasks.stream()
                    .mapToInt(task -> task.getProgressActual() != null ? task.getProgressActual() : 0)
                    .sum();
                progressActual = (int) Math.round(sumActual / leafTasks.size());

                double sumExpected = leafTasks.stream()
                    .mapToInt(task -> computeProgressExpected(task))
                    .sum();
                progressExpected = (int) Math.round(sumExpected / leafTasks.size());
            } else {
                progressActual = 0;
                progressExpected = 0;
            }
        }
        
        return new ProjectDTO(
            p.getId(), p.getName(), p.getDescription(), p.getColor(),
            p.getStatus() != null ? p.getStatus().name() : null,
            p.getCreatedAt(),
            total, done, inProgress, p.getMembers().size(), progressActual, progressExpected
        );
    }

    private Integer computeProgressExpected(Task t) {
        LocalDate dueDate = t.getDueDate();
        if (dueDate == null) return 0;

        LocalDate startDate = t.getStartDate() != null ? t.getStartDate()
                            : (t.getCreatedAt() != null ? t.getCreatedAt().toLocalDate() : null);
        if (startDate == null) return 0;

        LocalDate today = LocalDate.now();
        long totalDays = ChronoUnit.DAYS.between(startDate, dueDate);

        if (totalDays == 0) return !today.isBefore(dueDate) ? 100 : 0;
        if (totalDays < 0)  return 100;

        long elapsed = ChronoUnit.DAYS.between(startDate, today);
        return Math.max(0, Math.min(100, (int) Math.round((double) elapsed / totalDays * 100.0)));
    }

    private ProjectStatus parseStatus(String rawStatus) {
        if (rawStatus == null || rawStatus.isBlank()) {
            return ProjectStatus.INITIATED;
        }
        try {
            return ProjectStatus.valueOf(rawStatus.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Estado de proyecto no válido: '" + rawStatus + "'");
        }
    }
}
