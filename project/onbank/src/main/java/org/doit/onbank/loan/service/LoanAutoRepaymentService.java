
package org.doit.onbank.loan.service;

import org.doit.onbank.loan.entity.LoanAccount;
import org.doit.onbank.loan.entity.LoanAccountStatus;
import org.doit.onbank.loan.entity.LoanAutoRepayment;
import org.doit.onbank.loan.repository.LoanAccountRepository;
import org.doit.onbank.loan.repository.LoanAutoRepaymentRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class LoanAutoRepaymentService {

    private final LoanAutoRepaymentRepository autoRepository;
    private final LoanAccountRepository loanAccountRepository;

    // 자동상환 등록
    @Transactional
    public void register(Long loanAccountId, Long memberId) {

        LoanAccount loan = loanAccountRepository.findById(loanAccountId)
                .orElseThrow(() ->
                    new IllegalArgumentException("대출 계좌가 없습니다."));

        if (!loan.getLoanApplication().getMember()
                .getMemberId().equals(memberId)) {
            throw new IllegalStateException(
                    "본인 대출 계좌만 등록할 수 있습니다.");
        }

        if (loan.getStatus() != LoanAccountStatus.ACTIVE) {
            throw new IllegalStateException(
                    "자동상환 등록이 불가능한 대출 상태입니다.");
        }

        if (loan.getLoanApplication().getDisbursementAccount() == null) {
            throw new IllegalStateException(
                    "자동상환 출금 계좌가 없습니다.");
        }

        autoRepository.findByLoanAccountId(loanAccountId)
                .ifPresentOrElse(
                    LoanAutoRepayment::enable,
                    () -> autoRepository.save(
                        new LoanAutoRepayment(loanAccountId, memberId))
                );
    }

    // 자동상환 해지
    @Transactional
    public void cancel(Long loanAccountId, Long memberId) {

        LoanAutoRepayment setting = autoRepository
                .findByLoanAccountId(loanAccountId)
                .orElseThrow(() ->
                    new IllegalArgumentException(
                        "자동상환 설정이 없습니다."));

        if (!setting.getMemberId().equals(memberId)) {
            throw new IllegalStateException("해지 권한이 없습니다.");
        }

        setting.disable();
    }
}
