package org.doit.onbank.admin.dto;

import java.time.LocalDateTime;
import java.util.List;

// MON-002 폴링 응답. 화면 JS는 3초마다 받아서 지표·표·탐지 목록을 통째로 다시 그린다 (D-84)
// uncheckedCount = 미확인 탐지 수 / items = 최근 50건, 최신순 / detections = 최근 탐지 10건, 최신순
public record MonitoringPoll(long uncheckedCount, List<Item> items, List<Detection> detections) {

    // level : DETECTED(60점 이상, 빨강) / CAUTION(30~59점, 노랑) / null(점수 없음 — 30점 미만, 판정 제외, 아직 판정 중)
    public record Item(Long transactionHistoryId, LocalDateTime transactionAt, String transactionType, Long amount,
                       String accountNumber, String memberLoginId, String memberName, String counterpartAccountNumber,
                       Long detectionId, Integer riskScore, String level) {

        // 탐지는 DB(row), 주의는 서버 메모리(cautionScore). 둘 다 없으면 점수 없음
        public static Item of(MonitoringRow row, Integer cautionScore) {
            String level = row.getDetectionId() != null ? "DETECTED" : cautionScore != null ? "CAUTION" : null;
            Integer score = row.getDetectionId() != null ? row.getRiskScore() : cautionScore;
            return new Item(row.getTransactionHistoryId(), row.getTransactionAt(), row.getTransactionType(),
                    row.getAmount(), row.getAccountNumber(), row.getMemberLoginId(), row.getMemberName(),
                    row.getCounterpartAccountNumber(), row.getDetectionId(), score, level);
        }
    }

    // 규칙·상태 이름은 서비스가 AdminFdsService.RULE_LABELS·STATUS_LABELS 로 바꿔서 넣는다 (탐지 목록 화면과 같은 글자)
    public record Detection(Long detectionId, LocalDateTime detectedAt, Integer riskScore, String ruleLabel,
                            String status, String statusLabel, String transactionType, Long amount, String accountNumber) {

        public static Detection of(MonitoringDetectionRow row, String ruleLabel, String statusLabel) {
            return new Detection(row.getDetectionId(), row.getDetectedAt(), row.getRiskScore(), ruleLabel,
                    row.getStatus(), statusLabel, row.getTransactionType(), row.getAmount(), row.getAccountNumber());
        }
    }
}
