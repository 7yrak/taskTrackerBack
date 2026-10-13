package com.tasktracker.dto;
import java.util.List;
public record IncidentImportPreview(int count, int existingCount, String projectName, String fingerprint,
    List<PreviewRow> rows, List<String> errors, List<String> warnings) {
    public record PreviewRow(int row, String title, String environment, String priority, String reporter, String reportedStatus) {}
}
