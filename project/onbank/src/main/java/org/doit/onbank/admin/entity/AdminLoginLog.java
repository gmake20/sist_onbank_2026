package org.doit.onbank.admin.entity;

import java.time.LocalDateTime;

import org.hibernate.type.YesNoConverter;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 관리자 로그인 이력 (성공·실패 모두). 테이블 DDL은 팀원4의 02_admin_ddl.sql
// 저장만 하고 수정·삭제 메서드는 두지 않는다
// 없는 아이디로 시도하면 adminId = null, failReason = UNKNOWN_ID
@Entity
@Table(name = "admin_login_log")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdminLoginLog {

	// 컬럼 길이에 맞춰 자른다 (긴 값을 넣어 INSERT가 실패하지 않도록)
	private static final int LOGIN_ID_MAX = 50;
	private static final int IP_MAX = 45;
	private static final int USER_AGENT_MAX = 500;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long loginLogId;

	private Long adminId;	// 없는 아이디면 NULL

	@Column(nullable = false, length = LOGIN_ID_MAX)
	private String loginIdInput;

	@Convert(converter = YesNoConverter.class)
	@Column(name = "success_yn", nullable = false, length = 1)
	private boolean success;

	@Column(length = 30)
	private String failReason;	// 성공이면 NULL. BAD_CREDENTIALS / LOGIN_LOCKED / INACTIVE / UNKNOWN_ID

	@Column(nullable = false, length = IP_MAX)
	private String ipAddress;

	@Column(length = USER_AGENT_MAX)
	private String userAgent;

	@Column(nullable = false, updatable = false)
	private LocalDateTime loginAt;

	@Builder
	private AdminLoginLog(Long adminId, String loginIdInput, boolean success, String failReason,
			String ipAddress, String userAgent) {
		this.adminId = adminId;
		this.loginIdInput = cut(loginIdInput == null ? "" : loginIdInput, LOGIN_ID_MAX);
		this.success = success;
		this.failReason = failReason;
		this.ipAddress = cut(ipAddress == null ? "" : ipAddress, IP_MAX);
		this.userAgent = cut(userAgent, USER_AGENT_MAX);
		this.loginAt = LocalDateTime.now();
	}

	private static String cut(String value, int max) {
		return (value == null || value.length() <= max) ? value : value.substring(0, max);
	}
}
