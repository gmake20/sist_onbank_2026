package org.doit.onbank.admin.dto;

import java.time.LocalDateTime;

// MON-002 모니터링 화면 오른쪽 "탐지 실시간 알림" 한 줄 (MonitoringSearchRepository.findRecentDetections)
// 쿼리의 AS 이름과 getter 이름이 짝 (FdsListRow 와 같은 방식)
public interface MonitoringDetectionRow {
    Long getDetectionId();          // 탐지 번호 → 상세(S-16) 링크
    Long getTransactionHistoryId(); // 거래내역 번호 → AI 보조 판정 결과를 찾는 키 (FdsAiService)
    LocalDateTime getDetectedAt();  // 탐지 일시
    Integer getRiskScore();         // 점수 합
    String getRuleCode();           // 대표 규칙 (FdsRule 이름)
    String getStatus();             // 처리 상태 (FdsStatus 이름)
    String getTransactionType();    // TRANSFER_OUT / WITHDRAWAL
    Long getAmount();               // 거래 금액
    String getAccountNumber();      // 출금 계좌
}
