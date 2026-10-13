package com.tasktracker.service;

import com.tasktracker.model.*;
import com.tasktracker.repository.*;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import java.util.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:hierarchy;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=", "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect", "spring.jpa.show-sql=false", "logging.level.com.tasktracker=INFO"})
@AutoConfigureMockMvc
class HierarchyAndOvertimeApiTest {
 @Autowired MockMvc mvc; @Autowired ObjectMapper json;
 @Autowired ProjectRepository projects; @Autowired TaskRepository tasks; @Autowired IncidentRepository incidents;
 Project project(){return projects.save(Project.builder().name("Proyecto " + UUID.randomUUID()).status(ProjectStatus.INITIATED).build());}
 Task task(Project p,Task parent){Task t=new Task();t.setTitle("Tarea");t.setProject(p);t.setParent(parent);return tasks.save(t);}
 Incident incident(Project p,Incident parent){Incident i=new Incident();i.setTitle("Incidente");i.setDescription("Error");i.setReporter("QA");i.setProject(p);i.setParent(parent);i.setDetectedEnvironment(IncidentEnvironment.UAT);i.setPriority(TaskPriority.HIGH);return incidents.save(i);}
 void move(String kind,Long id,Long parent,Long project) throws Exception {
  Map<String,Object> r=new HashMap<>();r.put("parentId",parent);r.put("projectId",project);
  mvc.perform(patch("/api/"+kind+"/"+id+"/hierarchy").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(r))).andExpect(status().isNoContent());
 }
 JsonNode fetch(String kind,Long id)throws Exception{return json.readTree(mvc.perform(get("/api/"+kind+"/"+id)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());}
 @Test void movesWholeTaskSubtreeBetweenProjectsAndDetachesInvalidIncidentLink()throws Exception {
  Project a=project(),b=project();Task root=task(a,null),child=task(a,root),grandchild=task(a,child),target=task(b,null);
  Incident linked=incident(a,null);linked.setTask(child);incidents.save(linked);
  move("tasks",root.getId(),target.getId(),b.getId());
  assertEquals(target.getId().longValue(),fetch("tasks",root.getId()).get("parentId").asLong());
  assertEquals(b.getId().longValue(),fetch("tasks",grandchild.getId()).get("projectId").asLong());
  assertTrue(fetch("incidents",linked.getId()).get("taskId").isNull());
  assertEquals("HIERARCHY",fetch("incidents",linked.getId()).get("events").get(0).get("type").asText());
 }
 @Test void rejectsCyclesThenAllowsReversingTaskRelationship()throws Exception {
  Project p=project();Task root=task(p,null),child=task(p,root);
  mvc.perform(patch("/api/tasks/"+root.getId()+"/hierarchy").contentType(MediaType.APPLICATION_JSON).content("{\"parentId\":"+child.getId()+"}")).andExpect(status().isBadRequest());
  assertTrue(fetch("tasks",root.getId()).get("parentId").isNull());
  move("tasks",child.getId(),null,p.getId());move("tasks",root.getId(),child.getId(),p.getId());
  assertEquals(child.getId().longValue(),fetch("tasks",root.getId()).get("parentId").asLong());
 }
 @Test void incidentTreeMovesPreserveCertificationAndCanReverse()throws Exception {
  Project a=project(),b=project();Incident root=incident(a,null),child=incident(a,root),target=incident(b,null);
  child.setStatus(IncidentStatus.QA_APPROVED);incidents.save(child);
  move("incidents",root.getId(),target.getId(),b.getId());
  assertEquals(b.getId().longValue(),fetch("incidents",child.getId()).get("projectId").asLong());
  assertEquals("QA_APPROVED",fetch("incidents",child.getId()).get("status").asText());
  mvc.perform(patch("/api/incidents/"+target.getId()+"/hierarchy").contentType(MediaType.APPLICATION_JSON).content("{\"parentId\":"+child.getId()+"}")).andExpect(status().isBadRequest());
  move("incidents",child.getId(),null,b.getId());move("incidents",root.getId(),child.getId(),b.getId());
  assertEquals(child.getId().longValue(),fetch("incidents",root.getId()).get("parentId").asLong());
 }
 @Test void incidentsNeedProjectAndCannotParentThemselves()throws Exception {
  Incident row=incident(project(),null);
  mvc.perform(patch("/api/incidents/"+row.getId()+"/hierarchy").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());
  mvc.perform(patch("/api/incidents/"+row.getId()+"/hierarchy").contentType(MediaType.APPLICATION_JSON).content("{\"parentId\":"+row.getId()+"}")).andExpect(status().isBadRequest());
 }
 @Test void overtimeCrudPreservesFractionalHoursAndRejectsInvalidEntries()throws Exception {
  Project p=project();String request="{\"projectId\":"+p.getId()+",\"date\":\"2026-10-13\",\"person\":\"Rodrigo\",\"topic\":\"Certificación\",\"hours\":1.5}";
  JsonNode created=json.readTree(mvc.perform(post("/api/overtime").contentType(MediaType.APPLICATION_JSON).content(request)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
  assertEquals(1.5,created.get("hours").asDouble());
  assertEquals("Por confirmar",created.get("client").asText());
  Long id=created.get("id").asLong();
  mvc.perform(put("/api/overtime/"+id).contentType(MediaType.APPLICATION_JSON).content(request.replace("1.5","2.25").replace("}",",\"client\":\"Entel\"}"))).andExpect(status().isOk()).andExpect(jsonPath("$.hours").value(2.25)).andExpect(jsonPath("$.client").value("Entel"));
  for(String bad:List.of(request.replace("1.5","0"),request.replace("1.5","25"),request.replace("Certificación"," "),request.replace("Rodrigo"," ")))
   mvc.perform(post("/api/overtime").contentType(MediaType.APPLICATION_JSON).content(bad)).andExpect(status().isBadRequest());
  mvc.perform(delete("/api/overtime/"+id)).andExpect(status().isNoContent());
 }
}
