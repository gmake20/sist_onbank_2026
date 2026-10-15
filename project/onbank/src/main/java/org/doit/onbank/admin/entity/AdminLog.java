package org.doit.onbank.admin.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.doit.onbank.common.security.ActorType;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

// 직원·관리자 작업 감사 기록. 저장만 하고 수정·삭제 메서드는 두지 않는다
// admin_log 테이블에는 updated_at이 없어서 BaseEntity를 상속하지 않는다
@Entity
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor
public class AdminLog {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long logId;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(nullable = false)
  private ActorType actorType;

  @Column(nullable = false)
  private Long actorId;

  @Column(nullable = false, length = 50)
  private String actionType;

  @Column(nullable = false, length = 30)
  private String targetType;

  @Column(nullable = false, length = 50)
  private String targetId;

  @Column(length = 500)
  private String beforeValue;

  @Column(length = 500)
  private String afterValue;

  @Column(length = 200)
  private String reason;

  @Column(length = 45)
  private String requestIp;

  @CreatedDate
  @Column(nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @Builder
  private AdminLog(ActorType actorType, Long actorId, String actionType, String targetType, String targetId,
                   String beforeValue, String afterValue, String reason, String requestIp
                   ) {
    this.actorType = actorType;
    this.actorId = actorId;
    this.actionType = actionType;
    this.targetType = targetType;
    this.targetId = targetId;
    this.beforeValue = beforeValue;
    this.afterValue = afterValue;
    this.reason = reason;
    this.requestIp = requestIp;

  }

}
