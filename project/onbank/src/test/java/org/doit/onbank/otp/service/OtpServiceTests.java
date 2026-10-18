package org.doit.onbank.otp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import org.doit.onbank.common.exception.BusinessException;
import org.doit.onbank.otp.dto.OtpIssueResult;
import org.doit.onbank.otp.entity.OtpDevice;
import org.doit.onbank.otp.entity.OtpStatus;
import org.doit.onbank.otp.repository.OtpDeviceRepository;
import org.doit.onbank.otp.service.OtpCheckResult.Type;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

// OtpService 단위 테스트 : DB 대신 가짜(Mock) Repository, 시각은 고정
@ExtendWith(MockitoExtension.class)
class OtpServiceTests {

	private static final Long MEMBER_ID = 100L;
	private static final long STEP = 56_666_667L;
	private static final Instant NOW = Instant.ofEpochSecond(STEP * TotpGenerator.STEP_SECONDS + 10);
	private static final byte[] SEED = "12345678901234567890".getBytes();

	@Mock
	private OtpDeviceRepository otpDeviceRepository;

	@Mock
	private OtpCodeChecker otpCodeChecker;

	private final OtpSeedCipher cipher = new OtpSeedCipher(OtpSeedCipherTests.TEST_KEY);

	private OtpService service;

	@BeforeEach
	void setUp() {
		service = new OtpService(otpDeviceRepository, cipher, otpCodeChecker, Clock.fixed(NOW, ZoneId.of("Asia/Seoul")));
	}

	private OtpDevice issuedDevice() {
		return OtpDevice.builder()
				.memberId(MEMBER_ID)
				.serialNo("0000000001")
				.seedEncrypted(cipher.encrypt(SEED))
				.build();
	}

	private void currentDeviceIs(OtpDevice device) {
		given(otpDeviceRepository.findByMemberIdForUpdate(MEMBER_ID, OtpStatus.USABLE)).willReturn(Optional.ofNullable(device));
	}

	// ---------- 발급 ----------

	// 등록 코드로 시드를 되살리면 DB에 암호화해 저장한 시드와 같다 → OTP 앱에 넣으면 서버와 같은 번호가 나온다
	@Test
	void issueSavesEncryptedSeedAndReturnsRegistrationCode() {
		currentDeviceIs(null);
		given(otpDeviceRepository.existsBySerialNo(anyString())).willReturn(false);

		OtpIssueResult result = service.issue(MEMBER_ID);

		ArgumentCaptor<OtpDevice> saved = ArgumentCaptor.forClass(OtpDevice.class);
		verify(otpDeviceRepository).saveAndFlush(saved.capture());
		OtpDevice device = saved.getValue();

		assertThat(device.getMemberId()).isEqualTo(MEMBER_ID);
		assertThat(device.getStatus()).isEqualTo(OtpStatus.ISSUED);
		assertThat(device.getSerialNo()).isEqualTo(result.serialNo()).matches("\\d{10}");
		assertThat(cipher.decrypt(device.getSeedEncrypted())).isEqualTo(Base32.decode(result.registrationCode()));
		assertThat(result.otpauthUri())
				.startsWith("otpauth://totp/OnBank:" + result.serialNo() + "?")
				.contains("secret=" + result.registrationCode());
	}

	// 등록 전 기기는 폐기하고 다시 발급 (폐기를 먼저 flush 해야 회원당 1개 제약에 걸리지 않음)
	@Test
	void issueDisposesUnactivatedDevice() {
		OtpDevice old = issuedDevice();
		currentDeviceIs(old);
		given(otpDeviceRepository.existsBySerialNo(anyString())).willReturn(false);

		service.issue(MEMBER_ID);

		assertThat(old.getStatus()).isEqualTo(OtpStatus.DISPOSED);
		verify(otpDeviceRepository).flush();
	}

	@Test
	void issueRejectedWhenActiveDeviceExists() {
		OtpDevice active = issuedDevice();
		active.activate(STEP, LocalDateTime.now());
		currentDeviceIs(active);

		assertThatThrownBy(() -> service.issue(MEMBER_ID)).isInstanceOf(BusinessException.class);
		verify(otpDeviceRepository, never()).saveAndFlush(any());
	}

	// ---------- 사용 등록 ----------

	@Test
	void activateWithTwoConsecutiveCodes() {
		OtpDevice device = issuedDevice();
		currentDeviceIs(device);

		service.activate(MEMBER_ID, TotpGenerator.generate(SEED, STEP - 1), TotpGenerator.generate(SEED, STEP));

		assertThat(device.getStatus()).isEqualTo(OtpStatus.ACTIVE);
		assertThat(device.getActivatedAt()).isNotNull();
		assertThat(device.getLastUsedStep()).isEqualTo(STEP);	// 등록에 쓴 번호는 이체에 다시 쓸 수 없음
	}

	// 같은 번호를 두 번 넣으면 안 됨 (연속된 두 번호여야 함)
	@Test
	void activateRejectsSameCodeTwice() {
		OtpDevice device = issuedDevice();
		currentDeviceIs(device);
		String code = TotpGenerator.generate(SEED, STEP);

		assertThatThrownBy(() -> service.activate(MEMBER_ID, code, code)).isInstanceOf(BusinessException.class);
		assertThat(device.getStatus()).isEqualTo(OtpStatus.ISSUED);
	}

	@Test
	void activateRejectsAlreadyActiveDevice() {
		OtpDevice device = issuedDevice();
		device.activate(STEP - 5, LocalDateTime.now());
		currentDeviceIs(device);

		assertThatThrownBy(() -> service.activate(MEMBER_ID,
				TotpGenerator.generate(SEED, STEP - 1), TotpGenerator.generate(SEED, STEP)))
				.isInstanceOf(BusinessException.class);
	}

	// ---------- 검증 ----------

	@Test
	void verifyPassesOnOk() {
		given(otpCodeChecker.check(MEMBER_ID, "123456")).willReturn(OtpCheckResult.of(Type.OK));

		assertThatCode(() -> service.verify(MEMBER_ID, "123456")).doesNotThrowAnyException();
	}

	@Test
	void verifyThrowsWithFailCountOnMismatch() {
		given(otpCodeChecker.check(MEMBER_ID, "000000")).willReturn(new OtpCheckResult(Type.MISMATCH, 3));

		assertThatThrownBy(() -> service.verify(MEMBER_ID, "000000"))
				.isInstanceOf(BusinessException.class)
				.hasMessageContaining("3/10");
	}

	@Test
	void verifyThrowsWhenNotRegistered() {
		given(otpCodeChecker.check(MEMBER_ID, "123456")).willReturn(OtpCheckResult.of(Type.NOT_REGISTERED));

		assertThatThrownBy(() -> service.verify(MEMBER_ID, "123456")).isInstanceOf(BusinessException.class);
	}
}
