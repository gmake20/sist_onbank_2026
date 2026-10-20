package org.doit.onbank.savings.service;

import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.RequiredArgsConstructor;

import org.doit.onbank.account.entity.*;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OnbankAccountMoneyAdapter implements SavingsMoneyPort {

    @PersistenceContext
    private EntityManager em;

    private Account getAccount(Long id, Long memberId) {

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

        if (!AccountType.CHECKING.equals(a.getAccountType())) {
            throw new IllegalStateException(
                    "입출금계좌만 사용할 수 있습니다."
            );
        }

        if (!AccountStatus.ACTIVE.equals(a.getAccountStatus())) {
            throw new IllegalStateException(
                    "정상 계좌가 아닙니다."
            );
        }

        return a;
    }

    @Override
    public void debit(
            Long id,
            Long memberId,
            long amount) {

        if (amount <= 0) {
            throw new IllegalArgumentException(
                    "출금금액 오류"
            );
        }

        Account a = getAccount(id, memberId);

        a.withdraw(amount, LocalDateTime.now());

        // TODO: ONBANK 입출금 원장(AccountTransaction 등)에 출금 기록 추가
    }

    @Override
    public void credit(
            Long id,
            Long memberId,
            long amount) {

        if (amount <= 0) {
            throw new IllegalArgumentException(
                    "입금금액 오류"
            );
        }

        Account a = getAccount(id, memberId);

        a.deposit(amount, LocalDateTime.now());

        // TODO: ONBANK 입출금 원장(AccountTransaction 등)에 입금 기록 추가
    }
}