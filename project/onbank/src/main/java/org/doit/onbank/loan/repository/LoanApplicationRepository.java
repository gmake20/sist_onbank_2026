package org.doit.onbank.loan.repository;

import java.util.Optional;

import org.doit.onbank.loan.entity.LoanApplication;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoanApplicationRepository
        extends JpaRepository<LoanApplication, Long> {

    Optional<LoanApplication> findByIdAndApplicantUsername(
            Long id,
            String applicantUsername
    );
}
