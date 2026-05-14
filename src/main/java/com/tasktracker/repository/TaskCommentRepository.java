package com.tasktracker.repository;

import com.tasktracker.model.Comment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskCommentRepository extends JpaRepository<Comment, Long> {
}