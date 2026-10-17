package org.doit.onbank.otp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import org.doit.onbank.otp.entity.OtpDevice;
import org.doit.onbank.otp.entity.OtpStatus;
import org.doit.onbank.otp.repository.OtpDeviceRepository;
import org.doit.onbank.otp.service.OtpCheckResult.Type;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

// OtpCodeChecker 단위 테스트 : DB 대신 가짜(Mock) Repository, 시각은 고정
// 트랜잭션(REQUIRES_NEW)은 Spring 없이 동작하지 않으므로 여기서는 검증 규칙만 확인한다
@ExtendWith(MockitoExtension.class)
class OtpCodeCheckerTests {

	private static final Long MEMBER_ID = 100L;
	private static final long STEP = 56_666_667L;
	private static final Instant NOW = Instant.ofEpochSecond(STEP * TotpGenerator.STEP_SECONDS + 10);
	private static final byte[] SEED = "12345678901234567890".getBytes();

	@Mock
	private OtpDeviceRepository otpDeviceRepository;

	private final OtpSeedCipher cipher = new OtpSeedCipher(OtpSeedCipherTests.TEST_KEY);

	private OtpCodeChecker checker;
	private OtpDevice device;

	@BeforeEach
	void setUp() {
		checker = new OtpCodeChecker(otpDeviceRepository, cipher, Clock.fixed(NOW, ZoneId.of("Asia/Seoul")));
		device = OtpDevice.builder()
				.memberId(MEMBER_ID)
				.serialNo("0000000001")
				.seedEncrypted(cipher.encrypt(SEED))
				.build();
	}

	private void deviceIsActive() {
		device.activate(STEP - 10, LocalDateTime.now());
		given(otpDeviceRepository.findByMemberIdForUpdate(MEMBER_ID, OtpStatus.USABLE)).willReturn(Optional.of(device));
	}

	private static String code(long step) {
		return TotpGenerator.generate(SEED, step);
	}

	@Test
	void currentCodePasses() {
		deviceIsActive();
		device.recordFailure();

		OtpCheckResult result = checker.check(MEMBER_ID, code(STEP));

		assertThat(result.type()).isEqualTo(Type.OK);
		assertThat(device.getFailCount()).isZero();
		assertThat(device.getLastUsedStep()).isEqualTo(STEP);
	}

	// 기기 시계가 30초 빠르거나 느려도 통과
	@Test
	void previousAndNextStepPass() {
		deviceIsActive();

		assertThat(checker.check(MEMBER_ID, code(STEP - 1)).type()).isEqualTo(Type.OK);
		assertThat(checker.check(MEMBER_ID, code(STEP + 1)).type()).isEqualTo(Type.OK);
	}

	@Test
	void codeOutsideWindowFails() {
		deviceIsActive();

		OtpCheckResult result = checker.check(MEMBER_ID, code(STEP - 2));

		assertThat(result.type()).isEqualTo(Type.MISMATCH);
		assertThat(result.failCount()).isEqualTo(1);
	}

	// 한 번 성공한 번호는 다시 받지 않고, 오류 횟수도 올리지 않는다
	@Test
	void sameCodeCannotBeReused() {
		deviceIsActive();
		checker.check(MEMBER_ID, code(STEP));

		OtpCheckResult result = checker.check(MEMBER_ID, code(STEP));

		assertThat(result.type()).isEqualTo(Type.REUSED);
		assertThat(device.getFailCount()).isZero();
	}

	// 성공한 구간보다 이전 구간의 번호도 받지 않는다
	@Test
	void olderCodeAfterNewerSuccessIsReused() {
		deviceIsActive();
		checker.check(MEMBER_ID, code(STEP + 1));

		assertThat(checker.check(MEMBER_ID, code(STEP)).type()).isEqualTo(Type.REUSED);
	}

	@Test
	void tenFailuresSuspend() {
		deviceIsActive();
		for (int i = 1; i < OtpDevice.MAX_FAIL_COUNT; i++) {
			assertThat(checker.check(MEMBER_ID, "000000").type()).isEqualTo(Type.MISMATCH);
		}

		OtpCheckResult result = checker.check(MEMBER_ID, "000000");

		assertThat(result.type()).isEqualTo(Type.SUSPENDED_NOW);
		assertThat(device.getStatus()).isEqualTo(OtpStatus.SUSPENDED);
	}

	// 정지된 OTP는 맞는 번호를 넣어도 거절
	@Test
	void suspendedRejectsEvenCorrectCode() {
		deviceIsActive();
		for (int i = 0; i < OtpDevice.MAX_FAIL_COUNT; i++) {
			device.recordFailure();
		}

		assertThat(checker.check(MEMBER_ID, code(STEP)).type()).isEqualTo(Type.SUSPENDED);
	}

	@Test
	void nullCodeCountsAsWrong() {
		deviceIsActive();

		assertThat(checker.check(MEMBER_ID, null).type()).isEqualTo(Type.MISMATCH);
		assertThat(device.getFailCount()).isEqualTo(1);
	}

	@Test
	void notRegistered() {
		given(otpDeviceRepository.findByMemberIdForUpdate(MEMBER_ID, OtpStatus.USABLE)).willReturn(Optional.empty());

		assertThat(checker.check(MEMBER_ID, code(STEP)).type()).isEqualTo(Type.NOT_REGISTERED);
	}

	// 발급만 받고 사용 등록 전이면 오류 횟수를 올리지 않고 거절
	@Test
	void issuedButNotActivated() {
		given(otpDeviceRepository.findByMemberIdForUpdate(MEMBER_ID, OtpStatus.USABLE)).willReturn(Optional.of(device));

		assertThat(checker.check(MEMBER_ID, code(STEP)).type()).isEqualTo(Type.NOT_ACTIVATED);
		assertThat(device.getFailCount()).isZero();
	}
}
