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
@NoArgsConstructor(access = AccessLevel.PROTECTED)	// JPA용 기본 생성자
@ToString(exclude = "password")
public class Member extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)	// AUTO_INCREMENT
	private Long memberId;

	@Column(nullable = false, length = 30)
	private String loginId;

	@Column(nullable = false, length = 100)
	private String password;	// BCrypt 해시

	@Column(nullable = false, length = 30)
	private String name;

	@Column(length = 100)
	private String email;		// 탈퇴 시 NULL

	@Column(length = 20)
	private String phone;		// 숫자만 저장. 탈퇴 시 NULL

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

	private LocalDateTime loginLockedAt;	// NULL이면 잠기지 않음

	@Column(nullable = false)
	private LocalDateTime termsAgreedAt;

	// CHAR(1) 'Y'/'N' ↔ boolean
	@Convert(converter = YesNoConverter.class)
	@Column(name = "marketing_agree_yn", nullable = false, length = 1)
	private boolean marketingAgree = false;

	private LocalDateTime withdrawnAt;

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
}
