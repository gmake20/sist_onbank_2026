package org.doit.onbank.admin.dto;

import java.math.BigDecimal;

// DSH-001 요약 카드 6개. 값이 없으면 서비스에서 0 으로 채운다
// loanBalance 는 loan_account.remaining_balance 가 BigDecimal 이라 그대로 둔다
public record DashboardSummary(long totalMembers, long suspendedMembers,
                               long todayTransactionCount, long todayTransactionAmount,
                               long frozenAccounts, BigDecimal loanBalance) {
}
