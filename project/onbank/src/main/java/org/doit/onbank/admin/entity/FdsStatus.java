package org.doit.onbank.admin.entity;

// fds_detection.status, fds_handling_history.stage
public enum FdsStatus {
        UNCHECKED,              // 미확인
        ASSIGNED,               // 담당자 배정
        CONTACTING,             // 고객 연락 중
        NORMAL_CONFIRMED,       // 정상 거래 확인 (종결)
        FRAUD_ACTION            // 사기 조치 (종결)
}
