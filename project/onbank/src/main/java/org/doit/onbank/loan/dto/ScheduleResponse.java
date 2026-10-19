
package org.doit.onbank.loan.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.doit.onbank.loan.entity.LoanRepaymentStatus;

public record ScheduleResponse(

        // 상환 회차
        Integer installmentNo,

        // 상환 예정일
        LocalDate dueDate,

        // 상환 원금
        BigDecimal principalAmount,

        // 상환 이자
        BigDecimal interestAmount,

        // 총 상환 금액
        BigDecimal totalAmount,

        // 상환 상태
        LoanRepaymentStatus status

) {
}
