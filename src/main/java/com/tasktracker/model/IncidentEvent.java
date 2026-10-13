package com.tasktracker.model;
import jakarta.persistence.*;
import lombok.*;
import java.time.*;
@Entity @Table(name="incident_events") @Getter @Setter @NoArgsConstructor
public class IncidentEvent {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @ManyToOne(fetch=FetchType.LAZY, optional=false) private Incident incident;
 @Column(nullable=false) private String type;
 @Enumerated(EnumType.STRING) private IncidentStatus fromStatus;
 @Enumerated(EnumType.STRING) private IncidentStatus toStatus;
 @Column(nullable=false) private String actor;
 @Column(nullable=false, columnDefinition="TEXT") private String observation;
 @Column(columnDefinition="TEXT") private String evidenceUrl;
 @Column(nullable=false) private LocalDateTime createdAt = LocalDateTime.now();
}
