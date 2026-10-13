package com.tasktracker.controller;
import com.tasktracker.dto.*;
import com.tasktracker.model.IncidentAttachment;
import com.tasktracker.service.IncidentService;
import com.tasktracker.service.IncidentExcelService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
@RestController @RequestMapping("/api/incidents") @RequiredArgsConstructor
public class IncidentController {
 private final IncidentService service;
 private final IncidentExcelService excel;
 @GetMapping("/excel/template") public ResponseEntity<byte[]> template(@RequestParam Long projectId) throws IOException {
  return workbook(excel.template(projectId), "plantilla-incidentes.xlsx");
 }
 @GetMapping("/excel/export") public ResponseEntity<byte[]> export(@RequestParam Long projectId) throws IOException {
  return workbook(excel.export(projectId), "incidentes-proyecto-" + projectId + ".xlsx");
 }
 @PostMapping(value="/excel/preview", consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
 public IncidentImportPreview preview(@RequestParam MultipartFile file, @RequestParam Long projectId,
  @RequestParam(defaultValue="") String environment, @RequestParam(defaultValue="") String reporter) {
  return excel.preview(file,projectId,environment,reporter);
 }
 @PostMapping(value="/excel/import", consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
 public IncidentImportResult replace(@RequestParam MultipartFile file, @RequestParam Long projectId,
  @RequestParam(defaultValue="") String environment, @RequestParam(defaultValue="") String reporter,
  @RequestParam(defaultValue="false") boolean confirmed, @RequestParam String fingerprint) {
  return excel.replace(file,projectId,environment,reporter,confirmed,fingerprint);
 }
 private ResponseEntity<byte[]> workbook(byte[] data,String filename) {
  return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
   .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(filename).build().toString()).body(data);
 }
 @GetMapping public List<IncidentDTO> all() { return service.all(); }
 @GetMapping("/{id}") public IncidentDTO get(@PathVariable Long id) { return service.get(id); }
 @PostMapping @ResponseStatus(HttpStatus.CREATED) public IncidentDTO create(@Valid @RequestBody IncidentRequest r) { return service.create(r); }
 @PutMapping("/{id}") public IncidentDTO update(@PathVariable Long id,@Valid @RequestBody IncidentRequest r) { return service.update(id,r); }
 @PostMapping("/{id}/transitions") public IncidentDTO transition(@PathVariable Long id,@Valid @RequestBody IncidentTransitionRequest r) { return service.transition(id,r); }
 @PostMapping("/{id}/comments") public IncidentDTO comment(@PathVariable Long id,@Valid @RequestBody IncidentCommentRequest r) { return service.comment(id,r); }
 @PostMapping(value="/{id}/attachments",consumes=MediaType.MULTIPART_FORM_DATA_VALUE) public IncidentDTO upload(@PathVariable Long id,@RequestParam String category,@RequestParam MultipartFile file) throws IOException { return service.upload(id,category,file); }
 @GetMapping("/{id}/attachments/{attachmentId}") public ResponseEntity<byte[]> attachment(@PathVariable Long id,@PathVariable Long attachmentId) {
  IncidentAttachment a=service.attachment(id,attachmentId);
  return ResponseEntity.ok().contentType(MediaType.parseMediaType(a.getContentType()))
   .header("X-Content-Type-Options","nosniff")
   .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(a.getFilename(),StandardCharsets.UTF_8).build().toString())
   .body(a.getData());
 }
}
