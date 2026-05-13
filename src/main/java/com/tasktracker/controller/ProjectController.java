package com.tasktracker.controller;

import com.tasktracker.dto.ProjectDTO;
import com.tasktracker.dto.ProjectRequest;
import com.tasktracker.service.ProjectService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    @GetMapping
    public List<ProjectDTO> getAll() {
        return projectService.findAll();
    }

    @GetMapping("/{id}")
    public ProjectDTO getById(@PathVariable Long id) {
        return projectService.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectDTO create(@Valid @RequestBody ProjectRequest req) {
        return projectService.create(req);
    }

    @PutMapping("/{id}")
    public ProjectDTO update(@PathVariable Long id, @Valid @RequestBody ProjectRequest req) {
        return projectService.update(id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        projectService.delete(id);
    }

    @PostMapping("/{projectId}/members/{memberId}")
    public ProjectDTO addMember(@PathVariable Long projectId, @PathVariable Long memberId) {
        return projectService.addMember(projectId, memberId);
    }

    @DeleteMapping("/{projectId}/members/{memberId}")
    public ProjectDTO removeMember(@PathVariable Long projectId, @PathVariable Long memberId) {
        return projectService.removeMember(projectId, memberId);
    }
}
