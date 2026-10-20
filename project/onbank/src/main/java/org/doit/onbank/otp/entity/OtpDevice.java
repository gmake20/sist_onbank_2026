package org.doit.onbank.otp.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
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

// 가상 OTP 기기 (이체용 보안매체, auth.md 4장)
// created_at 대신 issued_at 이 있어서 BaseEntity 를 상속하지 않고 Auditing 만 직접 붙인다
@Entity
@Table(name = "otp_device")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)	// JPA용 기본 생성자
@ToString(exclude = "seedEncrypted")
public class OtpDevice {

	public static final int MAX_FAIL_COUNT = 10;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)	// AUTO_INCREMENT
	private Long otpDeviceId;

	@Column(nullable = false)
	private Long memberId;

	@Column(nullable = false, length = 20)
	private String serialNo;		// 기기 화면에 표시하는 일련번호

	@Column(nullable = false, length = 255)
	private String seedEncrypted;	// TOTP 시드 (OtpSeedCipher 로 AES 암호화)

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 20)
	private OtpStatus status = OtpStatus.ISSUED;

	@Column(nullable = false)
	private int failCount = 0;

	private Long lastUsedStep;		// 마지막으로 성공한 30초 구간 번호 (같은 번호 재사용 방지)

	@CreatedDate
	@Column(nullable = false, updatable = false)
	private LocalDateTime issuedAt;

	private LocalDateTime activatedAt;

	@LastModifiedDate
	private LocalDateTime updatedAt;

	// 발급 시 입력받는 값만 빌더로 받는다. 상태값은 위의 기본값을 사용
	@Builder
	private OtpDevice(Long memberId, String serialNo, String seedEncrypted) {
		this.memberId = memberId;
		this.serialNo = serialNo;
		this.seedEncrypted = seedEncrypted;
	}

	// 사용 등록 완료 (연속 2회 입력 성공). step 은 두 번째로 입력한 번호의 구간
	public void activate(long step, LocalDateTime now) {
		status = OtpStatus.ACTIVE;
		activatedAt = now;
		lastUsedStep = step;
		failCount = 0;
	}

	// 번호 오류 1회 기록. MAX_FAIL_COUNT회가 되면 정지한다. 정지되었으면 true
	public boolean recordFailure() {
		failCount++;
		if (failCount >= MAX_FAIL_COUNT) {
			status = OtpStatus.SUSPENDED;
		}
		return status == OtpStatus.SUSPENDED;
	}

	// 번호 일치 : 오류 횟수 초기화, 사용한 구간 기록
	public void recordSuccess(long step) {
		failCount = 0;
		lastUsedStep = step;
	}

	// 이 구간의 번호를 이미 썼는지 (같은 구간이나 그 이전 구간이면 사용한 것으로 본다)
	public boolean isStepUsed(long step) {
		return lastUsedStep != null && step <= lastUsedStep;
	}

	// 사용 등록 전 기기 폐기 (등록 코드를 잃어버려 다시 발급받을 때)
	public void dispose() {
		status = OtpStatus.DISPOSED;
	}

	// 분실 신고 (사용중·정지 기기). 이후 이 기기로는 검증할 수 없고, 회원은 새 OTP를 발급받을 수 있다
	public void reportLost() {
		status = OtpStatus.LOST;
	}
}
