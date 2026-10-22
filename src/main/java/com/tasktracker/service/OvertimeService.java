package com.tasktracker.service;
import com.tasktracker.dto.*;
import com.tasktracker.model.OvertimeEntry;
import com.tasktracker.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
@Service @RequiredArgsConstructor @Transactional
public class OvertimeService {
 private final OvertimeRepository entries;
 private final ProjectRepository projects;
 private final OvertimeExcelReader excelReader;
 public List<OvertimeDTO> replaceFromExcel(org.springframework.web.multipart.MultipartFile file) {
  // Read and validate the complete workbook before replacing anything. The transaction
  // also restores the previous entries if any database write fails.
  var requests = excelReader.read(file);
  entries.deleteAllInBatch();
  for (var request : requests) save(null, request);
  entries.flush();
  return all();
 }
 @Transactional(readOnly=true) public List<OvertimeDTO> all() { return entries.findAllByOrderByDateDescIdDesc().stream().map(this::dto).toList(); }
 public OvertimeDTO save(Long id,OvertimeRequest r) {
  OvertimeEntry e=id==null?new OvertimeEntry():entries.findById(id).orElseThrow(()->new NoSuchElementException("Registro no encontrado"));
  e.setProject(projects.findById(r.projectId()).orElseThrow(()->new IllegalArgumentException("Proyecto no encontrado")));
  e.setDate(r.date()); e.setPerson(r.person().trim()); e.setTopic(r.topic().trim()); e.setHours(r.hours()); e.setClient(r.client()==null || r.client().isBlank()?"Por confirmar":r.client().trim());
  if(r.sentToManagement()!=null) e.setSentToManagement(r.sentToManagement());
  return dto(entries.save(e));
 }
 public void delete(Long id) { entries.delete(entries.findById(id).orElseThrow(()->new NoSuchElementException("Registro no encontrado"))); }
 private OvertimeDTO dto(OvertimeEntry e) { return new OvertimeDTO(e.getId(),e.getProject().getId(),e.getProject().getName(),e.getDate(),e.getPerson(),e.getTopic(),e.getHours(),e.getClient()==null || e.getClient().isBlank()?"Por confirmar":e.getClient(),e.isSentToManagement()); }
 @Transactional(readOnly=true)
 public byte[] exportExcel(List<Long> ids) throws java.io.IOException {
  Set<Long> selected = new HashSet<>(ids);
  List<OvertimeDTO> rows = all().stream().filter(e -> selected.contains(e.id())).toList();
  try (var book = new org.apache.poi.xssf.usermodel.XSSFWorkbook(); var output = new java.io.ByteArrayOutputStream()) {
   var sheet = book.createSheet("Horas extras");
   String[] headers = {"Fecha", "Persona", "Horas", "Tema/Motivo", "Proyecto", "Enviado a jefatura"};
   var header = sheet.createRow(0);
   var headerStyle = book.createCellStyle();
   var font = book.createFont(); font.setBold(true); headerStyle.setFont(font);
   for(int c=0;c<headers.length;c++) { var cell=header.createCell(c); cell.setCellValue(headers[c]); cell.setCellStyle(headerStyle); }
   int index=1; double total=0;
   for(var entry:rows) {
    var row=sheet.createRow(index++);
    row.createCell(0).setCellValue(entry.date().toString());
    row.createCell(1).setCellValue(entry.person());
    row.createCell(2).setCellValue(entry.hours().doubleValue());
    row.createCell(3).setCellValue(entry.topic());
    row.createCell(4).setCellValue(entry.projectName());
    row.createCell(5).setCellValue(entry.sentToManagement() ? "Sí" : "No");
    total+=entry.hours().doubleValue();
   }
   var footer=sheet.createRow(index); footer.createCell(1).setCellValue("Total horas"); footer.createCell(2).setCellValue(Math.round(total*100)/100.0);
   sheet.createFreezePane(0,1);
   sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0, Math.max(0,index-1),0,5));
   for(int c=0;c<headers.length;c++) { sheet.autoSizeColumn(c); sheet.setColumnWidth(c,Math.min(15000,sheet.getColumnWidth(c)+512)); }
   book.write(output); return output.toByteArray();
  }
 }
}
