package com.tasktracker.dto;

import java.time.LocalDateTime;

public record CommentDTO(
    Long id,
    String author,
    String text,
    LocalDateTime date
) {}