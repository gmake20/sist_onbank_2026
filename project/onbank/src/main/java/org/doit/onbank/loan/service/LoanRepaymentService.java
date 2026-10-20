
package org.doit.onbank.loan.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.doit.onbank.account.entity.Account;
import org.doit.onbank.account.entity.AccountStatus;
import org.doit.onbank.account.entity.AccountTransaction;
import org.doit.onbank.account.entity.AccountTransactionType;
import org.doit.onbank.account.repository.AccountRepository;
import org.doit.onbank.account.repository.AccountTransactionRepository;
import org.doit.onbank.loan.entity.LoanAccount;
import org.doit.onbank.loan.entity.LoanApplication;
import org.doit.onbank.loan.entity.LoanRepaymentSchedule;
import org.doit.onbank.loan.entity.LoanRepaymentStatus;
import org.doit.onbank.loan.repository.LoanAccountRepository;
import org.doit.onbank.loan.repository.LoanRepaymentScheduleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class LoanRepaymentService {

    private final LoanAccountRepository loanAccountRepository;
    private final LoanRepaymentScheduleRepository scheduleRepository;
    private final AccountRepository accountRepository;
    private final AccountTransactionRepository transactionRepository;

    @Transactional
    public void repay(
            Long loanAccountId,
            Long scheduleId,
            Long memberId) {

        // =====================================
        // 1. 입력값 검증
        // =====================================

        if (loanAccountId == null
                || scheduleId == null
                || memberId == null) {
            throw new IllegalArgumentException(
                    "상환 요청 정보가 올바르지 않습니다.");
        }

        // =====================================
        // 2. 대출 계좌 잠금 조회
        // =====================================

        LoanAccount loanAccount = loanAccountRepository
                .findByIdForUpdate(loanAccountId)
                .orElseThrow(() ->
                    new IllegalArgumentException(
                        "대출 계좌가 존재하지 않습니다.")
                );

        // =====================================
        // 3. 신청자 본인 확인
        // =====================================

        LoanApplication application =
                loanAccount.getLoanApplication();

        if (!application.getMember().getMemberId()
                .equals(memberId)) {
            throw new IllegalStateException(
                    "본인의 대출만 상환할 수 있습니다.");
        }

        // =====================================
        // 4. 대출 상태 검증
        // =====================================

        loanAccount.validateActive();

        // =====================================
        // 5. 상환 회차 잠금 조회
        // =====================================

        LoanRepaymentSchedule schedule = scheduleRepository
                .findByScheduleIdAndLoanAccount_LoanAccountIdForUpdate(
                    scheduleId,
                    loanAccountId
                )
                .orElseThrow(() ->
                    new IllegalArgumentException(
                        "상환 일정이 존재하지 않습니다.")
                );

        // =====================================
        // 6. 상환 가능 상태 확인
        // =====================================

        if (schedule.getStatus()
                != LoanRepaymentStatus.PENDING) {
            throw new IllegalStateException(
                    "이미 납부했거나 상환할 수 없는 회차입니다.");
        }

        // =====================================
        // 7. 상환 금액 조회
        // =====================================

        BigDecimal principal =
                schedule.getPrincipalAmount();

        BigDecimal interest =
                schedule.getInterestAmount();

        BigDecimal total =
                schedule.getTotalAmount();

        if (principal == null
                || interest == null
                || total == null
                || principal.signum() < 0
                || interest.signum() < 0
                || total.signum() <= 0
                || principal.add(interest).compareTo(total) != 0) {
            throw new IllegalStateException(
                    "상환 금액이 올바르지 않습니다.");
        }

        // =====================================
        // 8. 출금 계좌 확인
        // =====================================

        Account selectedAccount =
                application.getDisbursementAccount();

        if (selectedAccount == null) {
            throw new IllegalStateException(
                    "상환 출금 계좌가 지정되지 않았습니다.");
        }

        Account account = accountRepository
                .findByIdForUpdate(selectedAccount.getAccountId())
                .orElseThrow(() ->
                    new IllegalArgumentException(
                        "출금 계좌가 존재하지 않습니다.")
                );

        if (!memberId.equals(account.getMemberId())) {
            throw new IllegalStateException(
                    "본인 명의의 계좌만 사용할 수 있습니다.");
        }
        
        if (!"CHECKING".equals( account.getAccountType() )
                || !"ACTIVE".equals(account.getAccountStatus())) {
            throw new IllegalStateException(
                    "상환금을 출금할 수 없는 계좌입니다.");
        }

//        if (account.getAccountType() != AccountType.CHECKING
//                || account.getAccountStatus()
//                    != AccountStatus.ACTIVE) {
//            throw new IllegalStateException(
//                    "상환금을 출금할 수 없는 계좌입니다.");
//        }

        // =====================================
        // 9. 실제 출금
        // =====================================

        long withdrawalAmount = total.longValueExact();

        LocalDateTime now = LocalDateTime.now();

        account.withdraw(withdrawalAmount, now);

        // =====================================
        // 10. 대출 원금 차감
        // =====================================

        if (principal.signum() > 0) {
            loanAccount.repayPrincipal(principal);
        }

        // =====================================
        // 11. 회차 납부 완료
        // =====================================

        schedule.markPaid();

        // =====================================
        // 12. 거래내역 저장
        // =====================================

        AccountTransaction transaction =
                AccountTransaction.builder()
                    .account(account)
                    .transactionType(
                        AccountTransactionType.LOAN_REPAYMENT)
                    .amount(total)
                    .balanceAfter(
                        BigDecimal.valueOf(account.getBalance()))
                    .loanAccountId(loanAccountId)
                    .description(
                        schedule.getInstallmentNo()
                            + "회차 대출금 상환")
                    .build();

        transactionRepository.save(transaction);
    }
}
