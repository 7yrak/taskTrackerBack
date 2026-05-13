package com.tasktracker.repository;

import com.tasktracker.model.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProjectRepository extends JpaRepository<Project, Long> {
    java.util.Optional<Project> findByNameIgnoreCase(String name);
}
