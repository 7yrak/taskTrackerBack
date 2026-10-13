package com.tasktracker.dto;
import java.time.LocalDate;
import java.math.BigDecimal;
public record OvertimeDTO(Long id,Long projectId,String projectName,LocalDate date,String person,String topic,BigDecimal hours,String client) {}
