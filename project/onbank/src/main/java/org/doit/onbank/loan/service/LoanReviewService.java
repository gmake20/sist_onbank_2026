package org.doit.onbank.loan.service;

import java.util.Set;

import org.doit.onbank.loan.dto.LoanReviewApproveRequest;
import org.doit.onbank.loan.entity.LoanApplication;
import org.doit.onbank.loan.repository.LoanApplicationRepository;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class LoanReviewService {

    private final LoanApplicationRepository applicationRepository;

    // 관리자 심사 시작
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public void startReview(Long applicationId) {

        LoanApplication application =
                applicationRepository.findByIdForUpdate(applicationId)
                    .orElseThrow(() ->
                        new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            "대출 신청 내역이 없습니다."
                        )
                    );

        application.startReview();
    }

    // 관리자 대출 승인
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public void approve(
            Long applicationId,
            LoanReviewApproveRequest request,
            Long reviewerId
    ) {

        if (reviewerId == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "심사 담당 직원 정보가 없습니다."
            );
        }

        LoanApplication application =
                applicationRepository.findByIdForUpdate(applicationId)
                    .orElseThrow(() ->
                        new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            "대출 신청 내역이 없습니다."
                        )
                    );

        if (request.repaymentMethod() == null
                || !Set.of("EQUAL", "PRINCIPAL", "BULLET")
                        .contains(request.repaymentMethod())) {
            throw new IllegalArgumentException(
                    "올바른 상환 방식을 선택하세요."
            );
        }

        application.setRepaymentMethod(
                request.repaymentMethod()
        );

        application.approve(
                request.approvedAmount(),
                request.approvedInterestRate(),
                reviewerId,
                request.startDate()
        );

    }
}