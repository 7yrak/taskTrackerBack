package com.tasktracker.repository;

import com.tasktracker.model.IncidentAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface IncidentAttachmentRepository extends JpaRepository<IncidentAttachment, Long> {
    Optional<IncidentAttachment> findByIdAndIncidentId(Long id, Long incidentId);

    @org.springframework.data.jpa.repository.Modifying
    @Query("delete from IncidentAttachment a where a.incident.project.id = :projectId")
    void deleteByIncidentProjectId(@Param("projectId") Long projectId);

    // List metadata without loading the binary contents of every evidence file.
    @Query("select a.id as id, a.category as category, a.filename as filename, " +
        "a.contentType as contentType, a.size as size, a.createdAt as createdAt " +
        "from IncidentAttachment a where a.incident.id = :incidentId order by a.id")
    List<Metadata> findMetadataByIncidentId(@Param("incidentId") Long incidentId);

    interface Metadata {
        Long getId();
        String getCategory();
        String getFilename();
        String getContentType();
        long getSize();
        LocalDateTime getCreatedAt();
    }
}
