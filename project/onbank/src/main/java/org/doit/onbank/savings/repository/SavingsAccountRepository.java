package org.doit.onbank.savings.repository;

import java.util.List;
import java.util.Optional;

import org.doit.onbank.savings.entity.SavingsAccount;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface SavingsAccountRepository extends JpaRepository<SavingsAccount,Long>{
	
    @EntityGraph(attributePaths="product") List<SavingsAccount> findByMemberIdOrderByIdDesc(Long memberId);
    
    @EntityGraph(attributePaths="product") Optional<SavingsAccount> findByIdAndMemberId(Long id,Long memberId);
    
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select s from SavingsAccount s where s.id=:id") Optional<SavingsAccount> lockById(@Param("id") Long id);
    
    List<SavingsAccount> findByMemberId(Long memberId);
    
    Page<SavingsAccount> findByMemberId(
            Long memberId,
            Pageable pageable
    );
}
