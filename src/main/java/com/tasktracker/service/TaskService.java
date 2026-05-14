package com.tasktracker.service;

import com.tasktracker.dto.TaskDTO;
import com.tasktracker.dto.TaskRequest;
import com.tasktracker.dto.CommentDTO;
import com.tasktracker.model.*;
import com.tasktracker.repository.TaskCommentRepository;
import com.tasktracker.repository.ProjectRepository;
import com.tasktracker.repository.TaskRepository;
import com.tasktracker.repository.TeamMemberRepository;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;


@Service
@RequiredArgsConstructor
@Transactional
public class TaskService {

    private final TaskRepository taskRepository;
    private final ProjectRepository projectRepository;
    private final TeamMemberRepository memberRepository;
    private final TaskCommentRepository taskCommentRepository;

    @Transactional(readOnly = true)
    public List<TaskDTO> findAll(List<Long> projectIds, List<TaskStatus> statuses,
                                  List<TaskPriority> priorities, List<Long> assigneeIds) {
        Specification<Task> spec = (root, query, cb) -> {
            if (query != null) query.distinct(true);
            List<Predicate> predicates = new ArrayList<>();
            if (projectIds   != null && !projectIds.isEmpty())
                predicates.add(root.get("project").get("id").in(projectIds));
            if (statuses     != null && !statuses.isEmpty())
                predicates.add(root.get("status").in(statuses));
            if (priorities   != null && !priorities.isEmpty())
                predicates.add(root.get("priority").in(priorities));
            if (assigneeIds  != null && !assigneeIds.isEmpty())
                predicates.add(root.join("assignees", JoinType.LEFT).get("id").in(assigneeIds));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return taskRepository.findAll(spec).stream().map(this::toDTO).toList();
    }

    @Transactional(readOnly = true)
    public TaskDTO findById(Long id) {
        return taskRepository.findById(id).map(this::toDTO)
            .orElseThrow(() -> new NoSuchElementException("Tarea no encontrada: " + id));
    }

    public TaskDTO create(TaskRequest req) {
        Task task = buildTask(new Task(), req);
        return toDTO(taskRepository.save(task));
    }

    public TaskDTO update(Long id, TaskRequest req) {
        Task task = taskRepository.findById(id)
            .orElseThrow(() -> new NoSuchElementException("Tarea no encontrada: " + id));
        return toDTO(taskRepository.save(buildTask(task, req)));
    }

    public TaskDTO updateStatus(Long id, String status) {
        Task task = taskRepository.findById(id)
            .orElseThrow(() -> new NoSuchElementException("Tarea no encontrada: " + id));
        task.setStatus(TaskStatus.valueOf(status));
        return toDTO(taskRepository.save(task));
    }

    // This is a helper for the import service to get the managed entity
    public Task createAndReturnTask(TaskRequest req) {
        Task task = buildTask(new Task(), req);
        return taskRepository.save(task);
    }

    public void delete(Long id) {
        if (!taskRepository.existsById(id))
            throw new NoSuchElementException("Tarea no encontrada: " + id);
        taskRepository.detachFromParent(id);
        taskRepository.deleteById(id);
    }

    public void deleteAll() {
        // Borrar dependencias primero para evitar errores de restricción de clave foránea
        taskCommentRepository.deleteAllInBatch();
        taskRepository.deleteAllAssignees();
        taskRepository.clearAllParentReferences();
        taskRepository.deleteAllInBatch();
    }

    private Task buildTask(Task task, TaskRequest req) {
        task.setTitle(req.title());
        task.setDescription(req.description());

        if (req.status() != null) task.setStatus(TaskStatus.valueOf(req.status()));
        else if (task.getStatus() == null) task.setStatus(TaskStatus.TODO);

        if (req.priority() != null) task.setPriority(TaskPriority.valueOf(req.priority()));
        else if (task.getPriority() == null) task.setPriority(TaskPriority.MEDIUM);

        if (req.projectId() != null) {
            task.setProject(projectRepository.findById(req.projectId())
                .orElseThrow(() -> new NoSuchElementException("Proyecto no encontrado: " + req.projectId())));
        } else {
            task.setProject(null);
        }

        if (req.assigneeIds() != null && !req.assigneeIds().isEmpty()) {
            task.setAssignees(memberRepository.findAllById(req.assigneeIds()));
        } else {
            task.setAssignees(new ArrayList<>());
        }

        task.setStartDate(req.startDate());
        task.setDueDate(req.dueDate());

        if (req.progressActual() != null) {
            task.setProgressActual(Math.max(0, Math.min(100, req.progressActual())));
        } else if (task.getProgressActual() == null) {
            task.setProgressActual(0);
        }

        if (req.parentId() != null) {
            Task parent = taskRepository.findById(req.parentId())
                .orElseThrow(() -> new NoSuchElementException("Tarea padre no encontrada: " + req.parentId()));
            if (isAncestorOf(task.getId(), parent))
                throw new IllegalArgumentException("Referencia circular: la tarea no puede ser padre de sí misma o de sus antecesores");
            task.setParent(parent);
        } else {
            task.setParent(null);
        }

        if (req.comments() != null) {
            task.getComments().clear();
            for (CommentDTO cDto : req.comments()) {
                Comment comment = new Comment();
                comment.setAuthor(cDto.author() != null ? cDto.author() : "Usuario");
                comment.setText(cDto.text());
                comment.setDate(cDto.date() != null ? cDto.date() : java.time.LocalDateTime.now());
                comment.setTask(task);
                task.getComments().add(comment);
            }
        }

        return task;
    }

    private boolean isAncestorOf(Long candidateId, Task node) {
        if (candidateId == null) return false;
        Task cursor = node;
        while (cursor != null) {
            if (cursor.getId() != null && cursor.getId().equals(candidateId)) return true;
            cursor = cursor.getParent();
        }
        return false;
    }

    // ── DTO conversion ────────────────────────────────────────────────────────

    private TaskDTO toDTO(Task t) {
        boolean hasChildren = t.getChildren() != null && !t.getChildren().isEmpty();

        int progressActual = hasChildren
            ? computeProgressActual(t)
            : (t.getProgressActual() != null ? t.getProgressActual() : 0);

        LocalDate startDate = hasChildren ? computeStartDate(t) : t.getStartDate();
        LocalDate dueDate   = hasChildren ? computeDueDate(t)   : t.getDueDate();

        // For parent tasks: expected = avg of children's expected (same basis as actual).
        // This ensures real ≈ expected when each child is on track, avoiding false alerts.
        Integer progressExpected = hasChildren
            ? computeProgressExpectedFromChildren(t)
            : computeProgressExpected(t, startDate, dueDate);

        String status   = computeStatus(t).name();
        String priority = hasChildren ? computePriority(t).name() : (t.getPriority() != null ? t.getPriority().name() : null);

        List<Long>   assigneeIds   = hasChildren ? computeAssigneeIds(t)   : t.getAssignees().stream().map(TeamMember::getId).toList();
        List<String> assigneeNames = hasChildren ? computeAssigneeNames(t) : t.getAssignees().stream().map(TeamMember::getName).toList();

        List<CommentDTO> comments = t.getComments().stream()
            .map(c -> new CommentDTO(c.getId(), c.getAuthor(), c.getText(), c.getDate()))
            .sorted(Comparator.comparing(CommentDTO::date))
            .toList();

        return new TaskDTO(
            t.getId(),
            t.getTitle(),
            t.getDescription(),
            status,
            priority,
            t.getProject() != null ? t.getProject().getId()    : null,
            t.getProject() != null ? t.getProject().getName()  : null,
            t.getProject() != null ? t.getProject().getColor() : null,
            assigneeIds,
            assigneeNames,
            startDate,
            dueDate,
            t.getCreatedAt(),
            t.getUpdatedAt(),
            progressActual,
            progressExpected,
            t.getParent() != null ? t.getParent().getId() : null,
            hasChildren,
            comments
        );
    }

    // ── Derived property helpers (recursive) ──────────────────────────────────

    private int computeProgressActual(Task t) {
        List<Task> children = t.getChildren();
        if (children == null || children.isEmpty()) {
            if (t.getStatus() == TaskStatus.DONE) return 100;
            return t.getProgressActual() != null ? t.getProgressActual() : 0;
        }
        return (int) Math.round(children.stream()
            .mapToInt(this::computeProgressActual)
            .average().orElse(0));
    }

    private LocalDate computeStartDate(Task t) {
        List<Task> children = t.getChildren();
        if (children == null || children.isEmpty()) return t.getStartDate();
        return children.stream()
            .map(this::computeStartDate)
            .filter(Objects::nonNull)
            .min(Comparator.naturalOrder())
            .orElse(null);
    }

    private LocalDate computeDueDate(Task t) {
        List<Task> children = t.getChildren();
        if (children == null || children.isEmpty()) return t.getDueDate();
        return children.stream()
            .map(this::computeDueDate)
            .filter(Objects::nonNull)
            .max(Comparator.naturalOrder())
            .orElse(null);
    }

    private TaskStatus computeStatus(Task t) {
        List<Task> children = t.getChildren();
        if (children == null || children.isEmpty()) {
            if (t.getProgressActual() != null && t.getProgressActual() >= 100) return TaskStatus.DONE;
            return t.getStatus() != null ? t.getStatus() : TaskStatus.TODO;
        }
        List<TaskStatus> childStatuses = children.stream().map(this::computeStatus).toList();
        if (childStatuses.stream().allMatch(s -> s == TaskStatus.DONE))     return TaskStatus.DONE;
        if (childStatuses.stream().anyMatch(s -> s == TaskStatus.BLOCKED))  return TaskStatus.BLOCKED;
        if (childStatuses.stream().anyMatch(s -> s == TaskStatus.IN_REVIEW))return TaskStatus.IN_REVIEW;
        if (childStatuses.stream().anyMatch(s -> s == TaskStatus.IN_PROGRESS)) return TaskStatus.IN_PROGRESS;
        return TaskStatus.TODO;
    }

    private TaskPriority computePriority(Task t) {
        List<Task> children = t.getChildren();
        if (children == null || children.isEmpty())
            return t.getPriority() != null ? t.getPriority() : TaskPriority.MEDIUM;
        return children.stream()
            .map(this::computePriority)
            .max(Comparator.comparingInt(Enum::ordinal))
            .orElse(TaskPriority.MEDIUM);
    }

    private List<Long> computeAssigneeIds(Task t) {
        List<Task> children = t.getChildren();
        if (children == null || children.isEmpty())
            return t.getAssignees().stream().map(TeamMember::getId).toList();
        return children.stream()
            .flatMap(c -> computeAssigneeIds(c).stream())
            .distinct()
            .toList();
    }

    private List<String> computeAssigneeNames(Task t) {
        List<Task> children = t.getChildren();
        if (children == null || children.isEmpty())
            return t.getAssignees().stream().map(TeamMember::getName).toList();
        return children.stream()
            .flatMap(c -> computeAssigneeNames(c).stream())
            .distinct()
            .toList();
    }

    private Integer computeProgressExpectedFromChildren(Task t) {
        List<Task> children = t.getChildren();
        if (children == null || children.isEmpty()) {
            return computeProgressExpected(t, t.getStartDate(), t.getDueDate());
        }
        List<Integer> childExpected = children.stream()
            .map(c -> computeProgressExpectedFromChildren(c))
            .filter(Objects::nonNull)
            .toList();
        if (childExpected.isEmpty()) return null;
        return (int) Math.round(childExpected.stream().mapToInt(Integer::intValue).average().orElse(0));
    }

    private Integer computeProgressExpected(Task t, LocalDate startDate, LocalDate dueDate) {
        if (dueDate == null) return null;
        LocalDate start = startDate != null ? startDate
                        : (t.getCreatedAt() != null ? t.getCreatedAt().toLocalDate() : null);
        if (start == null) return null;
        LocalDate today = LocalDate.now();
        long totalDays = ChronoUnit.DAYS.between(start, dueDate);
        if (totalDays == 0) return !today.isBefore(dueDate) ? 100 : 0;
        if (totalDays < 0)  return 100;
        long elapsed = ChronoUnit.DAYS.between(start, today);
        return Math.max(0, Math.min(100, (int) Math.round((double) elapsed / totalDays * 100.0)));
    }
}
