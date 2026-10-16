
package org.doit.onbank.loan.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public record LoanReviewApproveRequest(

        @NotNull
        @DecimalMin("1")
        BigDecimal approvedAmount,

        @NotNull
        @DecimalMin("0")
        @DecimalMax("100")
        BigDecimal approvedInterestRate,

        @NotNull
        LocalDateTime startDate

) {
}
