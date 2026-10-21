
package org.doit.onbank.savings.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.doit.onbank.account.entity.Account;
import org.doit.onbank.account.entity.AccountStatus;
import org.doit.onbank.account.entity.AccountTransaction;
import org.doit.onbank.account.entity.AccountTransactionType;
import org.doit.onbank.account.service.TransactionHistoryService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class OnbankAccountMoneyAdapter implements SavingsMoneyPort {

    @PersistenceContext
    private EntityManager em;
    
    private final TransactionHistoryService transactionHistoryService;

    private Account getAccount(Long id, Long memberId) {

        if (id == null || memberId == null) {
            throw new IllegalArgumentException("계좌 정보가 올바르지 않습니다.");
        }

        Account a = em.find(
                Account.class,
                id,
                LockModeType.PESSIMISTIC_WRITE
        );

        if (a == null || !memberId.equals(a.getMemberId())) {
            throw new IllegalArgumentException(
                    "본인 명의의 입출금계좌가 아닙니다."
            );
        }

        // AccountType이 enum인 경우
//        if (a.getAccountType() != AccountType.CHECKING) {
//            throw new IllegalStateException(
//                    "입출금계좌만 사용할 수 있습니다."
//            );
//        }

        // AccountStatus가 문자열 상수인 경우
        if (a.getAccountStatus() != AccountStatus.ACTIVE) {
            throw new IllegalStateException(
                    "정상 계좌가 아닙니다."
            );
        }

        return a;
    }

//    @Override
//    @Transactional(propagation = Propagation.MANDATORY)
//    public void debit(Long id, Long memberId, long amount) {
//
//        if (amount <= 0) {
//            throw new IllegalArgumentException("출금금액 오류");
//        }
//
//        Account account = getAccount(id, memberId);
//
//        LocalDateTime now = LocalDateTime.now();
//
//        // 1. 입출금계좌 출금
//        account.withdraw(amount, now);
//
//        // 2. 출금 후 잔액
//        BigDecimal balanceAfter =
//                BigDecimal.valueOf(account.getBalance());
//
//        // 3. 거래내역 생성
//        AccountTransaction transaction =
//                AccountTransaction.builder()
//                        .account(account)
//                        .transactionType(
//                                AccountTransactionType.SAVINGS_PAYMENT
//                        )
//                        .amount(BigDecimal.valueOf(amount))
//                        .balanceAfter(balanceAfter)
//                        .loanAccountId(null)
//                        .description("ONBANK 적금 납입")
//                        .build();
//
//        // 4. 거래내역 저장
//        em.persist(transaction);
//    }
    
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void debit(Long id, Long memberId, long amount) {

        if (amount <= 0) {
            throw new IllegalArgumentException("출금 금액 오류");
        }

        Account account = getAccount(id, memberId);
        LocalDateTime now = LocalDateTime.now();

        // 1. 입출금계좌 출금
        account.withdraw(amount, now);

        // 2. 거래 원장 기록
        transactionHistoryService.record(
                account,
                "WITHDRAWAL",
                amount,
                "ONBANK 적금 납입",
                now
        );
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void credit(Long id, Long memberId, long amount) {

        if (amount <= 0) {
            throw new IllegalArgumentException("입금 금액 오류");
        }

        Account account = getAccount(id, memberId);
        LocalDateTime now = LocalDateTime.now();

        account.deposit(amount, now);

        transactionHistoryService.record(
                account,
                "DEPOSIT",
                amount,
                "ONBANK 예적금 만기 지급",
                now
        );
    }

//    @Override
//    @Transactional(propagation = Propagation.MANDATORY)
//    public void credit(Long id, Long memberId, long amount) {
//
//        if (amount <= 0) {
//            throw new IllegalArgumentException("입금금액 오류");
//        }
//
//        Account account = getAccount(id, memberId);
//
//        LocalDateTime now = LocalDateTime.now();
//
//        // 1. 입출금계좌 입금
//        account.deposit(amount, now);
//
//        // 2. 입금 후 잔액
//        BigDecimal balanceAfter =
//                BigDecimal.valueOf(account.getBalance());
//
//        // 3. 거래내역 생성
//        AccountTransaction transaction =
//                AccountTransaction.builder()
//                        .account(account)
//                        .transactionType(
//                                AccountTransactionType.SAVINGS_MATURITY
//                        )
//                        .amount(BigDecimal.valueOf(amount))
//                        .balanceAfter(balanceAfter)
//                        .loanAccountId(null)
//                        .description("ONBANK 예적금 만기 지급")
//                        .build();
//
//        // 4. 거래내역 저장
//        em.persist(transaction);
//    }
    
    
}
