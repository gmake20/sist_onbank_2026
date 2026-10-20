package org.doit.onbank.loan.repository;

import java.util.List;
import java.util.Optional;

import org.doit.onbank.loan.entity.LoanAutoRepayment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoanAutoRepaymentRepository
        extends JpaRepository<LoanAutoRepayment, Long> {

    Optional<LoanAutoRepayment> findByLoanAccountId(
            Long loanAccountId);

    List<LoanAutoRepayment> findByEnabledTrue();
}
