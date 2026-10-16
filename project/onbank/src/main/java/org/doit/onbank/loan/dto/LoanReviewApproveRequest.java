package org.doit.onbank.loan.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record LoanReviewApproveRequest(

        @NotNull
        @Positive
        BigDecimal approvedAmount,

        @NotNull
        @DecimalMin("0.0")
        @DecimalMax("100.0")
        BigDecimal approvedInterestRate,

        @NotNull
        String repaymentMethod,

        @NotNull
        LocalDateTime startDate

) {
}