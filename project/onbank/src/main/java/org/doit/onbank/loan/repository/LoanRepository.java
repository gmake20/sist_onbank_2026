package org.doit.onbank.loan.repository;

import java.util.Optional;

import org.doit.onbank.loan.entity.Loan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface LoanRepository extends JpaRepository<Loan, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from Loan l where l.loanId = :id")
    Optional<Loan> findByIdForUpdate(@Param("id") Long id);
    Optional<Loan> findByLoanIdAndMemberId(Long loanId, Long memberId);
}
