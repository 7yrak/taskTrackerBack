package com.tasktracker.service;
import com.tasktracker.model.*;
import com.tasktracker.repository.*;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.http.MediaType;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:incidents;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=", "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect", "spring.jpa.show-sql=false", "logging.level.com.tasktracker=INFO"})
@AutoConfigureMockMvc
class IncidentApiTest extends com.tasktracker.security.KovaHttpTestSupport {
 @Autowired org.springframework.web.context.WebApplicationContext context;
 @org.junit.jupiter.api.BeforeEach void authenticateBusinessApi() {
  mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(context)
   .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity())
   .defaultRequest(get("/").with(kovaAdmin()).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()))
   .build();
 }
 @Autowired MockMvc mvc; @Autowired ObjectMapper json;
 @Autowired ProjectRepository projects; @Autowired TeamMemberRepository members; @Autowired TaskRepository tasks;
 JsonNode postAction(long id,String action,String actor,String observation) throws Exception {
  var body=json.createObjectNode().put("action",action).put("actor",actor).put("observation",observation);
  return json.readTree(mvc.perform(post("/api/incidents/{id}/transitions",id).contentType(MediaType.APPLICATION_JSON).content(body.toString())).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
 }
 @Test void persistedApiFlowAndEvidence() throws Exception {
  Project p=Project.builder().name("Prueba incidentes").build(); p=projects.save(p);
  TeamMember dev=members.save(TeamMember.builder().name("Dev API").email("dev-api@example.test").build());
  TeamMember qa=members.save(TeamMember.builder().name("QA API").email("qa-api@example.test").build());
  var body=json.createObjectNode().put("title","Incidente API").put("description","Error de prueba").put("reporter","Cliente").put("projectId",p.getId()).put("developerId",dev.getId()).put("qaId",qa.getId()).put("detectedEnvironment","PROD").put("priority","HIGH");
  long id=json.readTree(mvc.perform(post("/api/incidents").contentType(MediaType.APPLICATION_JSON).content(body.toString())).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();
  mvc.perform(post("/api/incidents/{id}/transitions",id).contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"SEND_UAT\",\"actor\":\"QA\"}")).andExpect(status().isBadRequest());
  postAction(id,"START",""," "); postAction(id,"SEND_CERT","",""); postAction(id,"APPROVE_QA","","OK en CERT"); postAction(id,"SEND_UAT","","");
  postAction(id,"REJECT","Cliente","Persiste");
  mvc.perform(post("/api/incidents/{id}/transitions",id).contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"SEND_UAT\",\"actor\":\"QA\"}")).andExpect(status().isBadRequest());
  postAction(id,"SEND_CERT","",""); postAction(id,"APPROVE_QA","","Nueva prueba"); postAction(id,"SEND_UAT","",""); postAction(id,"APPROVE_CLIENT","Cliente","OK en UAT");
  byte[] image={(byte)137,80,78,71,13,10,26,10,0};
  JsonNode uploaded=json.readTree(mvc.perform(multipart("/api/incidents/{id}/attachments",id).file(new MockMultipartFile("file","evidencia.png","image/png",image)).param("category","CORRECTION")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
  long attachmentId=uploaded.get("attachments").get(0).get("id").asLong();
  mvc.perform(get("/api/incidents/{id}/attachments/{attachmentId}",id,attachmentId)).andExpect(status().isOk()).andExpect(content().bytes(image)).andExpect(header().string("X-Content-Type-Options","nosniff"));
  mvc.perform(get("/api/incidents/{id}/attachments/{attachmentId}",id+999,attachmentId)).andExpect(status().isNotFound());
  postAction(id,"DEPLOY","Operador","Despliegue realizado");
  JsonNode fetched=json.readTree(mvc.perform(get("/api/incidents/{id}",id)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
  assertEquals("CLOSED",fetched.get("status").asText()); assertFalse(fetched.get("closedAt").isNull()); assertEquals(12,fetched.get("events").size());
  postAction(id,"REOPEN","Reportante","Regresión");
  mvc.perform(get("/api/incidents")).andExpect(status().isOk());
 }
 @Test void inlineDataAndStateSaveAtomicallyAndRetainCertificationRules() throws Exception {
  Project p=projects.save(Project.builder().name("Inline estados").build());
  TeamMember dev=members.save(TeamMember.builder().name("Dev inline").email("dev-inline@example.test").build());
  TeamMember qa=members.save(TeamMember.builder().name("QA inline").email("qa-inline@example.test").build());
  var data=json.createObjectNode().put("title","Original").put("description","Prueba").put("reporter","QA").put("projectId",p.getId()).put("developerId",dev.getId()).put("qaId",qa.getId()).put("detectedEnvironment","UAT").put("priority","HIGH");
  long id=json.readTree(mvc.perform(post("/api/incidents").contentType(MediaType.APPLICATION_JSON).content(data.toString())).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();
  var edit=json.createObjectNode(); edit.set("data",data.deepCopy().put("title","Editado en fila"));
  edit.set("transition",json.createObjectNode().put("action","START"));
  mvc.perform(post("/api/incidents/{id}/inline-edit",id).contentType(MediaType.APPLICATION_JSON).content(edit.toString())).andExpect(status().isOk()).andExpect(jsonPath("$.title").value("Editado en fila")).andExpect(jsonPath("$.status").value("DEVELOPMENT"));
  edit.set("data",data.deepCopy().put("title","No debe guardarse").putNull("qaId"));
  edit.set("transition",json.createObjectNode().put("action","SEND_CERT"));
  mvc.perform(post("/api/incidents/{id}/inline-edit",id).contentType(MediaType.APPLICATION_JSON).content(edit.toString())).andExpect(status().isBadRequest());
  mvc.perform(get("/api/incidents/{id}",id)).andExpect(status().isOk()).andExpect(jsonPath("$.title").value("Editado en fila")).andExpect(jsonPath("$.qaId").value(qa.getId())).andExpect(jsonPath("$.status").value("DEVELOPMENT"));
  postAction(id,"SEND_CERT","","");
  edit.putNull("data");edit.set("transition",json.createObjectNode().put("action","APPROVE_QA").put("observation",""));
  mvc.perform(post("/api/incidents/{id}/inline-edit",id).contentType(MediaType.APPLICATION_JSON).content(edit.toString())).andExpect(status().isBadRequest());
  edit.set("transition",json.createObjectNode().put("action","APPROVE_QA").put("observation","Pruebas correctas"));
  mvc.perform(post("/api/incidents/{id}/inline-edit",id).contentType(MediaType.APPLICATION_JSON).content(edit.toString())).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("QA_APPROVED"));
  edit.set("transition",json.createObjectNode().put("action","DEPLOY").put("actor","Operador").put("observation","No permitido"));
  mvc.perform(post("/api/incidents/{id}/inline-edit",id).contentType(MediaType.APPLICATION_JSON).content(edit.toString())).andExpect(status().isBadRequest());
  edit.putNull("transition");
  mvc.perform(post("/api/incidents/{id}/inline-edit",id).contentType(MediaType.APPLICATION_JSON).content(edit.toString())).andExpect(status().isBadRequest());
 }
 @Test void invalidRequestsAndCrossProjectTaskAreRejected() throws Exception {
  mvc.perform(post("/api/incidents").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());
  Project p=projects.save(Project.builder().name("Proyecto A").build()); Project other=projects.save(Project.builder().name("Proyecto B").build());
  Task t=tasks.save(Task.builder().title("Tarea otro proyecto").project(other).build());
  var body=json.createObjectNode().put("title","Error").put("description","Error").put("reporter","Autor").put("projectId",p.getId()).put("taskId",t.getId()).put("detectedEnvironment","CERT").put("priority","LOW");
  mvc.perform(post("/api/incidents").contentType(MediaType.APPLICATION_JSON).content(body.toString())).andExpect(status().isBadRequest());
 }
}
