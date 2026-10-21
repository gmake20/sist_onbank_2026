package org.doit.onbank.admin.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

// FDS 판정에 쓰는 사실. 기준과 비교는 FdsRuleEvaluator 가 한다
// 이번 거래는 이미 저장된 뒤라, 합계·건수에는 포함하고 "평소 최대"·"처음 계좌"에서는 뺀 값이다
// 값이 없으면 null (예: 90일 기록 없음, 직전 거래 없음, OTP 재등록 아님)
public record FdsFacts(
    // 이번 거래
    String transactionType,     // TRANSFER_OUT(이체) / WITHDRAWAL(ATM 출금)
    Long memberId,
    Long accountId,
    Long transactionHistoryId,
    long amount,
    LocalDateTime transactionAt,

    // A 계정
    LocalDateTime otpReactivatedAt,      // 분실(LOST) 기기가 있을 때만 지금 기기 등록 시각, 아니면 null
    LocalDate birthDate,

    // B 돈 이동
    long memberTodayTotal,              // 회원 모든 계좌 오늘 이체 + ATM 출금 합계 (이번 거래 포함)
    Long usualMaxAmount,                // 최근 N일 1회 최대 금액 (이번 거래 제외)
    long recentCount,                   // 최근 N분 이체 + ATM 출금 건수 (이번 거래 포함)
    Boolean firstRecipient,             // 처음 보내는 계좌인지. ATM 출금이면 null
    Long recentLargestDeposit,          // 최근 N분 이 계좌 입금 최대 금액, 없으면 null
    LocalDateTime previousTransactionAt // 이번 거래 바로 전 거래 시각
) {
}

