package org.doit.onbank.loan.repository;

import java.util.List;

import org.doit.onbank.loan.entity.LoanProduct;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoanProductRepository
        extends JpaRepository<LoanProduct, Long> {

    // 전체 상품 최신순 조회
    List<LoanProduct> findAllByOrderByIdDesc();

    // 상품명 검색
    List<LoanProduct> findByNameContainingIgnoreCaseOrderByIdDesc(
            String keyword
    );
}