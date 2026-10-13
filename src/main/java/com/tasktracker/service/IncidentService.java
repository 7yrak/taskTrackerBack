package com.tasktracker.service;
import com.tasktracker.dto.*;
import com.tasktracker.model.*;
import com.tasktracker.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.time.*;
import java.util.*;
import java.io.IOException;
import java.net.URI;

@Service @RequiredArgsConstructor @Transactional
public class IncidentService {
 private final IncidentRepository incidents;
 private final IncidentAttachmentRepository attachments;
 private final ProjectRepository projects;
 private final TaskRepository tasks;
 private final TeamMemberRepository members;
 private final IncidentWorkflow workflow;
 @Transactional(readOnly=true) public List<IncidentDTO> all() { return incidents.findAllByOrderByCreatedAtDesc().stream().map(this::dto).toList(); }
 @Transactional(readOnly=true) public IncidentDTO get(Long id) { return dto(find(id)); }
 private Incident find(Long id) { return incidents.findById(id).orElseThrow(() -> new NoSuchElementException("Incidente no encontrado")); }
 private Incident locked(Long id) {
  Incident snapshot=find(id);
  projects.findLockedById(snapshot.getProject().getId()).orElseThrow(() -> new IllegalArgumentException("Proyecto no encontrado"));
  return incidents.findLockedById(id).orElseThrow(() -> new NoSuchElementException("Incidente no encontrado"));
 }
 public IncidentDTO create(IncidentRequest r) {
  projects.findLockedById(r.projectId()).orElseThrow(() -> new IllegalArgumentException("Proyecto no encontrado"));
  Incident i = new Incident(); apply(i,r); event(i,"CREATED",null,i.getStatus(),i.getReporter(),"Incidente registrado",null);
  return dto(incidents.saveAndFlush(i));
 }
 public IncidentDTO update(Long id, IncidentRequest r) {
  if (r != null) java.util.stream.Stream.of(find(id).getProject().getId(), r.projectId()).distinct().sorted()
   .forEach(projectId -> projects.findLockedById(projectId).orElseThrow(() -> new IllegalArgumentException("Proyecto no encontrado")));
  Incident i=locked(id);
  if(i.getStatus()==IncidentStatus.CLOSED || i.getStatus()==IncidentStatus.CANCELLED) throw new IllegalArgumentException("El incidente está cerrado o cancelado");
  // An approved correction cannot be changed without returning through development.
  if(i.getStatus()!=IncidentStatus.REGISTERED && i.getStatus()!=IncidentStatus.DEVELOPMENT)
   throw new IllegalArgumentException("Solo se puede editar antes de enviar la corrección a CERT");
  if (!i.getProject().getId().equals(r.projectId()) && (i.getParent()!=null || incidents.existsByParentId(id)))
   throw new IllegalArgumentException("Para cambiar de proyecto un grupo, usa arrastrar y soltar");
  apply(i,r); event(i,"UPDATED",i.getStatus(),i.getStatus(),i.getReporter(),"Datos del incidente actualizados",null);
  return dto(incidents.saveAndFlush(i));
 }
 private TeamMember member(Long id) { return id==null ? null : members.findById(id).orElseThrow(() -> new IllegalArgumentException("Responsable no encontrado")); }
 private void apply(Incident i, IncidentRequest r) {
  Project p=projects.findById(r.projectId()).orElseThrow(() -> new IllegalArgumentException("Proyecto no encontrado"));
  Task t=r.taskId()==null ? null : tasks.findById(r.taskId()).orElseThrow(() -> new IllegalArgumentException("Tarea no encontrada"));
  if(t!=null && (t.getProject()==null || !p.getId().equals(t.getProject().getId()))) throw new IllegalArgumentException("La tarea debe pertenecer al proyecto del incidente");
  TeamMember dev=member(r.developerId()), qa=member(r.qaId());
  if(i.getStatus()==IncidentStatus.DEVELOPMENT && dev==null) throw new IllegalArgumentException("Debe seleccionar responsable de desarrollo");
  i.setTitle(r.title().trim()); i.setDescription(r.description().trim()); i.setReporter(r.reporter().trim());
  i.setDetectedEnvironment(r.detectedEnvironment()); i.setPriority(r.priority()); i.setProject(p); i.setTask(t);
  i.setDeveloper(dev); i.setQa(qa); i.setReproductionSteps(r.reproductionSteps()); i.setExpectedResult(r.expectedResult()); i.setActualResult(r.actualResult()); i.setDueDate(r.dueDate()); i.setReportedStatus(r.reportedStatus()); i.setQaDeliveryDate(r.qaDeliveryDate()); i.setImportNotes(r.importNotes()); i.setUpdatedAt(LocalDateTime.now());
 }
 public IncidentDTO transition(Long id, IncidentTransitionRequest r) {
  Incident i=locked(id); IncidentStatus from=i.getStatus(), to=workflow.next(from,r.action());
  String actor=r.actor();
  if(to==IncidentStatus.DEVELOPMENT && i.getDeveloper()==null) throw new IllegalArgumentException("Debe seleccionar responsable de desarrollo");
  if("START".equals(r.action()) || "SEND_CERT".equals(r.action())) {
   if(i.getDeveloper()==null) throw new IllegalArgumentException("Debe seleccionar responsable de desarrollo");
   actor=i.getDeveloper().getName();
  }
  if(to==IncidentStatus.CERT && i.getQa()==null) throw new IllegalArgumentException("Debe seleccionar responsable QA");
  if("APPROVE_QA".equals(r.action()) || ("REJECT".equals(r.action()) && from==IncidentStatus.CERT)) {
   if(i.getQa()==null) throw new IllegalArgumentException("Debe seleccionar responsable QA"); actor=i.getQa().getName();
  }
  if("SEND_UAT".equals(r.action())) actor=i.getQa().getName();
  requireText(actor,"Debe registrar el nombre del responsable");
  Set<String> withReason=Set.of("APPROVE_QA","APPROVE_CLIENT","REJECT","REOPEN","CANCEL","DEPLOY");
  if(withReason.contains(r.action())) requireText(r.observation(),"Debe registrar una observación o motivo");
  validateUrl(r.evidenceUrl());
  i.setStatus(to); i.setUpdatedAt(LocalDateTime.now()); i.setClosedAt(to==IncidentStatus.CLOSED ? LocalDateTime.now() : null);
  event(i,r.action(),from,to,actor.trim(),blank(r.observation()) ? "Cambio de etapa" : r.observation().trim(),r.evidenceUrl());
  return dto(incidents.saveAndFlush(i));
 }
 public IncidentDTO comment(Long id, IncidentCommentRequest r) {
  Incident i=locked(id); event(i,"COMMENT",i.getStatus(),i.getStatus(),r.author().trim(),r.text().trim(),null);
  i.setUpdatedAt(LocalDateTime.now()); return dto(incidents.saveAndFlush(i));
 }
 public IncidentDTO upload(Long id, String category, MultipartFile file) throws IOException {
  Incident i=locked(id);
  if(!Set.of("INCIDENT","CORRECTION").contains(category)) throw new IllegalArgumentException("Categoría de evidencia no válida");
  if(file.isEmpty() || file.getSize()>10*1024*1024) throw new IllegalArgumentException("El archivo debe contener datos y no superar 10 MB");
  byte[] data=file.getBytes(); String type=detectType(data);
  String name=Optional.ofNullable(file.getOriginalFilename()).orElse("evidencia").replaceAll("[\\\\/\\r\\n\\p{Cntrl}]","_");
  if(name.length()>200) name=name.substring(name.length()-200);
  String lower=name.toLowerCase(Locale.ROOT);
  boolean match=switch(type) { case "image/png" -> lower.endsWith(".png"); case "image/jpeg" -> lower.endsWith(".jpg") || lower.endsWith(".jpeg"); case "image/webp" -> lower.endsWith(".webp"); default -> lower.endsWith(".pdf"); };
  if(!match) throw new IllegalArgumentException("La extensión no corresponde al contenido del archivo");
  IncidentAttachment a=new IncidentAttachment(); a.setIncident(i); a.setCategory(category); a.setFilename(name); a.setContentType(type); a.setSize(data.length); a.setData(data); attachments.saveAndFlush(a);
  event(i,"ATTACHMENT",i.getStatus(),i.getStatus(),i.getReporter(),"Evidencia " + (category.equals("INCIDENT") ? "del incidente: " : "de corrección: ")+name,null);
  i.setUpdatedAt(LocalDateTime.now()); return dto(incidents.saveAndFlush(i));
 }
 @Transactional(readOnly=true) public IncidentAttachment attachment(Long id, Long attachmentId) {
  return attachments.findByIdAndIncidentId(attachmentId,id).orElseThrow(() -> new NoSuchElementException("Evidencia no encontrada"));
 }
 static String detectType(byte[] d) {
  if(d.length>=8 && Arrays.equals(Arrays.copyOf(d,8),new byte[]{(byte)137,80,78,71,13,10,26,10})) return "image/png";
  if(d.length>=3 && d[0]==(byte)255 && d[1]==(byte)216 && d[2]==(byte)255) return "image/jpeg";
  if(d.length>=12 && new String(d,0,4,java.nio.charset.StandardCharsets.US_ASCII).equals("RIFF") && new String(d,8,4,java.nio.charset.StandardCharsets.US_ASCII).equals("WEBP")) return "image/webp";
  if(d.length>=5 && new String(d,0,5,java.nio.charset.StandardCharsets.US_ASCII).equals("%PDF-")) return "application/pdf";
  throw new IllegalArgumentException("Solo se permiten archivos PNG, JPG, WebP o PDF");
 }
 private static boolean blank(String s) { return s==null || s.isBlank(); }
 private static void requireText(String s,String message) { if(blank(s)) throw new IllegalArgumentException(message); }
 private void validateUrl(String s) {
  if(blank(s)) return;
  try { URI u=URI.create(s); if(!Set.of("http","https").contains(u.getScheme()) || u.getHost()==null) throw new IllegalArgumentException(); }
  catch(IllegalArgumentException e) { throw new IllegalArgumentException("El enlace de evidencia debe ser una URL http o https válida"); }
 }
 private void event(Incident i,String type,IncidentStatus from,IncidentStatus to,String actor,String text,String url) {
  IncidentEvent e=new IncidentEvent(); e.setIncident(i); e.setType(type); e.setFromStatus(from); e.setToStatus(to); e.setActor(actor); e.setObservation(text); e.setEvidenceUrl(blank(url)?null:url); i.getEvents().add(e);
 }
 private IncidentDTO dto(Incident i) {
  return new IncidentDTO(i.getId(),i.getTitle(),i.getDescription(),i.getDetectedEnvironment(),i.getPriority(),i.getStatus(),i.getReporter(),i.getProject().getId(),i.getProject().getName(),i.getTask()==null?null:i.getTask().getId(),i.getTask()==null?null:i.getTask().getTitle(),i.getDeveloper()==null?null:i.getDeveloper().getId(),i.getDeveloper()==null?null:i.getDeveloper().getName(),i.getQa()==null?null:i.getQa().getId(),i.getQa()==null?null:i.getQa().getName(),i.getReproductionSteps(),i.getExpectedResult(),i.getActualResult(),i.getDueDate(),i.getCreatedAt(),i.getUpdatedAt(),i.getClosedAt(),i.getEvents().stream().map(e -> new IncidentDTO.Event(e.getId(),e.getType(),e.getFromStatus(),e.getToStatus(),e.getActor(),e.getObservation(),e.getEvidenceUrl(),e.getCreatedAt())).toList(),attachments.findMetadataByIncidentId(i.getId()).stream().map(a -> new IncidentDTO.Attachment(a.getId(),a.getCategory(),a.getFilename(),a.getContentType(),a.getSize(),a.getCreatedAt())).toList(),i.getReportedStatus(),i.getQaDeliveryDate(),i.getImportNotes(),i.getParent()==null?null:i.getParent().getId());
 }
}
