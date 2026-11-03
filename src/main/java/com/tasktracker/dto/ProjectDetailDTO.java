package com.tasktracker.dto;

import java.util.List;

public record ProjectDetailDTO(ProjectDTO project, List<TaskDTO> tasks,
    List<TeamMemberDTO> members, List<IncidentDTO> incidents, List<OvertimeDTO> overtime) {}
