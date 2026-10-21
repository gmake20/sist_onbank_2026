package org.doit.onbank.admin.dto;

import java.time.LocalDateTime;

// FDS-002 탐지 목록 한 줄 (FdsDetectionSearchRepository.search)
// 쿼리의 AS 이름과 getter 이름이 짝 (TransactionListRow 와 같은 방식)
public interface FdsListRow {
    Long getDetectionId();              // 탐지 번호
    LocalDateTime getDetectedAt();      // 탐지 일시
    Integer getRiskScore();             // 점수 합
    String getRuleCode();               // 대표 규칙 (FdsRule 이름)
    Integer getOtherRuleCount();        // 함께 걸린 나머지 규칙 수 ("외 N건")
    String getTransactionType();        // TRANSFER_OUT = 이체 / WITHDRAWAL = ATM 출금
    Long getAmount();                   // 거래 금액
    String getAccountNumber();          // 계좌번호
    String getMemberLoginId();          // 회원 아이디
    String getMemberName();             // 회원 이름
    String getStatus();                 // FdsStatus 이름
    String getAssignedAdminName();      // 담당자 이름, 배정 전이면 null
    Long getOpenCountOnAccount();       // 같은 계좌의 처리 중 탐지 수 (이 건 포함)
}
