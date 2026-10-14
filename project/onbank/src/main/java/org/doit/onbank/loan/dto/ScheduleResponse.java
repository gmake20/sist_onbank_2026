package org.doit.onbank.loan.dto;

import java.time.LocalDate;
public record ScheduleResponse(short installmentNo, LocalDate dueDate,
   long principalAmount, long interestAmount, long totalAmount, String status) {}
