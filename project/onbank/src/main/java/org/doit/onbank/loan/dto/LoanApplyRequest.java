package org.doit.onbank.loan.dto;

import java.math.BigDecimal;


import jakarta.validation.constraints.*;

public record LoanApplyRequest(
    @NotBlank String loanType,
    @NotNull @Min(10000) @Max(1000000000000L) Long requestedAmount,
    @NotNull @DecimalMin("0.0") @DecimalMax("100.0") @Digits(integer=3,fraction=4) BigDecimal annualInterestRate,
    @NotNull @Min(1) @Max(600) Integer termMonths,
    @NotBlank String repaymentMethod,
    Long repaymentAccountId,
    String employmentStatus,
    @PositiveOrZero Long annualIncome
) {}