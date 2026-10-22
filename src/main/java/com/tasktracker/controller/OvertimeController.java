package com.tasktracker.controller;
import com.tasktracker.dto.*;
import com.tasktracker.service.OvertimeService;
import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import java.util.List;
@RestController @RequestMapping("/api/overtime") @RequiredArgsConstructor
public class OvertimeController {
 private final OvertimeService service;
 @PostMapping(value="/import/excel", consumes="multipart/form-data")
 public List<OvertimeDTO> importExcel(@RequestParam("file") org.springframework.web.multipart.MultipartFile file) {
  return service.replaceFromExcel(file);
 }
 @PostMapping("/export/excel")
 public org.springframework.http.ResponseEntity<byte[]> exportExcel(@RequestBody List<Long> ids) throws java.io.IOException {
  return org.springframework.http.ResponseEntity.ok()
   .header("Content-Disposition", "attachment; filename=horas-extras.xlsx")
   .contentType(org.springframework.http.MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
   .body(service.exportExcel(ids));
 }
 @GetMapping public List<OvertimeDTO> all() { return service.all(); }
 @PostMapping @ResponseStatus(HttpStatus.CREATED) public OvertimeDTO create(@Valid @RequestBody OvertimeRequest r) { return service.save(null,r); }
 @PutMapping("/{id}") public OvertimeDTO update(@PathVariable Long id,@Valid @RequestBody OvertimeRequest r) { return service.save(id,r); }
 @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@PathVariable Long id) { service.delete(id); }
}
