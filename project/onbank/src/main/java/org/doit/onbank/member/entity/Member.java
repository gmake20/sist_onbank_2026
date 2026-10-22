package org.doit.onbank.member.entity;

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
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

// 고객 회원 (직원은 employee 테이블)
@Entity
@Table(name = "members")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // JPA용 기본 생성자
@ToString(exclude = "password")
public class Member extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY) // AUTO_INCREMENT
	private Long memberId;

	@Column(nullable = false, length = 30)
	private String loginId;

	@Column(nullable = false, length = 100)
	private String password; // BCrypt 해시

	@Column(nullable = false, length = 30)
	private String name;

	@Column(length = 100)
	private String email; // 탈퇴 시 NULL

	@Column(length = 20)
	private String phone; // 숫자만 저장. 탈퇴 시 NULL

	@Column(nullable = false)
	private LocalDate birthDate;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 20)
	private MemberStatus status = MemberStatus.ACTIVE;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 20)
	private VerificationStatus verificationStatus = VerificationStatus.NONE;

	@Column(nullable = false)
	private int loginFailCount = 0;

	private LocalDateTime loginLockedAt; // 잠긴 시각 기록용. 잠김 여부는 status = LOCKED 로 판단

	@Column(nullable = false)
	private LocalDateTime termsAgreedAt;

	// CHAR(1) 'Y'/'N' ↔ boolean
	@Convert(converter = YesNoConverter.class)
	@Column(name = "marketing_agree_yn", nullable = false, length = 1)
	private boolean marketingAgree = false;

	private LocalDateTime withdrawnAt;

	// 비밀번호 찾기로 임시 비밀번호를 받았으면 true → 로그인 직후 변경 화면으로 보낸다 (TempPasswordFilter)
	@Convert(converter = YesNoConverter.class)
	@Column(name = "temp_pw_yn", nullable = false, length = 1)
	private boolean tempPassword = false;

	// 회원가입 시 입력받는 값만 빌더로 받는다. 상태값은 위의 기본값을 사용
	@Builder
	private Member(String loginId, String password, String name, String email, String phone,
			LocalDate birthDate, LocalDateTime termsAgreedAt, boolean marketingAgree) {
		this.loginId = loginId;
		this.password = password;
		this.name = name;
		this.email = email;
		this.phone = phone;
		this.birthDate = birthDate;
		this.termsAgreedAt = termsAgreedAt;
		this.marketingAgree = marketingAgree;
	}

	// ---------- 로그인 실패 잠금 (LoginFailureHandler에서 호출) ----------
	// 잠금은 status = LOCKED (관리자와 같은 방식). 정상(ACTIVE)일 때만 잠근다
	// 정지·탈퇴 회원은 Spring Security가 비밀번호 확인 전에 막으므로 여기까지 오지 않는다

	// 비밀번호 오류 1회 기록. maxFailCount회가 되면 잠근다. 잠긴 상태면 true
	public boolean recordLoginFailure(int maxFailCount, LocalDateTime now) {
		loginFailCount++;
		if (loginFailCount >= maxFailCount && status == MemberStatus.ACTIVE) {
			status = MemberStatus.LOCKED;
			loginLockedAt = now;
		}
		return status == MemberStatus.LOCKED;
	}

	// 로그인 성공 : 실패 횟수 초기화
	public void recordLoginSuccess() {
		loginFailCount = 0;
	}

	// 잠금 해제 (비밀번호 찾기의 임시 비밀번호 발급, 다음 단계 : 직원 회원 관리 화면에서 호출)
	public void unlockLogin() {
		loginFailCount = 0;
		loginLockedAt = null;
		if (status == MemberStatus.LOCKED) {
			status = MemberStatus.ACTIVE;
		}
	}

	// ---------- 비밀번호 ----------

	// 비밀번호 찾기 : 임시 비밀번호로 바꾸고 로그인 잠금도 푼다 (본인 이메일로 받은 사람만 로그인 가능)
	public void issueTempPassword(String encodedPassword) {
		this.password = encodedPassword;
		this.tempPassword = true;
		unlockLogin();
	}

	// 비밀번호 변경 (본인이 변경 화면에서). 임시 비밀번호 표시도 해제한다
	public void changePassword(String encodedPassword) {
		this.password = encodedPassword;
		this.tempPassword = false;
	}

	// ---------- 이용 제한 (관리자 회원 관리 화면에서 호출, MEM-003) ----------
	// 정지(SUSPENDED)는 isEnabled() = false 라 비밀번호 확인 전에 로그인이 막힌다

	// 이용 제한 : 정상·로그인 잠금 → 정지
	public void restrict() {
		status = MemberStatus.SUSPENDED;
	}

	// 이용 제한 해제 : 정지 → 정상 (실패 횟수·잠긴 시각 초기화)
	public void unrestrict() {
		loginFailCount = 0;
		loginLockedAt = null;
		if (status == MemberStatus.SUSPENDED) {
			status = MemberStatus.ACTIVE;
		}
	}

	// ---------- 회원정보 수정·탈퇴 (마이페이지에서 본인이 호출) ----------

	// 연락처·마케팅 수신 동의 변경. 아이디·이름·생년월일은 바꾸지 않는다
	// 중복·인증 확인은 MemberService에서 끝난 상태로 호출된다
	public void changeContact(String email, String phone, boolean marketingAgree) {
		this.email = email;
		this.phone = phone;
		this.marketingAgree = marketingAgree;
	}

	// 탈퇴 : 행은 지우지 않고 상태만 바꾼다. 이메일·휴대폰은 비워 같은 값으로 재가입할 수 있게 한다
	// 아이디는 남겨 재사용을 막는다 (schema-team1-dictionary.md). 탈퇴 회원은 isEnabled() = false 라 로그인 불가
	public void withdraw(LocalDateTime now) {
		if (status == MemberStatus.WITHDRAWN) {
			throw new IllegalStateException("이미 탈퇴한 회원입니다.");
		}
		status = MemberStatus.WITHDRAWN;
		withdrawnAt = now;
		email = null;
		phone = null;
		marketingAgree = false;
	}
}
