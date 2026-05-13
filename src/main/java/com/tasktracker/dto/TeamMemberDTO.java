package com.tasktracker.dto;

import java.time.LocalDateTime;
import java.util.List;

public record TeamMemberDTO(
    Long id,
    String name,
    String email,
    String role,
    LocalDateTime createdAt,
    List<String> projectNames,
    long taskCount
) {}
