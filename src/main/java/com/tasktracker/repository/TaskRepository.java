package com.tasktracker.repository;

import com.tasktracker.model.Task;
import com.tasktracker.model.TaskPriority;
import com.tasktracker.model.TaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface TaskRepository extends JpaRepository<Task, Long>, JpaSpecificationExecutor<Task> {

    List<Task> findByProjectId(Long projectId);

    List<Task> findByAssigneesId(Long assigneeId);

    List<Task> findByStatus(TaskStatus status);

    List<Task> findByPriority(TaskPriority priority);

    long countByStatus(TaskStatus status);

    long countByPriority(TaskPriority priority);

    long countByProjectId(Long projectId);

    long countByProjectIdAndStatus(Long projectId, TaskStatus status);

    @Query("SELECT COUNT(t) FROM Task t WHERE t.dueDate < :today AND t.status NOT IN ('DONE')")
    long countOverdue(LocalDate today);

    @Query("SELECT DISTINCT t.parent.id FROM Task t WHERE t.project.id = :projectId AND t.parent IS NOT NULL")
    List<Long> findParentIdsByProjectId(@Param("projectId") Long projectId);

    @Modifying
    @Query("UPDATE Task t SET t.parent = null WHERE t.parent.id = :parentId")
    void detachFromParent(@Param("parentId") Long parentId);

    @Modifying
    @Query(value = "UPDATE tasks SET parent_id = NULL", nativeQuery = true)
    void clearAllParentReferences();

    @Modifying
    @Query(value = "DELETE FROM task_assignees", nativeQuery = true)
    void deleteAllAssignees();
}
