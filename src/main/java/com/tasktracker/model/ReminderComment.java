package com.tasktracker.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "reminder_comments")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReminderComment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(columnDefinition = "TEXT")
    private String text;

    private LocalDateTime date;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reminder_id")
    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Reminder reminder;

    @PrePersist
    protected void onCreate() {
        if (date == null) date = LocalDateTime.now();
    }
}