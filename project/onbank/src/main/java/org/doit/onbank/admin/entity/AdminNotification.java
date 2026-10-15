package org.doit.onbank.admin.entity;

// 관리자·직원 알림함. 받는 사람은 recipientType(ADMIN/EMPLOYEE) + recipientId 로 구분
// 두 테이블을 가리키므로 DB에도 FK가 없다

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.doit.onbank.common.security.ActorType;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdminNotification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long notificationId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 10)
    private ActorType recipientType;

    @Column(nullable = false)
    private Long recipientId;

    @Column(nullable = false, length = 30)
    private String type;

    private Long targetId;

    @Column(nullable = false, length = 300)
    private String message;

    private LocalDateTime readAt;   // NULL이면 미확인

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private AdminNotification(ActorType recipientType, Long recipientId, String type,
                              Long targetId, String message) {
        this.recipientType = recipientType;
        this.recipientId = recipientId;
        this.type = type;
        this.targetId = targetId;
        this.message = message;
    }

    // 알림 확인. 이미 읽은 알림은 처음 읽은 시간을 유지한다
    public void markAsRead() {
        if (this.readAt == null) {
            this.readAt = LocalDateTime.now();
        }
    }
}
