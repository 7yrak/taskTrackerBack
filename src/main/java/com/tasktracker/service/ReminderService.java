package com.tasktracker.service;

import com.tasktracker.model.Reminder;
import com.tasktracker.model.ReminderComment;
import com.tasktracker.repository.ReminderCommentRepository;
import com.tasktracker.repository.ReminderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ReminderService {

    private final ReminderRepository reminderRepository;

    private final ReminderCommentRepository reminderCommentRepository;

    public List<Reminder> getAllReminders() {
        return reminderRepository.findAll();
    }

    public Optional<Reminder> getReminderById(Long id) {
        return reminderRepository.findById(id);
    }

    @Transactional
    public Reminder createReminder(Reminder reminder) {
        return reminderRepository.save(reminder);
    }

    @Transactional
    public Reminder updateReminder(Long id, Reminder updatedReminder) {
        return reminderRepository.findById(id).map(reminder -> {
            reminder.setTitle(updatedReminder.getTitle());
            reminder.setDescription(updatedReminder.getDescription());
            reminder.setStatus(updatedReminder.getStatus());
            reminder.setStartDate(updatedReminder.getStartDate());
            reminder.setDueDate(updatedReminder.getDueDate());
            return reminderRepository.save(reminder);
        }).orElseThrow(() -> new NoSuchElementException("Reminder not found: " + id));
    }

    @Transactional
    public void deleteReminder(Long id) {
        if (!reminderRepository.existsById(id)) {
            throw new NoSuchElementException("Reminder not found: " + id);
        }
        reminderRepository.deleteById(id);
    }

    @Transactional
    public Reminder addComment(Long reminderId, ReminderComment comment) {
        return reminderRepository.findById(reminderId).map(reminder -> {
            comment.setReminder(reminder);
            reminderCommentRepository.save(comment);
            reminder.getComments().add(comment);
            return reminder;
        }).orElseThrow(() -> new NoSuchElementException("Reminder not found: " + reminderId));
    }
}
