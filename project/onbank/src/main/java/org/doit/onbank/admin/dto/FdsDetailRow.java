package org.doit.onbank.admin.dto;

import java.time.LocalDateTime;

// FDS-002 탐지 상세 위쪽·오른쪽 정보 (FdsDetectionSearchRepository.findDetail)
// 쿼리의 AS 이름과 getter 이름이 짝 (FdsListRow 와 같은 방식)
// 규칙별 점수·근거는 detail(JSON 문자열)을 서비스에서 푼다
public interface FdsDetailRow {
    Long getDetectionId();                  // 탐지 번호
    LocalDateTime getDetectedAt();          // 탐지 일시
    Integer getRiskScore();                 // 점수 합
    String getRuleCode();                   // 대표 규칙 (FdsRule 이름)
    String getDetail();                     // 규칙별 [{"r","s","b"}] JSON (FDS-탐지규칙.md 4장)
    String getStatus();                     // FdsStatus 이름
    Long getAssignedAdminId();              // 담당자 번호 (화면에서 "내 담당"인지 볼 때). 배정 전이면 null
    String getAssignedAdminName();          // 담당자 이름, 배정 전이면 null
    LocalDateTime getAssignedAt();          // 배정 시각
    LocalDateTime getClosedAt();            // 종결 시각
    String getTransactionType();            // TRANSFER_OUT = 이체 / WITHDRAWAL = ATM 출금
    Long getAmount();                       // 거래 금액
    LocalDateTime getTransactionAt();       // 거래 일시
    String getContent();                    // 적요
    String getCounterpartAccountNumber();   // 받는 계좌. 이체일 때만
    String getAccountNumber();              // 출금 계좌
    String getMemberLoginId();              // 회원 아이디
    String getMemberName();                 // 회원 이름
    Long getOpenCountOnAccount();           // 같은 계좌의 처리 중 탐지 수 (이 건 포함)
}
