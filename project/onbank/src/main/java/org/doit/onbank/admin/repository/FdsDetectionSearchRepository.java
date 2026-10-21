package org.doit.onbank.admin.repository;

import org.doit.onbank.admin.dto.FdsListRow;
import org.doit.onbank.admin.entity.FdsDetection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;

// FDS-002 탐지 목록 (S-15). 조회만 (Repository 상속 → save·delete 없음)
// 일반 SQL (TransactionSearchRepository 와 같은 방식). 판정용 쿼리(FdsSearchRepository)와는 섞지 않는다
public interface FdsDetectionSearchRepository extends Repository<FdsDetection, Long> {

    // statuses : 서비스가 "처리 중 / 종결 / 전체 / 단계 하나"를 FdsStatus 이름 목록으로 바꿔서 넘긴다
    // 기간은 from 이상 toExclusive 미만 — 서비스에서 종료일 + 1일 00:00 을 넘긴다
    // 계좌번호는 하이픈을 뺀 값끼리 정확히 일치 (D-76)
    // assignedAdminId : "내 담당만"일 때만 값이 있다
    String FROM_WHERE = """
             FROM fds_detection fd
             JOIN account a ON a.account_id = fd.account_id
             JOIN members m ON m.member_id = a.member_id
             JOIN transaction_history th ON th.transaction_history_id = fd.transaction_history_id
             LEFT JOIN admin ad ON ad.admin_id = fd.assigned_admin_id
             WHERE fd.detected_at >= :from AND fd.detected_at < :toExclusive
             AND fd.status IN (:statuses)
             AND (:accountNumber IS NULL OR REPLACE(a.account_number, '-', '') = :accountNumber)
             AND (:assignedAdminId IS NULL OR fd.assigned_admin_id = :assignedAdminId)
             """;

    // "외 N건" = detail JSON 배열 길이 - 1. JSON 이 깨져 있으면 0 으로 (목록 전체가 오류 나지 않게)
    // 처리 중 상태 3개는 FdsStatus 와 같게 (UNCHECKED·ASSIGNED·CONTACTING, FDS-탐지규칙.md 6장)
    @Query(value = """
             SELECT fd.detection_id AS detectionId,
                    fd.detected_at AS detectedAt,
                    fd.risk_score AS riskScore,
                    fd.rule_code AS ruleCode,
                    CASE WHEN JSON_VALID(fd.detail) THEN JSON_LENGTH(fd.detail) - 1 ELSE 0 END AS otherRuleCount,
                    th.transaction_type AS transactionType,
                    th.transaction_amount AS amount,
                    a.account_number AS accountNumber,
                    m.login_id AS memberLoginId,
                    m.name AS memberName,
                    fd.status AS status,
                    ad.name AS assignedAdminName,
                    (SELECT COUNT(*) FROM fds_detection o
                      WHERE o.account_id = fd.account_id
                      AND o.status IN ('UNCHECKED', 'ASSIGNED', 'CONTACTING')) AS openCountOnAccount
             """ + FROM_WHERE + " ORDER BY fd.detection_id DESC",
            countQuery = "SELECT COUNT(*) " + FROM_WHERE,
            nativeQuery = true)
    Page<FdsListRow> search(@Param("from") LocalDateTime from,
                            @Param("toExclusive") LocalDateTime toExclusive,
                            @Param("statuses") Collection<String> statuses,
                            @Param("accountNumber") String accountNumber,
                            @Param("assignedAdminId") Long assignedAdminId,
                            Pageable pageable);
}
