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
 @GetMapping public List<OvertimeDTO> all() { return service.all(); }
 @PostMapping @ResponseStatus(HttpStatus.CREATED) public OvertimeDTO create(@Valid @RequestBody OvertimeRequest r) { return service.save(null,r); }
 @PutMapping("/{id}") public OvertimeDTO update(@PathVariable Long id,@Valid @RequestBody OvertimeRequest r) { return service.save(id,r); }
 @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@PathVariable Long id) { service.delete(id); }
}
