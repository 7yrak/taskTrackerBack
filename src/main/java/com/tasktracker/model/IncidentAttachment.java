package com.tasktracker.model;
import jakarta.persistence.*;
import lombok.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.*;
@Entity @Table(name="incident_attachments") @Getter @Setter @NoArgsConstructor
public class IncidentAttachment {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @ManyToOne(fetch=FetchType.LAZY, optional=false) @JsonIgnore private Incident incident;
 @Column(nullable=false) private String category;
 @Column(nullable=false) private String filename;
 @Column(nullable=false) private String contentType;
 @Column(nullable=false) private long size;
 @Column(nullable=false, columnDefinition="bytea") @JsonIgnore private byte[] data;
 private LocalDateTime createdAt = LocalDateTime.now();
}
