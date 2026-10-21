package org.doit.onbank.admin.service;

import lombok.RequiredArgsConstructor;
import org.doit.onbank.admin.dto.ProductEditForm;
import org.doit.onbank.admin.dto.ProductForm;
import org.doit.onbank.admin.repository.SavingsProductSearchRepository;
import org.doit.onbank.savings.repository.SavingsProductRepository;
import org.doit.onbank.common.exception.BusinessException;
import org.doit.onbank.common.security.BackOfficeUser;
import org.doit.onbank.savings.entity.SavingsProduct;
import org.doit.onbank.savings.entity.SavingsType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

// S-10 상품 목록 (PRD-001), S-11 등록 (PRD-002), 수정·판매중지·다시 판매 (PRD-003) : 상품 변경 + admin_log 기록을 한 트랜잭션으로
// 저장은 팀원3 SavingsProductRepository, 조회·이름 검사는 우리 SavingsProductSearchRepository (팀원3 파일은 안 고침)
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminProductService {
    private final AdminLogService adminLogService;
    private final SavingsProductSearchRepository savingsProductSearchRepository;
    private final SavingsProductRepository savingsProductRepository;

    private static final int PAGE_SIZE = 20;

    // 팀원3 SavingsType 에 한글 이름이 없어서 여기서 붙인다
    public static final Map<String, String> TYPE_LABELS = Map.of(
            "FIXED_DEPOSIT", "정기예금",
            "INSTALLMENT_SAVINGS", "정기적금");

    // 빈 문자열 상품명은 null 로 바꿔 "전체"로 처리. 유형·판매 상태는 컨트롤러에서 이미 null 로 들어온다
    public Page<SavingsProduct> search(String name, SavingsType type, Boolean active, int page) {
        return savingsProductSearchRepository.search(blankToNull(name), type, active,
                PageRequest.of(Math.max(page, 0), PAGE_SIZE));
    }

    // 반환값 = 새 상품 ID
    @Transactional
    public Long createProduct(BackOfficeUser me, ProductForm form, String requestIp) {
        if (form.getMaxAmount() < form.getMinAmount()) {
            throw new IllegalArgumentException("최대 금액은 최소 금액 이상이어야 합니다.");
        }
        String name = form.getProductName().trim();
        if (savingsProductSearchRepository.existsByNameAndActiveTrue(name)) {
            throw new IllegalStateException("같은 이름의 판매중 상품이 있습니다.");
        }

        // 생성자가 active = true 로 만든다 → 등록하면 바로 판매중
        SavingsProduct product = savingsProductRepository.save(new SavingsProduct(
                name, SavingsType.valueOf(form.getProductType()), form.getAnnualRate(),
                form.getTermMonths(), form.getMinAmount(), form.getMaxAmount()));

        adminLogService.record(me, "PRODUCT_CREATE", "PRODUCT", String.valueOf(product.getId()),
                null, TYPE_LABELS.get(form.getProductType()) + " " + name, null, requestIp);
        return product.getId();
    }

    // 수정 화면·처리에서 쓰는 상품 하나. 없으면 BusinessException → 컨트롤러가 목록으로 돌려보낸다
    public SavingsProduct product(Long productId) {
        return savingsProductRepository.findById(productId)
                .orElseThrow(() -> new BusinessException("상품이 없습니다."));
    }

    // 상품명·연이율·금액만 바꾼다. 연이율은 가입 때 계좌에 복사돼서(SavingsAccount.contractRate) 기존 가입자는 그대로
    @Transactional
    public void updateProduct(BackOfficeUser me, Long productId, ProductEditForm form, String requestIp) {
        if (form.getMaxAmount() < form.getMinAmount()) {
            throw new IllegalArgumentException("최대 금액은 최소 금액 이상이어야 합니다.");
        }
        SavingsProduct product = product(productId);
        String name = form.getProductName().trim();
        // 이름을 바꿀 때만 검사. 새 이름이라 자기 자신은 걸리지 않는다
        if (!name.equals(product.getName()) && savingsProductSearchRepository.existsByNameAndActiveTrue(name)) {
            throw new IllegalStateException("같은 이름의 판매중 상품이 있습니다.");
        }
        String before = summary(product);
        product.changeTerms(name, form.getAnnualRate(), form.getMinAmount(), form.getMaxAmount());

        adminLogService.record(me, "PRODUCT_UPDATE", "PRODUCT", String.valueOf(productId),
                before, summary(product), null, requestIp);
    }

    // 판매중지 : 고객 목록·상세·가입에서 빠진다. 이미 가입한 계좌는 그대로
    @Transactional
    public void stop(BackOfficeUser me, Long productId, String reason, String requestIp) {
        String r = checkReason(reason);
        SavingsProduct product = product(productId);
        if (!product.isActive()) {                       // 다른 화면에서 먼저 바꾼 경우
            throw new BusinessException("이미 판매중지된 상품입니다.");
        }
        product.stopSale();
        adminLogService.record(me, "PRODUCT_STOP", "PRODUCT", String.valueOf(productId),
                "ON_SALE", "STOPPED", r, requestIp);
    }

    // 다시 판매 : 같은 이름의 판매중 상품이 있으면 막는다 (자기 자신은 판매중지 상태라 안 걸림)
    @Transactional
    public void resume(BackOfficeUser me, Long productId, String reason, String requestIp) {
        String r = checkReason(reason);
        SavingsProduct product = product(productId);
        if (product.isActive()) {
            throw new BusinessException("이미 판매중인 상품입니다.");
        }
        if (savingsProductSearchRepository.existsByNameAndActiveTrue(product.getName())) {
            throw new BusinessException("같은 이름의 판매중 상품이 있습니다.");
        }
        product.resumeSale();
        adminLogService.record(me, "PRODUCT_RESUME", "PRODUCT", String.valueOf(productId),
                "STOPPED", "ON_SALE", r, requestIp);
    }

    private String checkReason(String reason) {
        if (reason == null || reason.isBlank() || reason.trim().length() > 200) {   // admin_log.reason 200자
            throw new BusinessException("사유를 200자 이내로 입력하세요.");
        }
        return reason.trim();
    }

    // 수정 로그의 변경 전·후 값 (admin_log before/after 500자 안)
    private String summary(SavingsProduct p) {
        return p.getName() + " / " + p.getAnnualRate() + "% / " + p.getMinAmount() + "~" + p.getMaxAmount() + "원";
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
