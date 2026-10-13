package com.tasktracker.service;

import com.tasktracker.dto.HierarchyMoveRequest;
import com.tasktracker.model.*;
import com.tasktracker.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;

@Service @RequiredArgsConstructor @Transactional
public class RowHierarchyService {
 private final TaskRepository tasks;
 private final IncidentRepository incidents;
 private final ProjectRepository projects;
 private final ProjectService projectService;

 private void lockProjects() {
  projects.findAll().stream().map(Project::getId).sorted().forEach(id -> projects.findLockedById(id));
 }
 private Project project(Long id) {
  return id==null ? null : projects.findById(id).orElseThrow(() -> new IllegalArgumentException("Proyecto no encontrado"));
 }
 private <T> Set<Long> subtree(Long id, List<T> rows, Function<T,Long> key, Function<T,Long> parent) {
  Set<Long> selected=new HashSet<>(); selected.add(id);
  boolean changed;
  do { changed=false; for(T row:rows) if(selected.contains(parent.apply(row))) changed |= selected.add(key.apply(row)); } while(changed);
  return selected;
 }
 public void moveTask(Long id, HierarchyMoveRequest request) {
  lockProjects(); List<Task> rows=tasks.findAllLocked();
  Task row=rows.stream().filter(t -> t.getId().equals(id)).findFirst().orElseThrow(() -> new NoSuchElementException("Tarea no encontrada"));
  Set<Long> group=subtree(id,rows,Task::getId,t -> t.getParent()==null?null:t.getParent().getId());
  Task parent=request.parentId()==null?null:rows.stream().filter(t -> t.getId().equals(request.parentId())).findFirst().orElseThrow(() -> new IllegalArgumentException("Tarea padre no encontrada"));
  if(parent!=null && group.contains(parent.getId())) throw new IllegalArgumentException("Primero deja la hija sin padre para invertir la relación. No se permiten ciclos.");
  Project destination=parent==null?project(request.projectId()):parent.getProject();
  Set<Long> affected=new HashSet<>();
  for(Task t:rows) if(group.contains(t.getId())) {
   if(t.getProject()!=null) affected.add(t.getProject().getId());
   t.setProject(destination);
  }
  row.setParent(parent); tasks.flush();
  // A task link is only valid inside the incident's own project.
  for(Incident i:incidents.findAllLocked()) if(i.getTask()!=null && group.contains(i.getTask().getId()) &&
    (destination==null || !i.getProject().getId().equals(destination.getId()))) {
   i.setTask(null); history(i,"Vínculo a tarea retirado al mover la tarea a otro proyecto");
  }
  if(destination!=null) affected.add(destination.getId());
  affected.forEach(projectService::syncStatusFromTasks);
 }
 public void moveIncident(Long id, HierarchyMoveRequest request) {
  lockProjects(); List<Incident> rows=incidents.findAllLocked();
  Incident row=rows.stream().filter(i -> i.getId().equals(id)).findFirst().orElseThrow(() -> new NoSuchElementException("Incidente no encontrado"));
  Set<Long> group=subtree(id,rows,Incident::getId,i -> i.getParent()==null?null:i.getParent().getId());
  Incident parent=request.parentId()==null?null:rows.stream().filter(i -> i.getId().equals(request.parentId())).findFirst().orElseThrow(() -> new IllegalArgumentException("Incidente padre no encontrado"));
  if(parent!=null && group.contains(parent.getId())) throw new IllegalArgumentException("Primero deja la hija sin padre para invertir la relación. No se permiten ciclos.");
  Project destination=parent==null?project(request.projectId()):parent.getProject();
  if(destination==null) throw new IllegalArgumentException("El incidente debe pertenecer a un proyecto");
  for(Incident i:rows) if(group.contains(i.getId())) {
   boolean moved=!i.getProject().getId().equals(destination.getId());
   i.setProject(destination);
   if(i.getTask()!=null && (i.getTask().getProject()==null || !i.getTask().getProject().getId().equals(destination.getId()))) i.setTask(null);
   if(moved || i==row) history(i,"Organización: proyecto " + destination.getName() + (i==row ? (parent==null?", sin padre":", padre #"+parent.getId()):"; movido con su padre"));
  }
  row.setParent(parent);
  // Organizing rows never changes workflow states or certifications.
 }
 private void history(Incident i,String observation) {
  IncidentEvent event=new IncidentEvent(); event.setIncident(i); event.setType("HIERARCHY");
  event.setFromStatus(i.getStatus()); event.setToStatus(i.getStatus()); event.setActor("Organización de tabla"); event.setObservation(observation);
  i.getEvents().add(event); i.setUpdatedAt(LocalDateTime.now());
 }
}
