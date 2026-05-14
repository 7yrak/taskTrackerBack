package com.tasktracker.repository;

import com.tasktracker.model.ReminderComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ReminderCommentRepository extends JpaRepository<ReminderComment, Long> {
}