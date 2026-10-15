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

// 이상거래 처리 이력 (단계 변경 + 고객 연락 결과). 추가만 하고 수정·삭제하지 않는다
// 연락 관련 3개(contactAt, contactMethod, contactResult)는 CONTACTING 단계에서만 채운다

@Entity
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor
public class FdsHandlingHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long historyId;

    @Column(nullable = false)
    private Long detectionId;

    @Column(nullable = false)
    private Long adminId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private FdsStatus stage;

    @Column(length = 500)
    private String memo;

    private LocalDateTime contactAt;

    @Column(length = 20)
    private String contactMethod;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(length = 30)
    private ContactResult contactResult;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private FdsHandlingHistory(Long detectionId, Long adminId, FdsStatus stage, String memo,
                               LocalDateTime contactAt, String contactMethod, ContactResult contactResult) {
        this.detectionId = detectionId;
        this.adminId = adminId;
        this.stage = stage;
        this.memo = memo;
        this.contactAt = contactAt;
        this.contactMethod = contactMethod;
        this.contactResult = contactResult;
    }

}
