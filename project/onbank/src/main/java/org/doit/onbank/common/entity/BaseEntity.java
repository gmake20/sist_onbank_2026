package org.doit.onbank.common.entity;

import java.time.LocalDateTime;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;

// created_at / updated_at 컬럼이 있는 테이블의 공통 부모 클래스
// DB의 DEFAULT CURRENT_TIMESTAMP(6)는 Hibernate가 INSERT 시 NULL을 직접 넣어서 동작하지 않으므로
// JPA Auditing(@EnableJpaAuditing)으로 값을 채운다.
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@Getter
public abstract class BaseEntity {

	@CreatedDate
	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@LastModifiedDate
	private LocalDateTime updatedAt;
}
