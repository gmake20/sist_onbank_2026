package org.doit.onbank.admin.dto;

import java.time.LocalDateTime;

// FDS-002 탐지 상세 아래 처리 이력 한 줄 (FdsDetectionSearchRepository.findHistory)
// fds_handling_history 는 추가만 한다 (FdsHandlingHistory.java 14줄). 2단계(배정)부터 쌓인다
public interface FdsHistoryRow {
    LocalDateTime getCreatedAt();       // 기록 시각
    String getStage();                  // 바뀐 단계 (FdsStatus 이름)
    String getAdminName();              // 처리한 관리자 이름
    String getContactMethod();          // 연락 방법 (ContactMethod 이름). 연락 단계에서만
    String getContactResult();          // 연락 결과 (ContactResult 이름). 연락 단계에서만
    String getMemo();                   // 메모
}
