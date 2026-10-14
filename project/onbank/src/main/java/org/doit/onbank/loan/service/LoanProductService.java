package org.doit.onbank.loan.service;

import java.util.List;

import org.doit.onbank.loan.dto.LoanProductForm;
import org.doit.onbank.loan.entity.LoanProduct;
import org.doit.onbank.loan.repository.LoanProductRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LoanProductService {

    private final LoanProductRepository repository;

    // 전체 목록 및 검색
    public List<LoanProduct> findProducts(String keyword) {

        if (keyword == null || keyword.isBlank()) {
            return repository.findAllByOrderByIdDesc();
        }

        return repository
                .findByNameContainingIgnoreCaseOrderByIdDesc(
                        keyword.trim()
                );
    }

    // 상품 상세 조회
    public LoanProduct findById(Long id) {

        return repository.findById(id)
                .orElseThrow(() ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "대출 상품을 찾을 수 없습니다."
                    )
                );
    }

    // 상품 등록
    @Transactional
    public Long register(LoanProductForm form) {

        LoanProduct product = new LoanProduct();

        product.setName(form.getName());
        product.setSubtitle(form.getSubtitle());
        product.setTarget(form.getTarget());
        product.setLimit(form.getLimit());
        product.setMethod(form.getMethod());
        product.setTag(form.getTag());
        product.setAnnualInterestRate(
                form.getAnnualInterestRate()
        );
        product.setMaxTermMonths(form.getMaxTermMonths());

        LoanProduct saved = repository.save(product);

        return saved.getId();
    }
}