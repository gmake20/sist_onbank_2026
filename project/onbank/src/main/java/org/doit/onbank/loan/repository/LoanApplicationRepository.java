package org.doit.onbank.loan.repository;

import java.util.List;
import java.util.Optional;

import org.doit.onbank.loan.entity.LoanApplication;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface LoanApplicationRepository
        extends JpaRepository<LoanApplication, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT a
        FROM LoanApplication a
        WHERE a.id = :applicationId
    """)
    Optional<LoanApplication> findByIdForUpdate(
            @Param("applicationId") Long applicationId
    );

    @EntityGraph(attributePaths = {"product"})
    Optional<LoanApplication> findByIdAndMember_MemberId(
            Long id,
            Long memberId
    );
    
    @EntityGraph(attributePaths = {"product"})
    List<LoanApplication> findAllByMember_MemberIdOrderByCreatedAtDesc(
            Long memberId
    );
    
    @EntityGraph(attributePaths = {"product", "member"})
    List<LoanApplication> findAllByOrderByCreatedAtDesc();
 
}
