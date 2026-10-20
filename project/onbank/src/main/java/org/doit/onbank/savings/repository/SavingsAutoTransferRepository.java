package org.doit.onbank.savings.repository;

import java.util.List;
import java.util.Optional;

import org.doit.onbank.savings.entity.SavingsAutoTransfer;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

public interface SavingsAutoTransferRepository extends JpaRepository<SavingsAutoTransfer, Long> {
    Optional<SavingsAutoTransfer> findByAccount_Id(Long accountId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from SavingsAutoTransfer a where a.account.id = :accountId")
    Optional<SavingsAutoTransfer> lockByAccountId(Long accountId);
    
    @Query("select a.account.id from SavingsAutoTransfer a where a.enabled = true")
    List<Long> findEnabledAccountIds();
    
    @EntityGraph(attributePaths = {"account", "account.product"})
    List<SavingsAutoTransfer> findByAccount_MemberIdOrderByIdDesc(
            Long memberId
    );
}
