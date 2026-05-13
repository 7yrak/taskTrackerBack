package com.tasktracker.repository;

import com.tasktracker.model.TeamMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TeamMemberRepository extends JpaRepository<TeamMember, Long> {
    Optional<TeamMember> findByEmail(String email);
    Optional<TeamMember> findByNameIgnoreCase(String name);
    boolean existsByEmail(String email);
}
