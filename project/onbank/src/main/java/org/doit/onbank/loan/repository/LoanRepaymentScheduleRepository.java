package org.doit.onbank.loan.repository;

import java.util.List;

import org.doit.onbank.loan.entity.LoanRepaymentSchedule;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoanRepaymentScheduleRepository extends JpaRepository<LoanRepaymentSchedule, Long> {
    boolean existsByLoan_LoanId(Long loanId);
    List<LoanRepaymentSchedule> findByLoan_LoanIdOrderByInstallmentNo(Long loanId);
}
