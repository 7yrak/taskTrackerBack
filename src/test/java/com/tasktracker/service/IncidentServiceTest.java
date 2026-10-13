package com.tasktracker.service;
import com.tasktracker.dto.*;
import com.tasktracker.model.*;
import com.tasktracker.repository.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.springframework.mock.web.MockMultipartFile;
class IncidentServiceTest {
 IncidentRepository repo; IncidentAttachmentRepository attachmentRepo; ProjectRepository projectRepo; IncidentService service; Incident incident;
 @BeforeEach void setup() {
  repo=mock(IncidentRepository.class);
  attachmentRepo=mock(IncidentAttachmentRepository.class);
  projectRepo=mock(ProjectRepository.class);
  service=new IncidentService(repo,attachmentRepo,projectRepo,mock(TaskRepository.class),mock(TeamMemberRepository.class),new IncidentWorkflow());
  incident=new Incident(); incident.setId(1L); incident.setTitle("Error"); incident.setDescription("Descripción"); incident.setReporter("Reportante"); incident.setPriority(TaskPriority.HIGH); incident.setDetectedEnvironment(IncidentEnvironment.PROD);
  Project p=new Project(); p.setId(1L); p.setName("Proyecto"); incident.setProject(p);
  TeamMember dev=new TeamMember(); dev.setId(2L); dev.setName("Desarrollo"); incident.setDeveloper(dev);
  TeamMember qa=new TeamMember(); qa.setId(3L); qa.setName("QA"); incident.setQa(qa);
  when(projectRepo.findLockedById(1L)).thenReturn(Optional.of(p));
  when(repo.findById(1L)).thenReturn(Optional.of(incident));
  when(repo.findLockedById(1L)).thenReturn(Optional.of(incident)); when(repo.saveAndFlush(any())).thenAnswer(a -> a.getArgument(0));
 }
 IncidentDTO action(String a,String actor,String note) { return service.transition(1L,new IncidentTransitionRequest(a,actor,note,null)); }
 @Test void completeRouteRequiresQaAndClientAndClosesOnDeploy() {
  assertEquals(IncidentStatus.DEVELOPMENT,action("START",null,null).status());
  assertThrows(IllegalArgumentException.class,()->action("SEND_UAT","QA",null));
  assertEquals(IncidentStatus.CERT,action("SEND_CERT",null,null).status());
  assertThrows(IllegalArgumentException.class,()->action("APPROVE_QA",null," "));
  assertEquals(IncidentStatus.QA_APPROVED,action("APPROVE_QA","Nombre arbitrario","Pruebas OK").status());
  assertEquals("QA",incident.getEvents().getLast().getActor());
  action("SEND_UAT",null,null);
  assertThrows(IllegalArgumentException.class,()->action("DEPLOY","Operador","Desplegado"));
  assertThrows(IllegalArgumentException.class,()->action("APPROVE_CLIENT"," ","OK"));
  action("APPROVE_CLIENT","Cliente","Aceptado");
  assertEquals(IncidentStatus.CLOSED,action("DEPLOY","Operador","Desplegado").status()); assertNotNull(incident.getClosedAt());
 }
 @Test void clientRejectionAndReopeningRequireFreshCertificationAndPreserveHistory() {
  action("START",null,null); action("SEND_CERT",null,null); action("APPROVE_QA",null,"OK"); action("SEND_UAT",null,null);
  action("REJECT","Cliente","Persiste el error");
  assertEquals(5,incident.getEvents().size());
  assertThrows(IllegalArgumentException.class,()->action("SEND_UAT","QA",null));
  assertThrows(IllegalArgumentException.class,()->action("APPROVE_CLIENT","Cliente","OK"));
  action("SEND_CERT",null,null); action("APPROVE_QA",null,"Nueva prueba OK"); action("SEND_UAT",null,null); action("APPROVE_CLIENT","Cliente","OK"); action("DEPLOY","Operador","OK");
  action("REOPEN","Reportante","Se reproduce nuevamente"); assertNull(incident.getClosedAt());
  assertEquals(11,incident.getEvents().size()); assertThrows(IllegalArgumentException.class,()->action("DEPLOY","Operador","OK"));
 }
 @Test void responsibilityAndReasonsAreMandatory() {
  incident.setDeveloper(null); assertThrows(IllegalArgumentException.class,()->action("START",null,null));
  incident.setStatus(IncidentStatus.DEVELOPMENT); incident.setDeveloper(new TeamMember()); incident.getDeveloper().setName("Dev"); incident.setQa(null);
  assertThrows(IllegalArgumentException.class,()->action("SEND_CERT",null,null));
  assertThrows(IllegalArgumentException.class,()->action("CANCEL","Autor",null));
  assertEquals(IncidentStatus.CANCELLED,action("CANCEL","Autor","Duplicado").status());
  assertThrows(IllegalArgumentException.class,()->action("START","Autor",null));
 }
 @Test void correctionCannotBeEditedAfterCertificationStarted() {
  incident.setStatus(IncidentStatus.QA_APPROVED);
  assertThrows(IllegalArgumentException.class,()->service.update(1L,null));
 }
 @Test void attachmentValidationAndCategories() throws Exception {
  byte[] png={(byte)137,80,78,71,13,10,26,10,0};
  assertEquals("image/png",IncidentService.detectType(png));
  service.upload(1L,"CORRECTION",new MockMultipartFile("file","prueba.png","image/png",png));
  var captured=org.mockito.ArgumentCaptor.forClass(IncidentAttachment.class);
  verify(attachmentRepo).saveAndFlush(captured.capture());
  assertEquals("CORRECTION",captured.getValue().getCategory());
  assertThrows(IllegalArgumentException.class,()->service.upload(1L,"INCIDENT",new MockMultipartFile("file","falso.png","image/png","texto".getBytes())));
  assertThrows(IllegalArgumentException.class,()->service.upload(1L,"INCIDENT",new MockMultipartFile("file","error.pdf","application/pdf",png)));
  assertThrows(IllegalArgumentException.class,()->service.upload(1L,"INVALID",new MockMultipartFile("file","prueba.png","image/png",png)));
  assertThrows(IllegalArgumentException.class,()->service.upload(1L,"INCIDENT",new MockMultipartFile("file","grande.png","image/png",new byte[10*1024*1024+1])));
 }
 @Test void evidenceLinksMustBeHttpOrHttps() {
  incident.setStatus(IncidentStatus.CERT);
  assertThrows(IllegalArgumentException.class,()->service.transition(1L,new IncidentTransitionRequest("APPROVE_QA",null,"OK","javascript:alert(1)")));
  assertEquals(IncidentStatus.CERT,incident.getStatus());
 }
}
