package com.tasktracker.service;
import com.tasktracker.model.*;
import org.springframework.stereotype.Component;
@Component
public class IncidentWorkflow {
 public IncidentStatus next(IncidentStatus status, String action) {
  if ("CANCEL".equals(action) && status != IncidentStatus.CLOSED && status != IncidentStatus.CANCELLED) return IncidentStatus.CANCELLED;
  if ("REOPEN".equals(action) && status == IncidentStatus.CLOSED) return IncidentStatus.DEVELOPMENT;
  if ("REJECT".equals(action) && (status == IncidentStatus.CERT || status == IncidentStatus.UAT)) return IncidentStatus.DEVELOPMENT;
  return switch (action) {
   case "START" -> require(status, IncidentStatus.REGISTERED, IncidentStatus.DEVELOPMENT);
   case "SEND_CERT" -> require(status, IncidentStatus.DEVELOPMENT, IncidentStatus.CERT);
   case "APPROVE_QA" -> require(status, IncidentStatus.CERT, IncidentStatus.QA_APPROVED);
   case "SEND_UAT" -> require(status, IncidentStatus.QA_APPROVED, IncidentStatus.UAT);
   case "APPROVE_CLIENT" -> require(status, IncidentStatus.UAT, IncidentStatus.CLIENT_APPROVED);
   case "DEPLOY" -> require(status, IncidentStatus.CLIENT_APPROVED, IncidentStatus.CLOSED);
   default -> throw new IllegalArgumentException("Acción no permitida para el estado actual");
  };
 }
 private IncidentStatus require(IncidentStatus current, IncidentStatus expected, IncidentStatus next) {
  if (current != expected) throw new IllegalArgumentException("Debe completar las etapas y aprobaciones previas");
  return next;
 }
}
