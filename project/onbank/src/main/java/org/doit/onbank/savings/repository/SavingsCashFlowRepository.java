package org.doit.onbank.savings.repository;

import org.doit.onbank.savings.entity.SavingsCashFlow;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SavingsCashFlowRepository extends JpaRepository<SavingsCashFlow,Long>{
    boolean existsByRequestKey(String key);
}
