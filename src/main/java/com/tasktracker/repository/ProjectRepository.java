package com.tasktracker.repository;

import com.tasktracker.model.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProjectRepository extends JpaRepository<Project, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select p from Project p where p.id = :id")
    java.util.Optional<Project> findLockedById(@org.springframework.data.repository.query.Param("id") Long id);
    java.util.Optional<Project> findByNameIgnoreCase(String name);
}
