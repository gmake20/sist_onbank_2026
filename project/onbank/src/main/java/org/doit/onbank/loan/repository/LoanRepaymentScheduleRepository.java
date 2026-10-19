
package org.doit.onbank.loan.repository;

import java.util.List;

import org.doit.onbank.loan.entity.LoanRepaymentSchedule;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoanRepaymentScheduleRepository
        extends JpaRepository<LoanRepaymentSchedule, Long> {

    // 대출 계좌별 상환 스케줄 존재 확인
    boolean existsByLoanAccount_LoanAccountId(
            Long loanAccountId);

    // 대출 신청 ID로 상환 스케줄 조회
    List<LoanRepaymentSchedule>
        findByLoanAccount_LoanApplication_IdOrderByInstallmentNoAsc(
                Long applicationId);

    // 대출 계좌 ID로 상환 스케줄 조회
    List<LoanRepaymentSchedule>
        findByLoanAccount_LoanAccountIdOrderByInstallmentNoAsc(
                Long loanAccountId);
}
