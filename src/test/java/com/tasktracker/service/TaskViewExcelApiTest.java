package com.tasktracker.service;

import com.tasktracker.model.*;
import com.tasktracker.repository.*;
import com.tasktracker.dto.OvertimeRequest;
import com.tasktracker.security.AuthTestSupport;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import java.io.*;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:viewExport;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=", "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect", "spring.jpa.show-sql=false"})
@AutoConfigureMockMvc
class TaskViewExcelApiTest extends AuthTestSupport {
    @Autowired MockMvc mvc;
    @Autowired TaskRepository tasks;
    @Autowired ProjectRepository projects;
    @Autowired OvertimeService overtime;
    @Autowired IncidentRepository incidents;
    @Autowired IncidentExcelService incidentExcel;

    Task task(Project project, String title, int progress) {
        var value = new Task(); value.setTitle(title); value.setProject(project);
        value.setStatus(TaskStatus.IN_PROGRESS); value.setPriority(TaskPriority.HIGH);
        value.setStartDate(LocalDate.of(2026, 10, 1)); value.setDueDate(LocalDate.of(2026, 11, 15));
        value.setProgressActual(progress); return tasks.save(value);
    }

    @Test void exportsOnlySelectedTasksInViewOrderWithNumericDatesAndPercentages() throws Exception {
        var project = projects.save(Project.builder().name("Portal de proyectos · Demostración").status(ProjectStatus.INITIATED).build());
        var first = task(project, "Planificación de entregas y coordinación con el equipo", 42);
        var second = task(project, "Validación funcional de la integración y revisión de observaciones", 75);
        task(project, "Esta tarea no está en la vista", 10);
        String request = "[{\"id\":" + second.getId() + ",\"level\":0},{\"id\":" + first.getId() + ",\"level\":1}]";
        byte[] bytes = mvc.perform(post("/api/tasks/export/view").with(owner()).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content(request)).andExpect(status().isOk())
            .andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .andReturn().getResponse().getContentAsByteArray();
        try (var book = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            var sheet = book.getSheet("Tareas");
            assertEquals(2, sheet.getLastRowNum());
            assertEquals(second.getTitle(), sheet.getRow(1).getCell(0).getStringCellValue());
            assertEquals(first.getTitle(), sheet.getRow(2).getCell(0).getStringCellValue());
            assertEquals(0.75, sheet.getRow(1).getCell(7).getNumericCellValue());
            assertEquals("0%", sheet.getRow(1).getCell(7).getCellStyle().getDataFormatString());
            assertTrue(DateUtil.isCellDateFormatted(sheet.getRow(1).getCell(5)));
            assertEquals(2, sheet.getRow(2).getCell(0).getCellStyle().getIndention());
            assertFalse(sheet.isDisplayGridlines());
            assertEquals(1, sheet.getPaneInformation().getHorizontalSplitPosition());
        }
        sample("tareas.xlsx", bytes);
        var ids = new ArrayList<Long>();
        for (int n = 0; n < 12; n++) ids.add(overtime.save(null, new OvertimeRequest(project.getId(), LocalDate.of(2026,10,20+n%8),
            n%2 == 0 ? "Sergio" : "Nicolás", n%2 == 0 ? "Validación del cierre mensual y revisión de observaciones con el equipo de certificación" : "Soporte de integración y seguimiento de entregas", new BigDecimal("2.25"), "Por confirmar", n%3 == 0)).id());
        sample("horas-extras.xlsx", overtime.exportExcel(ids));
        var incident = new Incident(); incident.setProject(project); incident.setTitle("Validar mensajes de error del formulario");
        incident.setDescription("Al guardar un formulario incompleto, se debe identificar cada campo pendiente y conservar la información ingresada.");
        incident.setReporter("Sergio"); incident.setDetectedEnvironment(IncidentEnvironment.CERT); incident.setPriority(TaskPriority.HIGH);
        incident.setDueDate(LocalDate.of(2026,11,15)); incidents.save(incident);
        sample("incidentes.xlsx", incidentExcel.export(project.getId()));
    }

    @Test void exportRequiresSessionCsrfAndValidSelection() throws Exception {
        mvc.perform(post("/api/tasks/export/view").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("[]"))
            .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/tasks/export/view").with(owner()).contentType(MediaType.APPLICATION_JSON).content("[]"))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/tasks/export/view").with(owner()).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("[]"))
            .andExpect(status().isBadRequest());
        mvc.perform(post("/api/tasks/export/view").with(owner()).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("[{\"id\":1,\"level\":-1}]"))
            .andExpect(status().isBadRequest());
    }

    private void sample(String name, byte[] bytes) throws IOException {
        String output = System.getProperty("export.samples");
        if (output != null) { Path folder = Path.of(output); Files.createDirectories(folder); Files.write(folder.resolve(name), bytes); }
    }
}
