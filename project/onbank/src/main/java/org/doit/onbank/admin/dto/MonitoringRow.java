package org.doit.onbank.admin.dto;

import java.time.LocalDateTime;

// MON-002 실시간 거래 현황 한 줄 (MonitoringSearchRepository)
// 쿼리의 AS 이름과 getter 이름이 짝 (TransactionListRow 와 같은 방식)
public interface MonitoringRow {
    Long getTransactionHistoryId();         // 거래내역 번호. FdsCautionStore 의 키
    LocalDateTime getTransactionAt();       // 거래일시
    String getTransactionType();            // TRANSFER_OUT / WITHDRAWAL (팀원2 TransactionType)
    Long getAmount();                       // 거래 금액
    String getAccountNumber();              // 출금 계좌
    String getMemberLoginId();              // 회원 아이디
    String getMemberName();                 // 회원 이름
    String getCounterpartAccountNumber();   // 받는 계좌. 이체만 값이 있음
    Long getDetectionId();                  // 탐지 번호. 60점 이상만 값이 있음 → 상세(S-16) 링크
    Integer getRiskScore();                 // 탐지 점수. 주의(30~59)는 DB에 없어서 서비스가 FdsCautionStore 에서 붙인다
}
