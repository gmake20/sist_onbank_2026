package org.doit.onbank.savings.repository;

import java.util.List;
import org.doit.onbank.savings.entity.SavingsProduct;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SavingsProductRepository extends JpaRepository<SavingsProduct,Long>{
    
	List<SavingsProduct> findByActiveTrueOrderByIdDesc();
}
