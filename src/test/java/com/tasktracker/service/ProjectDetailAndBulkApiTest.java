package com.tasktracker.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tasktracker.model.*;
import com.tasktracker.repository.*;
import com.tasktracker.dto.OvertimeRequest;
import com.tasktracker.security.AuthTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:projectBulk;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=", "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect", "spring.jpa.show-sql=false"})
@AutoConfigureMockMvc
class ProjectDetailAndBulkApiTest extends AuthTestSupport {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired TaskRepository tasks;
    @Autowired ProjectRepository projects;
    @Autowired TeamMemberRepository members;
    @Autowired IncidentRepository incidents;
    @Autowired OvertimeService overtime;
    @Autowired TaskService service;

    Project project() { return projects.save(Project.builder().name("Ficha " + UUID.randomUUID()).status(ProjectStatus.IN_PROGRESS).build()); }
    Task task(Project project, String title) {
        var task = new Task(); task.setProject(project); task.setTitle(title); task.setDescription("Conservar descripción");
        task.setStatus(TaskStatus.IN_PROGRESS); task.setPriority(TaskPriority.LOW); task.setProgressActual(35);
        task.setStartDate(LocalDate.of(2026,10,1)); task.setDueDate(LocalDate.of(2026,12,1)); return tasks.save(task);
    }
    TeamMember member() { return members.save(TeamMember.builder().name("Sergio").email(UUID.randomUUID()+"@example.test").build()); }
    org.springframework.test.web.servlet.ResultActions batch(Map<String,Object> request) throws Exception {
        return mvc.perform(patch("/api/tasks/bulk").with(owner()).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(request)));
    }

    @Test void updatesOnlyChosenFieldsAndTasksPreservingHierarchyAndProjectState() throws Exception {
        var p = project(); var a = task(p,"A"); var b = task(p,"B"); var untouched = task(p,"Sin seleccionar");
        var member = member(); p.setStatus(ProjectStatus.ON_HOLD); projects.save(p);
        batch(Map.of("taskIds",List.of(b.getId(),a.getId()),"priority","CRITICAL","updateAssignees",true,"assigneeIds",List.of(member.getId())))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(b.getId())).andExpect(jsonPath("$[1].assigneeIds[0]").value(member.getId()));
        var saved = service.findById(a.getId());
        assertEquals("CRITICAL", saved.priority()); assertEquals("Conservar descripción",saved.description());
        assertEquals(35,saved.progressActual()); assertEquals(LocalDate.of(2026,12,1),saved.dueDate()); assertNull(saved.parentId());
        assertEquals("LOW",service.findById(untouched.getId()).priority());
        assertEquals(ProjectStatus.ON_HOLD,projects.findById(p.getId()).orElseThrow().getStatus());
    }

    @Test void rejectsWholeBatchWhenALaterTasksDatesWouldBeInvalid() throws Exception {
        var p = project(); var a = task(p,"Válida"); var b = task(p,"Inválida");
        b.setStartDate(LocalDate.of(2026,11,1)); tasks.save(b);
        batch(Map.of("taskIds",List.of(a.getId(),b.getId()),"priority","HIGH","updateDueDate",true,"dueDate","2026-10-15"))
            .andExpect(status().isBadRequest());
        for (var id : List.of(a.getId(),b.getId())) { var saved = service.findById(id); assertEquals("LOW",saved.priority()); assertEquals(LocalDate.of(2026,12,1),saved.dueDate()); }
    }

    @Test void rejectsMissingTasksMembersDuplicatesAndEmptyChangesWithoutWriting() throws Exception {
        var p=project(); var a=task(p,"Tarea");
        batch(Map.of("taskIds",List.of(a.getId(),Long.MAX_VALUE),"priority","HIGH")).andExpect(status().isBadRequest());
        batch(Map.of("taskIds",List.of(a.getId()),"updateAssignees",true,"assigneeIds",List.of(Long.MAX_VALUE))).andExpect(status().isBadRequest());
        batch(Map.of("taskIds",List.of(a.getId(),a.getId()),"priority","HIGH")).andExpect(status().isBadRequest());
        batch(Map.of("taskIds",List.of(a.getId()))).andExpect(status().isBadRequest());
        assertEquals("LOW",service.findById(a.getId()).priority());
    }

    @Test void groupFieldsCannotBeOverwrittenAndChildrenRemainUnchanged() throws Exception {
        var p=project(); var parent=task(p,"Grupo"); var child=task(p,"Hija"); child.setParent(parent); tasks.save(child);
        batch(Map.of("taskIds",List.of(parent.getId(),child.getId()),"priority","HIGH")).andExpect(status().isBadRequest());
        assertEquals("LOW",service.findById(child.getId()).priority()); assertEquals(parent.getId(),service.findById(child.getId()).parentId());
    }

    @Test void completesLeavesAndReopensWithConsistentProgressAndProjectStatus() throws Exception {
        var p=project(); var parent=task(p,"Grupo"); var child=task(p,"Hija"); child.setParent(parent); tasks.save(child);
        batch(Map.of("taskIds",List.of(child.getId()),"status","DONE")).andExpect(status().isOk()).andExpect(jsonPath("$[0].progressActual").value(100));
        assertEquals(ProjectStatus.COMPLETED,projects.findById(p.getId()).orElseThrow().getStatus());
        assertEquals("DONE",service.findById(parent.getId()).status());
        batch(Map.of("taskIds",List.of(child.getId()),"status","IN_PROGRESS")).andExpect(status().isOk()).andExpect(jsonPath("$[0].progressActual").value(0));
        assertEquals(ProjectStatus.IN_PROGRESS,projects.findById(p.getId()).orElseThrow().getStatus());
    }

    @Test void explicitClearRemovesDatesAndAssigneesWhileOmittedValuesRemain() throws Exception {
        var p=project(); var a=task(p,"Tarea"); a.setAssignees(new ArrayList<>(List.of(member()))); tasks.save(a);
        batch(Map.of("taskIds",List.of(a.getId()),"updateStartDate",true,"updateAssignees",true,"assigneeIds",List.of()))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].assigneeIds.length()").value(0));
        var saved=service.findById(a.getId()); assertNull(saved.startDate()); assertEquals(LocalDate.of(2026,12,1),saved.dueDate());
    }

    @Test void detailContainsOnlyProjectDataEvenWhenProjectIsClosed() throws Exception {
        var p=project(); var other=project(); var a=task(p,"Tarea de la ficha"); task(other,"No incluir");
        var assigned=member(); var registered=member(); a.setAssignees(new ArrayList<>(List.of(assigned))); tasks.save(a);
        p.setMembers(new HashSet<>(List.of(registered))); p.setStatus(ProjectStatus.CLOSED); projects.save(p);
        var i=new Incident(); i.setProject(p); i.setTitle("Incidente de la ficha"); i.setDescription("Validación"); i.setReporter("Sergio"); i.setDetectedEnvironment(IncidentEnvironment.CERT); i.setPriority(TaskPriority.HIGH); incidents.save(i);
        overtime.save(null,new OvertimeRequest(p.getId(),LocalDate.of(2026,11,2),"Sergio","Certificación",new BigDecimal("2.25"),"Por confirmar",false));
        overtime.save(null,new OvertimeRequest(other.getId(),LocalDate.of(2026,11,2),"Sergio","Otro proyecto",BigDecimal.ONE,"Por confirmar",false));
        mvc.perform(get("/api/projects/"+p.getId()+"/detail").with(owner())).andExpect(status().isOk())
            .andExpect(jsonPath("$.project.status").value("CLOSED"))
            .andExpect(jsonPath("$.tasks.length()").value(1)).andExpect(jsonPath("$.tasks[0].id").value(a.getId()))
            .andExpect(jsonPath("$.members.length()").value(2)).andExpect(jsonPath("$.incidents.length()").value(1))
            .andExpect(jsonPath("$.overtime.length()").value(1)).andExpect(jsonPath("$.overtime[0].hours").value(2.25));
    }

    @Test void endpointsRequireSessionCsrfAndValidRequest() throws Exception {
        mvc.perform(get("/api/projects/1/detail")).andExpect(status().isUnauthorized());
        mvc.perform(patch("/api/tasks/bulk").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isUnauthorized());
        mvc.perform(patch("/api/tasks/bulk").with(owner()).contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isForbidden());
        batch(Map.of("taskIds",List.of(),"priority","HIGH")).andExpect(status().isBadRequest());
        batch(Map.of("taskIds",List.of(0),"priority","HIGH")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/projects/"+Long.MAX_VALUE+"/detail").with(owner())).andExpect(status().isNotFound());
    }
}
