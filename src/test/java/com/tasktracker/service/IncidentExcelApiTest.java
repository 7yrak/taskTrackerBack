package com.tasktracker.service;

import com.fasterxml.jackson.databind.*;
import com.tasktracker.model.*;
import com.tasktracker.repository.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.http.MediaType;
import java.io.*;
import java.time.*;
import java.util.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:incidents;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=", "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect", "spring.jpa.show-sql=false", "logging.level.com.tasktracker=INFO"})
@AutoConfigureMockMvc
class IncidentExcelApiTest extends com.tasktracker.security.AuthTestSupport {
    @Autowired org.springframework.web.context.WebApplicationContext context;
 @org.junit.jupiter.api.BeforeEach void authenticateBusinessApi() {
  mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(context)
   .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity())
   .defaultRequest(get("/").with(owner()).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()))
   .build();
 }
 @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired ProjectRepository projects;
    @Autowired IncidentRepository incidents;
    @Autowired IncidentAttachmentRepository attachments;
    @Autowired TeamMemberRepository members;
    @Autowired TaskRepository tasks;
    @Autowired IncidentExcelService excel;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean IncidentService incidentService;

    Project project() { return projects.save(Project.builder().name("Excel " + UUID.randomUUID()).build()); }
    Incident existing(Project p) {
        Incident i=new Incident(); i.setTitle("Anterior"); i.setDescription("Anterior"); i.setReporter("Reportante");
        i.setProject(p); i.setDetectedEnvironment(IncidentEnvironment.PROD); i.setPriority(TaskPriority.HIGH);
        return incidents.save(i);
    }
    byte[] workbook(String[][] rows) throws Exception {
        try(XSSFWorkbook book=new XSSFWorkbook(); ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            Sheet sheet=book.createSheet("Incidentes"); Row header=sheet.createRow(0);
            for(int c=0;c<IncidentExcelService.HEADERS.length;c++) header.createCell(c).setCellValue(IncidentExcelService.HEADERS[c]);
            for(int r=0;r<rows.length;r++){ Row row=sheet.createRow(r+1); for(int c=0;c<rows[r].length;c++) row.createCell(c).setCellValue(rows[r][c]); }
            book.write(out); return out.toByteArray();
        }
    }
    MockMultipartFile file(byte[] bytes) { return new MockMultipartFile("file","incidentes.xlsx","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",bytes); }
    JsonNode preview(Project p, byte[] bytes) throws Exception {
        return json.readTree(mvc.perform(multipart("/api/incidents/excel/preview").file(file(bytes)).param("projectId",p.getId().toString()).param("environment","CERT").param("reporter","Susana Antilaf"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
    JsonNode replace(Project p, byte[] bytes, String fingerprint) throws Exception {
        return json.readTree(mvc.perform(multipart("/api/incidents/excel/import").file(file(bytes)).param("projectId",p.getId().toString()).param("environment","CERT").param("reporter","Susana Antilaf").param("confirmed","true").param("fingerprint",fingerprint))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
    @Test void realSusanaWorkbookReplacesOnlyChosenProjectAndRemovesOldEvidence() throws Exception {
        Project p=project(), other=project(); Incident old=existing(p), untouched=existing(other);
        IncidentAttachment a=new IncidentAttachment(); a.setIncident(old); a.setCategory("INCIDENT"); a.setFilename("anterior.png"); a.setContentType("image/png"); a.setData(new byte[]{1,2,3}); a.setSize(3); a=attachments.save(a);
        byte[] bytes=getClass().getResourceAsStream("/excel/incidentes-susana-antilaf.xlsx").readAllBytes();
        JsonNode preview=preview(p,bytes); assertEquals(6,preview.get("count").asInt()); assertEquals(0,preview.get("errors").size());
        assertTrue(incidents.existsById(old.getId()));
        JsonNode result=replace(p,bytes,preview.get("fingerprint").asText()); assertEquals(6,result.get("imported").asInt()); assertEquals(1,result.get("replaced").asInt());
        assertFalse(incidents.existsById(old.getId())); assertFalse(attachments.existsById(a.getId())); assertTrue(incidents.existsById(untouched.getId()));
        var loaded=incidents.findByProjectIdOrderByCreatedAtDesc(p.getId()); assertEquals(6,loaded.size());
        assertTrue(loaded.stream().allMatch(i -> i.getStatus()==IncidentStatus.REGISTERED && "Susana Antilaf".equals(i.getReporter()) && "Pendiente validación QA".equals(i.getReportedStatus())));
        assertTrue(loaded.stream().allMatch(i -> i.getQaDeliveryDate()!=null));
        assertEquals(Set.of(LocalDate.of(2026,9,29),LocalDate.of(2026,9,30),LocalDate.of(2026,10,2)),new HashSet<>(loaded.stream().map(Incident::getQaDeliveryDate).toList()));
    }
    @Test void invalidRowPreventsEveryDeletionEvenWithOtherValidRows() throws Exception {
        Project p=project(); Incident old=existing(p);
        byte[] bytes=workbook(new String[][]{{"","Válido","Descripción","CERT","Alta","Susana"},{"","Sin descripción","","CERT","Alta","Susana"}});
        JsonNode preview=preview(p,bytes); assertEquals(1,preview.get("errors").size()); assertTrue(preview.get("errors").get(0).asText().contains("Fila 3"));
        mvc.perform(multipart("/api/incidents/excel/import").file(file(bytes)).param("projectId",p.getId().toString()).param("confirmed","true").param("fingerprint",preview.get("fingerprint").asText())).andExpect(status().isBadRequest());
        assertTrue(incidents.existsById(old.getId())); assertEquals(1,incidents.findByProjectIdOrderByCreatedAtDesc(p.getId()).size());
    }
    @Test void exportIsReimportableWithTypedDatesAndEmails() throws Exception {
        Project p=project(); Incident i=existing(p);
        TeamMember dev=members.save(TeamMember.builder().name("Dev Excel").email(UUID.randomUUID()+"@example.test").build());
        i.setStatus(IncidentStatus.CLOSED); i.setClosedAt(LocalDateTime.now());
        i.setDeveloper(dev); i.setDueDate(LocalDate.of(2026,11,1)); i.setQaDeliveryDate(LocalDate.of(2026,9,29)); i.setReportedStatus("Pendiente QA"); i.setImportNotes("Observación original"); incidents.save(i);
        byte[] bytes=mvc.perform(get("/api/incidents/excel/export").param("projectId",p.getId().toString())).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        try(XSSFWorkbook book=new XSSFWorkbook(new ByteArrayInputStream(bytes))){
            Sheet sheet=book.getSheet("Incidentes"); assertEquals(17,sheet.getRow(0).getLastCellNum());
            assertEquals(dev.getEmail(),sheet.getRow(1).getCell(6).getStringCellValue()); assertTrue(DateUtil.isCellDateFormatted(sheet.getRow(1).getCell(14)));
            assertEquals(LocalDate.of(2026,9,29),sheet.getRow(1).getCell(14).getLocalDateTimeCellValue().toLocalDate());
        }
        JsonNode preview=preview(p,bytes); assertEquals(0,preview.get("errors").size()); replace(p,bytes,preview.get("fingerprint").asText());
        Incident loaded=incidents.findByProjectIdOrderByCreatedAtDesc(p.getId()).getFirst(); assertEquals(IncidentStatus.REGISTERED,loaded.getStatus()); assertEquals(i.getDueDate(),loaded.getDueDate()); assertEquals("Pendiente QA",loaded.getReportedStatus());
    }
    @Test void emptyFileAndMissingConfirmationNeverClearProject() throws Exception {
        Project p=project(); Incident old=existing(p); byte[] empty=workbook(new String[][]{});
        JsonNode preview=preview(p,empty); assertEquals(1,preview.get("errors").size());
        mvc.perform(multipart("/api/incidents/excel/import").file(file(empty)).param("projectId",p.getId().toString()).param("confirmed","true").param("fingerprint",preview.get("fingerprint").asText())).andExpect(status().isBadRequest());
        byte[] valid=workbook(new String[][]{{"","Nuevo","Descripción","CERT","Media","Susana"}});
        mvc.perform(multipart("/api/incidents/excel/import").file(file(valid)).param("projectId",p.getId().toString()).param("fingerprint",preview.get("fingerprint").asText())).andExpect(status().isBadRequest());
        assertTrue(incidents.existsById(old.getId()));
    }
    @Test void replacingIncidentHierarchyClearsParentReferencesBeforeDelete() throws Exception {
        Project p=project(); Incident root=existing(p); Incident child=existing(p); child.setParent(root); incidents.save(child);
        byte[] bytes=workbook(new String[][]{{"","Nuevo","Descripción","UAT","Media","Susana"}});
        JsonNode preview=preview(p,bytes); replace(p,bytes,preview.get("fingerprint").asText());
        assertFalse(incidents.existsById(root.getId())); assertFalse(incidents.existsById(child.getId()));
        assertEquals(1,incidents.findByProjectIdOrderByCreatedAtDesc(p.getId()).size());
    }
    @Test void stalePreviewDoesNotOverwriteChanges() throws Exception {
        Project p=project(); Incident old=existing(p); byte[] bytes=workbook(new String[][]{{"","Nuevo","Descripción","CERT","Media","Susana"}});
        JsonNode preview=preview(p,bytes); old.setDescription("Editado después de previsualizar"); incidents.save(old);
        mvc.perform(multipart("/api/incidents/excel/import").file(file(bytes)).param("projectId",p.getId().toString()).param("confirmed","true").param("fingerprint",preview.get("fingerprint").asText())).andExpect(status().isBadRequest());
        assertEquals("Editado después de previsualizar",incidents.findById(old.getId()).orElseThrow().getDescription());
    }
    @Test void mismatchedProjectInvalidDateEmailTaskAndFormulasAreReported() throws Exception {
        Project p=project(); Project other=project(); Task task=tasks.save(Task.builder().title("Otra tarea").project(other).build());
        byte[] bytes=workbook(new String[][]{
            {other.getName(),"Proyecto incorrecto","Descripción","CERT","Media","Susana"},
            {"","Email inválido","Descripción","CERT","Media","Susana","ausente@example.test"},
            {"","Fecha inválida","Descripción","CERT","Media","Susana","","","","31/02/2026"},
            {"","Tarea incorrecta","Descripción","CERT","Media","Susana","","",task.getId().toString()}
        });
        assertEquals(4,preview(p,bytes).get("errors").size());
        try(XSSFWorkbook book=new XSSFWorkbook(new ByteArrayInputStream(workbook(new String[][]{{"","Formula","Descripción","CERT","Media","Susana"}})));ByteArrayOutputStream out=new ByteArrayOutputStream()){
            book.getSheet("Incidentes").getRow(1).getCell(2).setCellFormula("1+1");book.write(out);
            assertTrue(preview(p,out.toByteArray()).get("errors").get(0).asText().contains("fórmulas"));
        }
    }
    @Test void storageFailureRollsBackReplacementAndEvidenceDeletion() throws Exception {
        Project p=project(); Incident old=existing(p);
        IncidentAttachment a=new IncidentAttachment(); a.setIncident(old); a.setCategory("INCIDENT"); a.setFilename("original.pdf"); a.setContentType("application/pdf"); a.setData(new byte[]{1,2,3}); a.setSize(3); a=attachments.save(a);
        long attachmentId=a.getId();
        byte[] bytes=workbook(new String[][]{{"","Nuevo","Descripción","CERT","Media","Susana"}});
        String fingerprint=preview(p,bytes).get("fingerprint").asText();
        org.mockito.Mockito.doThrow(new IllegalStateException("Fallo de almacenamiento simulado"))
            .when(incidentService).create(org.mockito.ArgumentMatchers.any(com.tasktracker.dto.IncidentRequest.class));
        assertThrows(IllegalStateException.class,() -> excel.replace(file(bytes),p.getId(),"CERT","Susana",true,fingerprint));
        assertTrue(incidents.existsById(old.getId())); assertTrue(attachments.existsById(attachmentId));
        assertEquals(1,incidents.findByProjectIdOrderByCreatedAtDesc(p.getId()).size());
    }

    @Test void templateAndInvalidWorkbookValidation() throws Exception {
        Project p=project(); byte[] bytes=mvc.perform(get("/api/incidents/excel/template").param("projectId",p.getId().toString())).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        try(XSSFWorkbook book=new XSSFWorkbook(new ByteArrayInputStream(bytes))){ assertNotNull(book.getSheet("Instrucciones")); assertEquals("Título",book.getSheet("Incidentes").getRow(0).getCell(1).getStringCellValue()); }
        mvc.perform(multipart("/api/incidents/excel/preview").file(file("No es Excel".getBytes())).param("projectId",p.getId().toString())).andExpect(status().isBadRequest());
    }
}
