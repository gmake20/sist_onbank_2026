package org.doit.onbank.loan.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;


public record LoanApproveRequest(
    @NotNull @Positive Long approvedAmount,
    @NotNull @FutureOrPresent LocalDate startedAt
) {}