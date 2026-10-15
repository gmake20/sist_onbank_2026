
package org.doit.onbank.loan.service;

import java.util.List;
import java.util.Set;

import org.doit.onbank.loan.dto.LoanProductForm;
import org.doit.onbank.loan.dto.LoanProductListDTO;
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

    private final LoanProductRepository loanProductRepository;

    private static final Set<String> CATEGORIES = Set.of(
            "credit",
            "mortgage",
            "jeonse",
            "auto",
            "group",
            "housing",
            "business"
    );
    
    // 1. 대출 상품 등록
    @Transactional
    public Long register(LoanProductForm form) {

        if (form.getCategory() == null ||
                !CATEGORIES.contains(form.getCategory())) {
            throw new IllegalArgumentException(
                    "유효하지 않은 상품 카테고리입니다."
            );
        }

        LoanProduct product = new LoanProduct();

        product.setCategory(form.getCategory());
        product.setName(form.getName());
        product.setSubtitle(form.getSubtitle());
        product.setTarget(form.getTarget());
        product.setTag(form.getTag());
        product.setLimit(form.getLimit());
        product.setMethod(form.getMethod());

        product.setAnnualInterestRate(
                form.getAnnualInterestRate()
        );
        product.setMaxTermMonths(
                form.getMaxTermMonths()
        );

        LoanProduct saved = loanProductRepository.save(product);

        return saved.getId();
    }

    // 2. 전체 상품 조회 및 상품명 검색
    @Transactional(readOnly = true)
    public List<LoanProduct> findProducts(String keyword) {

        if (keyword == null || keyword.isBlank()) {
            return loanProductRepository.findAll();
        }

        return loanProductRepository
                .findAll()
                .stream()
                .filter(p -> p.getName() != null
                        && p.getName().toLowerCase()
                            .contains(keyword.trim().toLowerCase()))
                .toList();
    }

    // 3. 카테고리별 상품 조회
    @Transactional(readOnly = true)
    public List<LoanProduct> findByCategory(String category) {

        validateCategory(category);

        return loanProductRepository
                .findByCategoryOrderByIdDesc(category);
    }

    // 4. 상품 상세 조회
    @Transactional(readOnly = true)
    public LoanProduct findById(Long id) {

        return loanProductRepository.findById(id)
                .orElseThrow(() ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "대출 상품을 찾을 수 없습니다."
                    )
                );
    }


    // 카테고리 유효성 검사
    public void validateCategory(String category) {

        if (!CATEGORIES.contains(category)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "존재하지 않는 대출 카테고리입니다."
            );
        }
    }

    // 카테고리별 상품명 검색
    public List<LoanProduct> search(
            String category,
            String keyword
    ) {

        validateCategory(category);

        if (keyword == null || keyword.isBlank()) {
            return loanProductRepository.findByCategoryOrderByIdDesc(category);
        }

        return loanProductRepository
                .findByCategoryAndNameContainingIgnoreCaseOrderByIdDesc(
                        category,
                        keyword.trim()
                );
    }

    // 카테고리별 JSON 응답
    public List<LoanProductListDTO> getProductList(
            String category
    ) {

        return findByCategory(category)
                .stream()
                .map(LoanProductListDTO::from)
                .toList();
    }

    // 상품 상세 조회 + 카테고리 확인
    public LoanProduct findDetail(
            String category,
            Long id
    ) {

        validateCategory(category);

        LoanProduct product = loanProductRepository.findById(id)
                .orElseThrow(() ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "존재하지 않는 대출 상품입니다."
                    )
                );

        if (!category.equals(product.getCategory())) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "해당 카테고리에 상품이 없습니다."
            );
        }

        return product;
    }
}
