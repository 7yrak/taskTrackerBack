package com.tasktracker.controller;

import com.tasktracker.model.Reminder;
import com.tasktracker.model.ReminderComment;
import com.tasktracker.service.ReminderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/reminders")
@CrossOrigin(origins = "*")
public class ReminderController {

    @Autowired
    private ReminderService reminderService;

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
    public ResponseEntity<Void> deleteReminder(@PathVariable Long id) {
        reminderService.deleteReminder(id);
        return ResponseEntity.ok().build();
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