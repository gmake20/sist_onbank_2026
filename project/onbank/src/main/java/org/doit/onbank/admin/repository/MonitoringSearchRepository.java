package org.doit.onbank.admin.repository;

import org.doit.onbank.account.entity.TransactionHistory;
import org.doit.onbank.admin.dto.MonitoringDetectionRow;
import org.doit.onbank.admin.dto.MonitoringRow;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

import java.util.List;

// MON-002 실시간 거래 현황 (S-17). 조회만 (Repository 상속 → save·delete 없음)
// 이체(TRANSFER_OUT) + ATM 출금(WITHDRAWAL). 대출·예적금은 FDS 판정과 같은 적요 기준으로 뺀다 (D-78)
// 예약·정기 이체, 본인 계좌 이체는 판정을 안 받지만 실제로 나간 돈이라 보여 준다 → 점수 없음 (D-84)
// 화면은 3초마다 최근 50건을 통째로 다시 받는다 : FDS 는 이체 커밋 뒤에 돌아서 점수가 조금 늦게 붙는데,
// 번호로 이어 받으면(after) 그 사이 받은 줄은 끝까지 색이 안 칠해지기 때문 (D-84)
public interface MonitoringSearchRepository extends Repository<TransactionHistory, Long> {

    int LATEST_LIMIT = 50;
    int DETECTION_LIMIT = 10;

    // 받는 계좌·탐지는 transaction_sqno / transaction_history_id 에 UNIQUE 가 없어서 LIMIT 1 서브쿼리
    // (FdsDetectionSearchRepository.findDetail 과 같음)
    String SELECT_FROM_WHERE = """
             SELECT th.transaction_history_id AS transactionHistoryId,
                    th.transaction_at AS transactionAt,
                    th.transaction_type AS transactionType,
                    th.transaction_amount AS amount,
                    a.account_number AS accountNumber,
                    m.login_id AS memberLoginId,
                    m.name AS memberName,
                    CASE WHEN th.transaction_type = 'TRANSFER_OUT' THEN
                        (SELECT tr.deposit_account_number FROM account_transfer tr
                          WHERE tr.transaction_sqno = th.transaction_sqno LIMIT 1)
                    END AS counterpartAccountNumber,
                    (SELECT fd.detection_id FROM fds_detection fd
                      WHERE fd.transaction_history_id = th.transaction_history_id
                      ORDER BY fd.detection_id LIMIT 1) AS detectionId,
                    (SELECT fd.risk_score FROM fds_detection fd
                      WHERE fd.transaction_history_id = th.transaction_history_id
                      ORDER BY fd.detection_id LIMIT 1) AS riskScore
             FROM transaction_history th
             JOIN account a ON a.account_id = th.account_id
             JOIN members m ON m.member_id = a.member_id
             WHERE th.transaction_type IN ('TRANSFER_OUT', 'WITHDRAWAL')
             AND (th.transaction_content IS NULL OR th.transaction_content NOT IN (""" + FdsSearchRepository.EXCLUDED_CONTENTS + """
             ))
             """;

    // 최근 50건, 최신순 (화면 표 맨 위가 가장 최근)
    @Query(value = SELECT_FROM_WHERE + " ORDER BY th.transaction_history_id DESC LIMIT " + LATEST_LIMIT,
            nativeQuery = true)
    List<MonitoringRow> findLatest();

    // 지표 "미확인 탐지" (인덱스 IDX_FDS_DET_STATUS)
    @Query(value = "SELECT COUNT(*) FROM fds_detection WHERE status = 'UNCHECKED'", nativeQuery = true)
    long countUnchecked();

    // 오른쪽 "탐지 실시간 알림" : 최근 탐지 10건, 최신순. 처리 상태와 상관없이 (와이어프레임 11장 미확인/확인함)
    @Query(value = """
             SELECT fd.detection_id AS detectionId,
                    fd.transaction_history_id AS transactionHistoryId,
                    fd.detected_at AS detectedAt,
                    fd.risk_score AS riskScore,
                    fd.rule_code AS ruleCode,
                    fd.status AS status,
                    th.transaction_type AS transactionType,
                    th.transaction_amount AS amount,
                    a.account_number AS accountNumber
             FROM fds_detection fd
             JOIN account a ON a.account_id = fd.account_id
             JOIN transaction_history th ON th.transaction_history_id = fd.transaction_history_id
             ORDER BY fd.detection_id DESC LIMIT
             """ + DETECTION_LIMIT, nativeQuery = true)
    List<MonitoringDetectionRow> findRecentDetections();
}
