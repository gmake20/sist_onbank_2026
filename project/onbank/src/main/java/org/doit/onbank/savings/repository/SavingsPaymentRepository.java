package org.doit.onbank.savings.repository;

import java.util.*;
import org.doit.onbank.savings.entity.SavingsPayment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SavingsPaymentRepository extends JpaRepository<SavingsPayment,Long>{
    
	List<SavingsPayment> findByAccount_IdOrderByInstallmentNoAsc(Long id);
    
	boolean existsByAccount_IdAndInstallmentNo(Long id,int installmentNo);
}
