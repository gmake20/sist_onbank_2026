package org.doit.onbank.loan.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;

import org.doit.onbank.loan.entity.LoanApplication;
import org.doit.onbank.loan.entity.LoanApplicationStatus;
import org.doit.onbank.loan.repository.LoanApplicationRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class LoanReviewService {

    private final LoanApplicationRepository applicationRepository;

    private LoanApplication findForUpdate(Long id) {
        return applicationRepository.findByIdForUpdate(id)
                .orElseThrow(() ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "대출 신청 정보가 없습니다."
                    )
                );
    }

    // 심사 시작
    @Transactional
    public void startReview(Long id) {
        LoanApplication application = findForUpdate(id);
        application.startReview();
    }

    // 추가 서류 요청
    @Transactional
    public void requestAdditionalInfo(
            Long id,
            String reason,
            Long reviewerId
    ) {
        LoanApplication application = findForUpdate(id);

        application.requestAdditionalInfo(reason, reviewerId);
    }

    // 서류보완 완료 확인 후 심사 재개
    @Transactional
    public void resumeReview(Long id) {
        LoanApplication application = findForUpdate(id);

        application.resumeReview();
    }

    // 대출 거절
    @Transactional
    public void reject(
            Long id,
            String reason,
            Long reviewerId
    ) {
        LoanApplication application = findForUpdate(id);

        application.reject(reason, reviewerId);
    }

    // 대출 승인
    @Transactional
    public void approve(
            Long id,
            BigDecimal amount,
            BigDecimal rate,
            String repaymentMethod,
            LocalDateTime startDate,
            Long reviewerId
    ) {

        if (reviewerId == null) {
            throw new IllegalArgumentException(
                    "심사 담당자 정보가 없습니다."
            );
        }

        if (repaymentMethod == null ||
                !Set.of("EQUAL", "PRINCIPAL", "BULLET")
                    .contains(repaymentMethod)) {
            throw new IllegalArgumentException(
                    "올바른 상환 방식을 선택하세요."
            );
        }

        LoanApplication application = findForUpdate(id);
        

		if (application.getStatus() != LoanApplicationStatus.REVIEWING) {
		    throw new IllegalStateException(
		            "심사중인 대출만 승인할 수 있습니다."
		    );
		}

        application.setRepaymentMethod(repaymentMethod);

        application.approve(
                amount,
                rate,
                reviewerId,
                startDate
        );
    }
}