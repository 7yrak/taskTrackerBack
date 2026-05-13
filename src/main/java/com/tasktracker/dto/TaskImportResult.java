package com.tasktracker.dto;

import java.util.List;

public record TaskImportResult(
    int imported,
    int skipped,
    List<String> warnings
) {}
