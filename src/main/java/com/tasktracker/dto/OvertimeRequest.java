package com.tasktracker.dto;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.math.BigDecimal;
public record OvertimeRequest(@NotNull Long projectId,@NotNull LocalDate date,
 @NotBlank @Size(max=255) String person,@NotBlank @Size(max=5000) String topic,
 @NotNull @DecimalMin(value="0",inclusive=false) @DecimalMax("24") @Digits(integer=2,fraction=2) BigDecimal hours, @Size(max=255) String client,Boolean sentToManagement) {}
