package com.tasktracker.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record TeamMemberRequest(
    @NotBlank String name,
    @Email @NotBlank String email,
    String role
) {}
