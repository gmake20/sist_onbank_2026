package org.doit.onbank.loan.repository;

import java.util.List;

import org.doit.onbank.loan.entity.LoanRepaymentSchedule;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoanRepaymentScheduleRepository extends JpaRepository<LoanRepaymentSchedule, Long> {

    // 해당 대출 신청의 상환 스케줄 존재 여부
    boolean existsByLoanApplication_Id(Long applicationId);

    // 해당 대출 신청의 상환 스케줄 회차순 조회
    List<LoanRepaymentSchedule>
        findByLoanApplication_IdOrderByInstallmentNo(
            Long applicationId
        );
}