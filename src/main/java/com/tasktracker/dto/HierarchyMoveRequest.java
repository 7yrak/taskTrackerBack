package com.tasktracker.dto;
/** A null parent promotes the row to a root in the destination project. */
public record HierarchyMoveRequest(Long parentId, Long projectId) {}
