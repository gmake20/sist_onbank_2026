package org.doit.onbank.savings.repository;
 
import org.doit.onbank.savings.entity.SavingsEarlyCloseSettlement;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SavingsEarlyCloseSettlementRepository
        extends JpaRepository<SavingsEarlyCloseSettlement, Long> {
	
    boolean existsByAccount_Id(Long accountId);
}
