package org.doit.onbank.admin.dto;

import org.doit.onbank.account.entity.AccountStatus;
import org.doit.onbank.account.entity.AccountType;

import java.time.LocalDateTime;

// ACC-001 계좌 목록 한 줄. account + members 를 쿼리에서 바로 담는다
// accountType 은 테스트 계좌(TEST-xxxx)에서 NULL 일 수 있다
public record AccountListRow(Long accountId, String accountNumber, String memberLoginId, String memberName,
                             AccountType accountType, AccountStatus accountStatus, Long balance,
                             LocalDateTime lastTransactionAt) {
}
