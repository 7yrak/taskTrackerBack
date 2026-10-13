package com.tasktracker.model;
import jakarta.persistence.*;
import lombok.*;
import java.time.*;
import java.math.BigDecimal;
@Entity @Table(name="overtime_entries") @Getter @Setter @NoArgsConstructor
public class OvertimeEntry {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) private Project project;
 @Column(nullable=false) private LocalDate date;
 @Column(nullable=false) private String person;
 @Column(nullable=false,columnDefinition="TEXT") private String topic;
 @Column(nullable=false,precision=5,scale=2) private BigDecimal hours;
 @Column(length=255) private String client;
 private LocalDateTime createdAt=LocalDateTime.now();
}
