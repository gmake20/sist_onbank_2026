package org.doit.onbank.savings.repository;

import java.util.*;
import org.doit.onbank.savings.entity.SavingsAccount;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface SavingsAccountRepository extends JpaRepository<SavingsAccount,Long>{
	
    @EntityGraph(attributePaths="product") List<SavingsAccount> findByMemberIdOrderByIdDesc(Long memberId);
    
    @EntityGraph(attributePaths="product") Optional<SavingsAccount> findByIdAndMemberId(Long id,Long memberId);
    
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select s from SavingsAccount s where s.id=:id") Optional<SavingsAccount> lockById(@Param("id") Long id);
}
