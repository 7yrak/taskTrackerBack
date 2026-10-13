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
 @Transactional(readOnly=true) public List<OvertimeDTO> all() { return entries.findAllByOrderByDateDescIdDesc().stream().map(this::dto).toList(); }
 public OvertimeDTO save(Long id,OvertimeRequest r) {
  OvertimeEntry e=id==null?new OvertimeEntry():entries.findById(id).orElseThrow(()->new NoSuchElementException("Registro no encontrado"));
  e.setProject(projects.findById(r.projectId()).orElseThrow(()->new IllegalArgumentException("Proyecto no encontrado")));
  e.setDate(r.date()); e.setPerson(r.person().trim()); e.setTopic(r.topic().trim()); e.setHours(r.hours()); e.setClient(r.client()==null || r.client().isBlank()?"Por confirmar":r.client().trim());
  return dto(entries.save(e));
 }
 public void delete(Long id) { entries.delete(entries.findById(id).orElseThrow(()->new NoSuchElementException("Registro no encontrado"))); }
 private OvertimeDTO dto(OvertimeEntry e) { return new OvertimeDTO(e.getId(),e.getProject().getId(),e.getProject().getName(),e.getDate(),e.getPerson(),e.getTopic(),e.getHours(),e.getClient()==null || e.getClient().isBlank()?"Por confirmar":e.getClient()); }
}
