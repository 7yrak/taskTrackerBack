package com.tasktracker.model;
import jakarta.persistence.*;
import lombok.*;
import java.time.*;
import java.util.*;
@Entity @Table(name="incidents") @Getter @Setter @NoArgsConstructor
public class Incident {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(nullable=false) private String title;
 @Column(nullable=false, columnDefinition="TEXT") private String description;
 @Enumerated(EnumType.STRING) @Column(nullable=false) private IncidentEnvironment detectedEnvironment;
 @Enumerated(EnumType.STRING) @Column(nullable=false) private TaskPriority priority;
 @Enumerated(EnumType.STRING) @Column(nullable=false) private IncidentStatus status = IncidentStatus.REGISTERED;
 @Column(nullable=false) private String reporter;
 @ManyToOne(fetch=FetchType.LAZY, optional=false) private Project project;
 @ManyToOne(fetch=FetchType.LAZY) private Task task;
 @ManyToOne(fetch=FetchType.LAZY) private TeamMember developer;
 @ManyToOne(fetch=FetchType.LAZY) private TeamMember qa;
 @Column(columnDefinition="TEXT") private String reproductionSteps;
 @Column(columnDefinition="TEXT") private String expectedResult;
 @Column(columnDefinition="TEXT") private String actualResult;
 private LocalDate dueDate;
 private LocalDateTime createdAt = LocalDateTime.now();
 private LocalDateTime updatedAt = LocalDateTime.now();
 private LocalDateTime closedAt;
 @Version private Long version;
 @OneToMany(mappedBy="incident", cascade=CascadeType.ALL) @OrderBy("id ASC")
 private List<IncidentEvent> events = new ArrayList<>();
 @OneToMany(mappedBy="incident", cascade=CascadeType.ALL) @OrderBy("id ASC")
 private List<IncidentAttachment> attachments = new ArrayList<>();
}
