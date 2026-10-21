package org.doit.onbank.admin.entity;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

// 이상거래 탐지 건

@Entity
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor
public class FdsDetection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long detectionId;

    @Column(nullable = false)
    private Long accountId;

    @Column(nullable = false)
    private Long transactionHistoryId;

    @Column(nullable = false, length = 30)
    private String ruleCode;

    // 컬럼이 SMALLINT라서 지정하지 않으면 validate가 INT로 기대해 실패한다
    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(nullable = false)
    private int riskScore;

    @Column(length = 500)
    private String detail;

    // DB DEFAULT 'UNCHECKED'는 Hibernate가 값을 직접 넣어서 안 먹으므로 여기서 초기값
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private FdsStatus status = FdsStatus.UNCHECKED;

    private Long assignedAdminId;

    private LocalDateTime assignedAt;

    private LocalDateTime closedAt;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime detectedAt;

    @Builder
    private FdsDetection(Long accountId, Long transactionHistoryId, String ruleCode, int riskScore, String detail) {
        this.accountId = accountId;
        this.transactionHistoryId = transactionHistoryId;
        this.ruleCode = ruleCode;
        this.riskScore = riskScore;
        this.detail = detail;
    }

    // FDS-004 배정 : 미확인(UNCHECKED) → 담당 배정(ASSIGNED). 다른 상태면 거절 (Account.freeze 와 같은 방식)
    public void assign(Long adminId, LocalDateTime now) {
        if (status != FdsStatus.UNCHECKED) {
            throw new IllegalStateException("미확인 탐지만 맡을 수 있습니다. (현재 상태: " + status + ")");
        }
        this.status = FdsStatus.ASSIGNED;
        this.assignedAdminId = adminId;
        this.assignedAt = now;
    }

    // FDS-005 고객 연락 시작 : 담당 배정(ASSIGNED) → 고객 연락 중(CONTACTING). 순서를 건너뛰면 거절 (EX-ADM-FDS-003)
    // 담당자 본인인지는 서비스가 본다 (관리자 번호가 필요해서)
    public void startContact() {
        if (status != FdsStatus.ASSIGNED) {
            throw new IllegalStateException("담당 배정 단계에서만 고객 연락을 시작할 수 있습니다. (현재 상태: " + status + ")");
        }
        this.status = FdsStatus.CONTACTING;
    }

    // FDS-005 종결 : 고객 연락 중(CONTACTING) → 정상 거래 확인(NORMAL_CONFIRMED) / 사기 의심 조치(FRAUD_ACTION)
    // 담당자 본인인지, 연락 기록이 있는지, 마지막 연락 결과로 고를 수 있는 결과인지는 서비스가 본다 (다른 테이블이 필요해서)
    public void close(FdsStatus result, LocalDateTime now) {
        if (status != FdsStatus.CONTACTING) {
            throw new IllegalStateException("고객 연락 중 단계에서만 종결할 수 있습니다. (현재 상태: " + status + ")");
        }
        if (result != FdsStatus.NORMAL_CONFIRMED && result != FdsStatus.FRAUD_ACTION) {
            throw new IllegalArgumentException("종결 결과는 정상 거래 확인 또는 사기 의심 조치만 됩니다. (받은 값: " + result + ")");
        }
        this.status = result;
        this.closedAt = now;
    }

}

