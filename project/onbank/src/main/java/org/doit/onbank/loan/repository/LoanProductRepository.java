package org.doit.onbank.loan.repository;

import java.util.List;

import org.doit.onbank.loan.entity.LoanProduct;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoanProductRepository
        extends JpaRepository<LoanProduct, Long> {

	List<LoanProduct> findByCategoryOrderByIdDesc(
            String category
    );

    // 카테고리 + 상품명 검색
    List<LoanProduct> findByCategoryAndNameContainingIgnoreCaseOrderByIdDesc(
            String category,
            String name
    );
}