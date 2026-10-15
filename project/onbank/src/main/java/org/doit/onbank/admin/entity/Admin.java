package org.doit.onbank.admin.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.hibernate.type.YesNoConverter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

// 시스템 관리자 (/admin/** 사용자). 테이블 DDL은 팀원4의 02_admin_ddl.sql
// admin 테이블에는 updated_at이 없어서 BaseEntity를 상속하지 않고 created_at만 직접 둔다
@Entity
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(exclude = "password")
public class Admin {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long adminId;

	@Column(nullable = false, length = 50)
	private String loginId;

	@Column(nullable = false, length = 100)
	private String password;	// BCrypt 해시

	@Column(nullable = false, length = 50)
	private String name;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 20)
	private AdminRole role;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 10)
	private AdminStatus status = AdminStatus.ACTIVE;

	@Convert(converter = YesNoConverter.class)
	@Column(name = "temp_pw_yn", nullable = false, length = 1)
	private boolean tempPassword = true;

	// 컬럼이 TINYINT라서 지정하지 않으면 validate가 INT로 기대해 실패한다
	@JdbcTypeCode(SqlTypes.TINYINT)
	@Column(nullable = false)
	private int loginFailCount = 0;

	private LocalDateTime lastLoginAt;

	@CreatedDate
	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@Builder
	private Admin(String loginId, String password, String name, AdminRole role, boolean tempPassword) {
		this.loginId = loginId;
		this.password = password;
		this.name = name;
		this.role = role;
		this.tempPassword = tempPassword;
	}
}
