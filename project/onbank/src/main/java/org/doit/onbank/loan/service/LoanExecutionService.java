package org.doit.onbank.loan.service;

import lombok.RequiredArgsConstructor;
import org.doit.onbank.loan.entity.LoanApplication;
import org.doit.onbank.loan.entity.LoanApplicationStatus;
import org.doit.onbank.loan.repository.LoanApplicationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LoanExecutionService {

    private final LoanApplicationRepository loanApplicationRepository;

    @Transactional(readOnly = true)
    public LoanApplication findApprovedApplication(Long applicationId) {

        // 1. 대출 신청 조회
        LoanApplication application = loanApplicationRepository
                .findById(applicationId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "존재하지 않는 대출 신청입니다."
                        )
                );

        // 2. 승인 상태 확인
        if (application.getStatus() != LoanApplicationStatus.APPROVED) {
            throw new IllegalStateException(
                    "승인된 대출만 실행할 수 있습니다."
            );
        }

        // 3. 승인 금액 확인
        if (application.getApprovedAmount() == null ||
                application.getApprovedAmount().signum() <= 0) {

            throw new IllegalStateException(
                    "승인 금액이 올바르지 않습니다."
            );
        }

        // 4. 대출금 수령 계좌 확인
//        if (application.getDisbursementAccountId() == null) {
//            throw new IllegalStateException(
//                    "대출금 수령 계좌가 지정되지 않았습니다."
//            );
//        }

        return application;
    }
}