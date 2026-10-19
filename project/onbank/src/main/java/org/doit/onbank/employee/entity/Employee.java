package org.doit.onbank.employee.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.doit.onbank.common.entity.BaseEntity;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.hibernate.type.YesNoConverter;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
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

// 은행 직원 (/employee/** 사용자). 고객은 members, 시스템 관리자는 admin 테이블
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(exclude = "password")
public class Employee extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long employeeId;

	@Column(nullable = false, length = 10)
	private String employeeNo;	// 사번 - 로그인 아이디

	@Column(nullable = false, length = 100)
	private String password;	// BCrypt 해시

	@Column(nullable = false, length = 30)
	private String name;

	@Column(nullable = false, length = 100)
	private String email;

	@Column(length = 20)
	private String phone;

	// 소속 지점 (branch 엔티티를 만들면 @ManyToOne으로 바꾼다)
	@Column(nullable = false)
	private Long branchId;

	@Column(nullable = false, length = 20)
	private String position;	// 직급 - 화면 표시용, 권한과 무관

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 20)
	private EmployeeRole role = EmployeeRole.STAFF;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 20)
	private EmployeeStatus status = EmployeeStatus.ACTIVE;

	// 임시 비밀번호 여부 - true면 첫 로그인 시 비밀번호 변경 강제
	@Convert(converter = YesNoConverter.class)
	@Column(name = "temp_pw_yn", nullable = false, length = 1)
	private boolean tempPassword = true;

	@Column(nullable = false)
	private int loginFailCount = 0;

	private LocalDateTime loginLockedAt;	// NULL이면 잠기지 않음

	@Column(nullable = false)
	private LocalDate hiredAt;

	private LocalDate retiredAt;

	// 직원 등록 시 입력받는 값만 빌더로 받는다. 상태값은 위의 기본값을 사용
	@Builder
	private Employee(String employeeNo, String password, String name, String email, String phone,
			Long branchId, String position, EmployeeRole role, LocalDate hiredAt, boolean tempPassword) {
		this.employeeNo = employeeNo;
		this.password = password;
		this.name = name;
		this.email = email;
		this.phone = phone;
		this.branchId = branchId;
		this.position = position;
		this.role = role != null ? role : EmployeeRole.STAFF;
		this.hiredAt = hiredAt;
		this.tempPassword = tempPassword;
	}

	// ---------- 로그인 실패 잠금 (LoginFailureHandler에서 호출) ----------
	// 시간이 지나도 자동으로 풀리지 않는다. 관리자 화면에서 unlockLogin()으로 해제

	// 비밀번호 오류 1회 기록. maxFailCount회가 되면 잠근다. 잠긴 상태면 true
	public boolean recordLoginFailure(int maxFailCount, LocalDateTime now) {
		loginFailCount++;
		if (loginFailCount >= maxFailCount && loginLockedAt == null) {
			loginLockedAt = now;
		}
		return loginLockedAt != null;
	}

	// 로그인 성공 : 실패 횟수 초기화
	public void recordLoginSuccess() {
		loginFailCount = 0;
	}

	// 잠금 해제 (관리자 파트의 계정 잠금 해제 화면에서 호출)
	public void unlockLogin() {
		loginFailCount = 0;
		loginLockedAt = null;
	}

	// 비밀번호 변경 (본인이 변경 화면에서). 임시 비밀번호 표시도 해제한다
	public void changePassword(String encodedPassword) {
		this.password = encodedPassword;
		this.tempPassword = false;
	}
}
