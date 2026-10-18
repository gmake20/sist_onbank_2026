package org.doit.onbank.admin.service;

import lombok.RequiredArgsConstructor;
import org.doit.onbank.admin.dto.ProductForm;
import org.doit.onbank.common.security.BackOfficeUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// S-11 예적금 상품 등록 (PRD-002) : 상품 저장 + admin_log 기록을 한 트랜잭션으로
// product 테이블·ProductRepository 생기기 전까지 저장 부분은 주석으로 둔다
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminProductService {
    private final AdminLogService adminLogService;
    // ProductRepository가 생기면 주석 풀기
    // private final ProductRepository productRepository;

    @Transactional
    public void createProduct(BackOfficeUser me, ProductForm form, String requestIp) {
        // 중도해지 이율은 기본금리 이하 (명세서 2.8 예외 처리)
        if (form.getEarlyTerminationRate().compareTo(form.getBaseInterestRate()) > 0) {
            throw new IllegalArgumentException("중도해지 이율은 기본금리 이하여야 합니다.");
        }
        String name = form.getProductName().trim();

        // ProductRepository가 생기면 아래 주석 풀고, 맨 아래 throw 지우기
        // if (productRepository.existsByProductName(name)) {
        //     throw new IllegalStateException("같은 이름의 상품이 있습니다.");
        // }
        //
        // LocalDateTime now = LocalDateTime.now();      // Product에 자동 시간 채우기가 없어서 직접 넣는다
        // Product product = new Product();
        // product.setProductName(name);
        // product.setProductType(form.getProductType());
        // product.setBaseInterestRate(form.getBaseInterestRate());
        // product.setEarlyTerminationRate(form.getEarlyTerminationRate());
        // product.setTermMonths(form.getTermMonths().shortValue());   // TERM_MONTHS는 SMALLINT(Short)
        // product.setMinimumAmount(form.getMinimumAmount());
        // product.setPreferentialCondition(form.getPreferentialCondition());
        // product.setStatus(form.getStatus());
        // product.setCreatedAt(now);
        // product.setUpdatedAt(now);
        // productRepository.save(product);
        //
        // adminLogService.record(me, "PRODUCT_CREATE", "PRODUCT", String.valueOf(product.getProductId()),
        //         null, product.getProductType() + " " + name, null, requestIp);

        throw new UnsupportedOperationException("상품 저장 기능이 아직 연결되지 않았습니다.");
    }
}
