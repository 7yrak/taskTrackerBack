package com.tasktracker.service;

import com.tasktracker.dto.*;
import com.tasktracker.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service @RequiredArgsConstructor
public class ProjectDetailService {
    private final ProjectService projects;
    private final ProjectRepository projectRepository;
    private final TaskService tasks;
    private final TeamMemberService members;
    private final IncidentService incidents;
    private final OvertimeService overtime;

    @Transactional(readOnly = true)
    public ProjectDetailDTO get(Long id) {
        var project = projects.findById(id);
        var projectTasks = tasks.findByProject(id);
        var projectIncidents = incidents.forProject(id);
        Set<Long> memberIds = new TreeSet<>();
        projectRepository.findById(id).orElseThrow().getMembers().forEach(m -> memberIds.add(m.getId()));
        projectTasks.forEach(t -> memberIds.addAll(t.assigneeIds()));
        projectIncidents.forEach(i -> { if (i.developerId() != null) memberIds.add(i.developerId()); if (i.qaId() != null) memberIds.add(i.qaId()); });
        return new ProjectDetailDTO(project, projectTasks, memberIds.stream().map(members::findById).toList(),
            projectIncidents, overtime.forProject(id));
    }
}
