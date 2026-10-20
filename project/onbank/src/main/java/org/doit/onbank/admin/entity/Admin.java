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

	// ---------- 로그인 실패 잠금 (LoginFailureHandler에서 호출) ----------
	// 관리자는 잠금을 status = LOCKED로 표시한다 (직원·고객은 login_locked_at)
	// 시간이 지나도 자동으로 풀리지 않는다. 다른 관리자가 unlock()으로 해제

	// 비밀번호 오류 1회 기록. maxFailCount회가 되면 잠근다. 잠긴 상태면 true
	public boolean recordLoginFailure(int maxFailCount) {
		loginFailCount++;
		if (loginFailCount >= maxFailCount && status == AdminStatus.ACTIVE) {
			status = AdminStatus.LOCKED;
		}
		return status == AdminStatus.LOCKED;
	}

	// 로그인 성공 : 실패 횟수 초기화, 마지막 로그인 시간 기록
	public void recordLoginSuccess(LocalDateTime now) {
		loginFailCount = 0;
		lastLoginAt = now;
	}

	// 잠금 해제 (관리자 파트의 계정 잠금 해제 화면에서 호출). 비활성(INACTIVE) 계정은 그대로 둔다
	public void unlock() {
		loginFailCount = 0;
		if (status == AdminStatus.LOCKED) {
			status = AdminStatus.ACTIVE;
		}
	}

	// 비활성화 ACTIVE·LOCKED → INACTIVE. 실패 횟수는 활성화할 때 초기화
	public void deactivate() {
		status = AdminStatus.INACTIVE;
	}

	// 활성화 INACTIVE → ACTIVE, 실패 횟수 초기화. 잠긴(LOCKED) 계정은 unlock()으로
	public void activate() {
		loginFailCount = 0;
		if (status == AdminStatus.INACTIVE) {
			status = AdminStatus.ACTIVE;
		}
	}


	// 비밀번호 변경 (본인이 변경 화면에서). 임시 비밀번호 표시도 해제한다
	public void changePassword(String encodedPassword) {
		this.password = encodedPassword;
		this.tempPassword = false;
	}
}
