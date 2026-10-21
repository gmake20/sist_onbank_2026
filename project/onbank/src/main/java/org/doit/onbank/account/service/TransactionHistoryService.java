
package org.doit.onbank.account.service;

import java.time.LocalDateTime;

import org.doit.onbank.account.entity.Account;
import org.doit.onbank.account.entity.TransactionHistory;
import org.doit.onbank.account.entity.TransactionStatus;
import org.doit.onbank.account.repository.TransactionHistoryRepository;

// TransactionSqnoGenerator는 기존 프로젝트의 실제 패키지에서 import

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TransactionHistoryService {

    private final TransactionHistoryRepository transactionHistoryRepository;
    private final TransactionSqnoGenerator transactionSqnoGenerator;

    @Transactional(propagation = Propagation.MANDATORY)
    public TransactionHistory record(
            Account account,
            String transactionType,
            long amount,
            String content,
            LocalDateTime now) {

        if (account == null || amount <= 0 || now == null) {
            throw new IllegalArgumentException(
                    "거래 기록 정보가 올바르지 않습니다.");
        }

        TransactionHistory history = TransactionHistory.builder()
                .accountId(account.getAccountId())
                .transactionType(transactionType)
                .transactionAmount(amount)
                .balanceAfterTransaction(account.getBalance())
                .transactionContent(content)
                .transactionAt(now)
                .transactionStatus(TransactionStatus.COMPLETED)
                .transactionSqno(transactionSqnoGenerator.generate(now))
                .build();

        return transactionHistoryRepository.save(history);
    }
}
