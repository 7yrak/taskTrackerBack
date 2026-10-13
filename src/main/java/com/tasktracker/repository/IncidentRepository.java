package com.tasktracker.repository;
import com.tasktracker.model.Incident;
import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;
import java.util.*;
public interface IncidentRepository extends JpaRepository<Incident,Long> {
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select i from Incident i where i.id = :id")
 Optional<Incident> findLockedById(@org.springframework.data.repository.query.Param("id") Long id);
 List<Incident> findAllByOrderByCreatedAtDesc();
 List<Incident> findByProjectIdOrderByCreatedAtDesc(Long projectId);
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select i from Incident i where i.project.id = :projectId order by i.id")
 List<Incident> findLockedByProjectId(@org.springframework.data.repository.query.Param("projectId") Long projectId);
}
