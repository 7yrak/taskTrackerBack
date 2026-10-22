package com.tasktracker.service;

import com.tasktracker.dto.OvertimeRequest;
import com.tasktracker.model.*;
import com.tasktracker.repository.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import java.io.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:overtimeExcel;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=", "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect", "spring.jpa.show-sql=false"})
@AutoConfigureMockMvc
class OvertimeExcelApiTest {
    @Autowired MockMvc mvc;
    @Autowired OvertimeService service;
    @Autowired OvertimeRepository entries;
    @Autowired ProjectRepository projects;

    private Long seed() {
        entries.deleteAllInBatch();
        var project = projects.save(Project.builder().name("Excel " + UUID.randomUUID()).status(ProjectStatus.INITIATED).build());
        return service.save(null, new OvertimeRequest(project.getId(), LocalDate.of(2026,10,13), "Sergio", "Conciliación", new BigDecimal("2.25"), "Entel", true)).id();
    }
    private MockMultipartFile file(XSSFWorkbook book) throws Exception {
        var out = new ByteArrayOutputStream();
        book.write(out);
        return new MockMultipartFile("file", "horas.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", out.toByteArray());
    }
    @Test void exportedWorkbookReplacesAllEntriesAndImportsAppendedRowsAfterTotal() throws Exception {
        var oldId = seed();
        var original = service.all().get(0);
        try (var book = new XSSFWorkbook(new ByteArrayInputStream(service.exportExcel(List.of(oldId))))) {
            var sheet = book.getSheetAt(0);
            var row = sheet.createRow(sheet.getLastRowNum() + 1);
            row.createCell(0).setCellValue("14/10/2026");
            row.createCell(1).setCellValue("Nicolás");
            row.createCell(2).setCellValue("1,5");
            row.createCell(3).setCellValue("Soporte");
            row.createCell(4).setCellValue("Entel");
            row.createCell(5).setCellValue(original.projectName());
            row.createCell(6).setCellValue("No");
            // A record added after downloading must also disappear on replacement.
            service.save(null, new OvertimeRequest(original.projectId(), original.date(), "Eliminar", "Otro", BigDecimal.ONE, "Entel", false));
            mvc.perform(multipart("/api/overtime/import/excel").file(file(book)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        }
        assertFalse(entries.existsById(oldId));
        var restored = service.all().stream().filter(e -> e.person().equals("Sergio")).findFirst().orElseThrow();
        assertTrue(restored.sentToManagement());
        assertEquals(new BigDecimal("2.25"), restored.hours());
        assertEquals(2, entries.count());
    }
    @Test void invalidLaterRowPreservesAllPreviousEntries() throws Exception {
        var id = seed();
        try (var book = new XSSFWorkbook(new ByteArrayInputStream(service.exportExcel(List.of(id))))) {
            var row = book.getSheetAt(0).createRow(3);
            row.createCell(0).setCellValue("2026-02-30");
            row.createCell(1).setCellValue("Inválido");
            mvc.perform(multipart("/api/overtime/import/excel").file(file(book)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.startsWith("Fila 4:")));
        }
        assertTrue(entries.existsById(id));
        assertEquals(1, entries.count());
    }
    @Test void legacySixColumnWorkbookAcceptsExcelDateAndKeepsEveryDuplicateRow() throws Exception {
        var id = seed();
        try (var book = new XSSFWorkbook(new ByteArrayInputStream(service.exportExcel(List.of(id))))) {
            var sheet = book.getSheetAt(0);
            sheet.getRow(0).removeCell(sheet.getRow(0).getCell(6));
            sheet.getRow(1).removeCell(sheet.getRow(1).getCell(6));
            var style = book.createCellStyle();
            style.setDataFormat(book.createDataFormat().getFormat("dd/mm/yyyy"));
            sheet.getRow(1).getCell(0).setCellValue(LocalDate.of(2026,10,13));
            sheet.getRow(1).getCell(0).setCellStyle(style);
            var duplicate = sheet.createRow(3);
            for (int c = 0; c < 6; c++) {
                var source = sheet.getRow(1).getCell(c);
                if (c == 0) duplicate.createCell(c).setCellValue("2026-10-13");
                else if (c == 2) duplicate.createCell(c).setCellValue(2.25);
                else duplicate.createCell(c).setCellValue(source.getStringCellValue());
            }
            mvc.perform(multipart("/api/overtime/import/excel").file(file(book))).andExpect(status().isOk());
        }
        assertEquals(2, entries.count());
        assertTrue(service.all().stream().noneMatch(e -> e.sentToManagement()));
    }
    @Test void malformedWorkbookDoesNotDeleteExistingData() throws Exception {
        var id = seed();
        mvc.perform(multipart("/api/overtime/import/excel").file(new MockMultipartFile("file", "bad.xlsx", "application/octet-stream", new byte[]{1,2,3})))
            .andExpect(status().isBadRequest());
        assertTrue(entries.existsById(id));
    }
}
