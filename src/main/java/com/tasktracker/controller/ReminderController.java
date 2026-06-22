package com.tasktracker.controller;

import com.tasktracker.model.Reminder;
import com.tasktracker.model.ReminderComment;
import com.tasktracker.service.ReminderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/reminders")
@RequiredArgsConstructor
public class ReminderController {

    private final ReminderService reminderService;

    @GetMapping
    public List<Reminder> getAllReminders() {
        return reminderService.getAllReminders();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Reminder> getReminderById(@PathVariable Long id) {
        return reminderService.getReminderById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Reminder createReminder(@RequestBody Reminder reminder) {
        return reminderService.createReminder(reminder);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Reminder> updateReminder(@PathVariable Long id, @RequestBody Reminder reminder) {
        try {
            return ResponseEntity.ok(reminderService.updateReminder(id, reminder));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteReminder(@PathVariable Long id) {
        reminderService.deleteReminder(id);
    }

    @PostMapping("/{id}/comments")
    public ResponseEntity<Reminder> addComment(@PathVariable Long id, @RequestBody ReminderComment comment) {
        try {
            return ResponseEntity.ok(reminderService.addComment(id, comment));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
