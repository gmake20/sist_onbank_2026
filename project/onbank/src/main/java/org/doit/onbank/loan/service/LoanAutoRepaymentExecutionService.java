
package org.doit.onbank.loan.service;

import java.time.LocalDate;

import org.doit.onbank.loan.entity.LoanAutoRepayment;
import org.doit.onbank.loan.entity.LoanRepaymentSchedule;
import org.doit.onbank.loan.entity.LoanRepaymentStatus;
import org.doit.onbank.loan.repository.LoanAutoRepaymentRepository;
import org.doit.onbank.loan.repository.LoanRepaymentScheduleRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class LoanAutoRepaymentExecutionService {

    private final LoanAutoRepaymentRepository autoRepository;
    private final LoanRepaymentScheduleRepository scheduleRepository;
    private final LoanRepaymentService repaymentService;

    public void execute(Long autoRepaymentId, LocalDate today) {

        // 1. 자동상환 설정 조회
        LoanAutoRepayment setting = autoRepository
                .findById(autoRepaymentId)
                .orElseThrow(() ->
                    new IllegalArgumentException(
                        "자동상환 설정이 없습니다."));

        // 2. 자동상환 해지 여부
        if (!setting.isEnabled()) {
            return;
        }

        Long loanAccountId = setting.getLoanAccountId();
        Long memberId = setting.getMemberId();

        // 3. 오늘까지 납부해야 할 가장 오래된 회차 조회
        LoanRepaymentSchedule schedule = scheduleRepository
                .findFirstByLoanAccount_LoanAccountIdAndStatusAndDueDateLessThanEqualOrderByDueDateAscInstallmentNoAsc(
                    loanAccountId,
                    LoanRepaymentStatus.PENDING,
                    today
                )
                .orElse(null);

        if (schedule == null) {
            return;
        }

        // 4. 기존 대출 상환 서비스 호출
        repaymentService.repay(
                loanAccountId,
                schedule.getScheduleId(),
                memberId
        );
    }
}
