package org.doit.onbank.admin.repository;

import org.doit.onbank.admin.dto.FdsDetailRow;
import org.doit.onbank.admin.dto.FdsHistoryRow;
import org.doit.onbank.admin.dto.FdsListRow;
import org.doit.onbank.admin.entity.FdsDetection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

// FDS-002 탐지 목록 (S-15) · 상세 (S-16). 조회만 (Repository 상속 → save·delete 없음)
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

    // S-16 상세 한 건. 받는 계좌는 이체(TRANSFER_OUT)일 때만 account_transfer 에서
    // transaction_sqno 에 UNIQUE 가 없어서(05-DB설계 196줄) JOIN 대신 LIMIT 1 서브쿼리 → 여러 줄이 나와 Optional 이 깨지는 일 없게
    @Query(value = """
             SELECT fd.detection_id AS detectionId,
                    fd.detected_at AS detectedAt,
                    fd.risk_score AS riskScore,
                    fd.rule_code AS ruleCode,
                    fd.detail AS detail,
                    fd.status AS status,
                    fd.assigned_admin_id AS assignedAdminId,
                    ad.name AS assignedAdminName,
                    fd.assigned_at AS assignedAt,
                    fd.closed_at AS closedAt,
                    th.transaction_type AS transactionType,
                    th.transaction_amount AS amount,
                    th.transaction_at AS transactionAt,
                    th.transaction_content AS content,
                    CASE WHEN th.transaction_type = 'TRANSFER_OUT' THEN
                        (SELECT tr.deposit_account_number FROM account_transfer tr
                          WHERE tr.transaction_sqno = th.transaction_sqno LIMIT 1)
                    END AS counterpartAccountNumber,
                    a.account_number AS accountNumber,
                    m.login_id AS memberLoginId,
                    m.name AS memberName,
                    (SELECT COUNT(*) FROM fds_detection o
                      WHERE o.account_id = fd.account_id
                      AND o.status IN ('UNCHECKED', 'ASSIGNED', 'CONTACTING')) AS openCountOnAccount
             FROM fds_detection fd
             JOIN account a ON a.account_id = fd.account_id
             JOIN members m ON m.member_id = a.member_id
             JOIN transaction_history th ON th.transaction_history_id = fd.transaction_history_id
             LEFT JOIN admin ad ON ad.admin_id = fd.assigned_admin_id
             WHERE fd.detection_id = :detectionId
             """, nativeQuery = true)
    Optional<FdsDetailRow> findDetail(@Param("detectionId") Long detectionId);

    // S-16 처리 이력. 오래된 순 (단계가 진행된 순서대로 읽히게)
    @Query(value = """
             SELECT h.created_at AS createdAt,
                    h.stage AS stage,
                    ad.name AS adminName,
                    h.contact_method AS contactMethod,
                    h.contact_result AS contactResult,
                    h.memo AS memo
             FROM fds_handling_history h
             JOIN admin ad ON ad.admin_id = h.admin_id
             WHERE h.detection_id = :detectionId
             ORDER BY h.created_at, h.history_id
             """, nativeQuery = true)
    List<FdsHistoryRow> findHistory(@Param("detectionId") Long detectionId);
}
