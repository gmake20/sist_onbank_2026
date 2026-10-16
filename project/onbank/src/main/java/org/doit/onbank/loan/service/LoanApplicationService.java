package org.doit.onbank.loan.service;

import java.math.BigDecimal;

import org.doit.onbank.loan.dto.LoanApplicationForm;
import org.doit.onbank.loan.entity.LoanApplication;
import org.doit.onbank.loan.entity.LoanProduct;
import org.doit.onbank.loan.repository.LoanApplicationRepository;
import org.doit.onbank.loan.repository.LoanProductRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class LoanApplicationService {

    private final LoanApplicationRepository applicationRepository;
    private final LoanProductRepository productRepository;

    @Transactional
    public Long apply(
            LoanApplicationForm form,
            String username
    ) {
        if (username == null || username.isBlank()
                || "anonymousUser".equals(username)) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "로그인이 필요합니다."
            );
        }

        if (!form.isAgreedToTerms()) {
            throw new IllegalArgumentException(
                    "필수 약관에 동의해야 합니다."
            );
        }

        LoanProduct product = productRepository.findById(
                form.getProductId()
        ).orElseThrow(() ->
            new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "대출 상품이 존재하지 않습니다."
            )
        );

        BigDecimal amount = form.getRequestedAmount();
        Integer months = form.getRequestedMonths();

        if (amount == null ||
                amount.compareTo(BigDecimal.valueOf(10000)) < 0 ||
                amount.scale() > 0) {
            throw new IllegalArgumentException(
                    "신청 금액을 확인하세요."
            );
        }

        if (months == null || months < 1 || months > 600) {
            throw new IllegalArgumentException(
                    "대출 기간을 확인하세요."
            );
        }

        if (product.getMaxTermMonths() != null
                && months > product.getMaxTermMonths()) {
            throw new IllegalArgumentException(
                    "상품의 최대 대출 기간을 초과했습니다."
            );
        }

        // 상품별 실제 최대한도는 별도의 숫자 컬럼으로 검증 필요
        LoanApplication application = new LoanApplication(
                product,
                username,
                amount,
                months
        );

        return applicationRepository.save(application).getId();
    }

    @Transactional(readOnly = true)
    public LoanApplication findMine(
            Long applicationId,
            String username
    ) {

        LoanApplication application = applicationRepository
                .findByIdAndApplicantUsername(
                        applicationId,
                        username
                )
                .orElseThrow(() ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "신청 내역을 찾을 수 없습니다."
                    )
                );

        System.out.println("===== 대출 신청 조회 =====");
        System.out.println("신청번호 = " + application.getId());
        System.out.println("신청금액 = " + application.getRequestedAmount());
        System.out.println("신청기간 = " + application.getRequestedMonths());

        LoanProduct product = application.getProduct();

        if (product == null) {
            throw new IllegalStateException(
                    "대출 신청에 연결된 상품 정보가 없습니다."
            );
        }

        System.out.println("상품 ID = " + product.getId());
        System.out.println("상품명 = " + product.getName());

        return application;
    }
}
