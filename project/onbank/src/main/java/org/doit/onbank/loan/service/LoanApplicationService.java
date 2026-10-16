
package org.doit.onbank.loan.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import org.doit.onbank.loan.dto.LoanApplicationForm;
import org.doit.onbank.loan.entity.LoanApplication;
import org.doit.onbank.loan.entity.LoanProduct;
import org.doit.onbank.loan.repository.LoanApplicationRepository;
import org.doit.onbank.loan.repository.LoanProductRepository;
import org.doit.onbank.member.entity.Member;
import org.doit.onbank.member.repository.MemberRepository;

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
    private final MemberRepository memberRepository;

    // ========================================
    // 1. 대출 신청
    // ========================================

    @Transactional
    public Long apply(
            LoanApplicationForm form,
            Long memberId) {

        // 로그인 회원 확인
        if (memberId == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "로그인이 필요합니다."
            );
        }

        if (form == null) {
            throw new IllegalArgumentException(
                    "대출 신청 정보가 없습니다."
            );
        }

        // 필수 약관 동의
        if (!form.isAgreedToTerms()) {
            throw new IllegalArgumentException(
                    "필수 약관에 동의해야 합니다."
            );
        }

        // 고객 조회
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() ->
                    new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            "회원 정보를 찾을 수 없습니다."
                    )
                );

        // 대출 상품 확인
        if (form.getProductId() == null) {
            throw new IllegalArgumentException(
                    "대출 상품을 선택하세요."
            );
        }

        LoanProduct product = productRepository
                .findById(form.getProductId())
                .orElseThrow(() ->
                    new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            "대출 상품이 존재하지 않습니다."
                    )
                );

        BigDecimal amount = form.getRequestedAmount();
        Integer months = form.getRequestedMonths();
        String repaymentMethod = form.getRepaymentMethod();

        // 신청 금액 검증
        if (amount == null
                || amount.compareTo(BigDecimal.valueOf(10000)) < 0
                || amount.scale() > 0) {
            throw new IllegalArgumentException(
                    "신청 금액을 확인하세요."
            );
        }

        // 대출 기간 검증
        if (months == null
                || months < 1
                || months > 600) {
            throw new IllegalArgumentException(
                    "대출 기간을 확인하세요."
            );
        }

        // 상품 최대 기간 검증
        if (product.getMaxTermMonths() != null
                && months > product.getMaxTermMonths()) {
            throw new IllegalArgumentException(
                    "상품의 최대 대출 기간을 초과했습니다."
            );
        }

        // 상환 방식 검증
        if (repaymentMethod == null
                || !Set.of(
                    "EQUAL",
                    "PRINCIPAL",
                    "BULLET"
                ).contains(repaymentMethod)) {

            throw new IllegalArgumentException(
                    "지원하지 않는 상환 방식입니다."
            );
        }

        // 대출 신청 생성
        LoanApplication application = new LoanApplication(
                member,
                product,
                member.getLoginId(),
                amount,
                months,
                repaymentMethod,
                form.isAgreedToTerms()
        );

        // 대출 신청 저장
        return applicationRepository.save(application).getId();
    }

    // ========================================
    // 2. 본인 대출 신청 상세 조회
    // ========================================

    @Transactional(readOnly = true)
    public LoanApplication findMine(
            Long applicationId,
            Long memberId) {

        if (memberId == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "로그인이 필요합니다."
            );
        }

        LoanApplication application = applicationRepository
                .findByIdAndMember_MemberId(
                        applicationId,
                        memberId
                )
                .orElseThrow(() ->
                    new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            "신청 내역을 찾을 수 없습니다."
                    )
                );

        if (application.getProduct() == null) {
            throw new IllegalStateException(
                    "대출 신청에 연결된 상품 정보가 없습니다."
            );
        }

        return application;
    }

    // ========================================
    // 3. 본인 대출 신청 목록 조회
    // ========================================

    @Transactional(readOnly = true)
    public List<LoanApplication> findMyApplications(
            Long memberId) {

        if (memberId == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "로그인이 필요합니다."
            );
        }

        return applicationRepository
                .findAllByMember_MemberIdOrderByCreatedAtDesc(
                        memberId
                );
    }
}
