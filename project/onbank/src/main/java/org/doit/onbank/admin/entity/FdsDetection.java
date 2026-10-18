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

}

