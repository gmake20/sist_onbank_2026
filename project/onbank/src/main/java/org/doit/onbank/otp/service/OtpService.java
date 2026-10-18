package org.doit.onbank.otp.service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

import org.doit.onbank.common.exception.BusinessException;
import org.doit.onbank.otp.dto.OtpDeviceInfo;
import org.doit.onbank.otp.dto.OtpIssueResult;
import org.doit.onbank.otp.entity.OtpDevice;
import org.doit.onbank.otp.entity.OtpStatus;
import org.doit.onbank.otp.repository.OtpDeviceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 가상 OTP 발급 · 사용 등록 · 검증 (auth.md 4장)
// ① issue    : 일련번호 + 시드 생성, 등록 코드를 1번만 돌려줌      → 상태 ISSUED
// ② activate : 기기에 표시된 번호를 연속 2회 입력                  → 상태 ACTIVE
// ③ verify   : 이체할 때 6자리 확인 (팀원2 이체 Service가 호출)
// TODO 본인확인(identity_verification)이 생기면 issue 에서 memberService.requireVerified() 호출
@Service
public class OtpService {

	private static final int SEED_BYTES = 20;		// 160비트 (RFC 4226 권장)
	private static final int SERIAL_DIGITS = 10;
	private static final String ISSUER = "OnBank";	// OTP 앱에 표시되는 이름

	private final OtpDeviceRepository otpDeviceRepository;
	private final OtpSeedCipher otpSeedCipher;
	private final OtpCodeChecker otpCodeChecker;
	private final Clock clock;
	private final SecureRandom random = new SecureRandom();

	@Autowired
	public OtpService(OtpDeviceRepository otpDeviceRepository, OtpSeedCipher otpSeedCipher,
			OtpCodeChecker otpCodeChecker) {
		this(otpDeviceRepository, otpSeedCipher, otpCodeChecker, Clock.systemDefaultZone());
	}

	// 테스트에서 시각을 고정할 때 사용
	OtpService(OtpDeviceRepository otpDeviceRepository, OtpSeedCipher otpSeedCipher,
			OtpCodeChecker otpCodeChecker, Clock clock) {
		this.otpDeviceRepository = otpDeviceRepository;
		this.otpSeedCipher = otpSeedCipher;
		this.otpCodeChecker = otpCodeChecker;
		this.clock = clock;
	}

	// ---------- 조회 ----------

	// 지금 가진 OTP (발급·사용중·정지). 없거나 분실·폐기뿐이면 empty
	@Transactional(readOnly = true)
	public Optional<OtpDeviceInfo> findCurrent(Long memberId) {
		return otpDeviceRepository.findByMemberIdAndStatusIn(memberId, OtpStatus.USABLE).map(OtpDeviceInfo::from);
	}

	// ---------- ① 발급 ----------

	// 사용 등록 전(ISSUED) 기기가 있으면 폐기하고 새로 발급한다 (등록 코드를 잃어버린 경우)
	// 사용중·정지 기기가 있으면 거절 → 분실 신고 후 재발급
	@Transactional
	public OtpIssueResult issue(Long memberId) {
		Optional<OtpDevice> current = otpDeviceRepository.findByMemberIdForUpdate(memberId, OtpStatus.USABLE);
		if (current.isPresent()) {
			OtpDevice device = current.get();
			if (device.getStatus() != OtpStatus.ISSUED) {
				throw new BusinessException("이미 등록된 OTP가 있습니다. 분실 신고 후 다시 발급받아 주세요.");
			}
			device.dispose();
			// Hibernate는 INSERT를 UPDATE보다 먼저 실행하므로, 폐기를 먼저 반영해야 회원당 1개 제약에 걸리지 않는다
			otpDeviceRepository.flush();
		}

		byte[] seed = new byte[SEED_BYTES];
		random.nextBytes(seed);
		String serialNo = newSerialNo();

		try {
			otpDeviceRepository.saveAndFlush(OtpDevice.builder()
					.memberId(memberId)
					.serialNo(serialNo)
					.seedEncrypted(otpSeedCipher.encrypt(seed))
					.build());
		} catch (DataIntegrityViolationException e) {
			// 같은 회원이 동시에 두 번 발급한 경우 ux_otp_device_one_usable 에서 걸린다
			throw new BusinessException("OTP 발급 중 문제가 생겼습니다. 다시 시도해 주세요.");
		}

		String registrationCode = Base32.encode(seed);
		return new OtpIssueResult(serialNo, registrationCode, otpauthUri(serialNo, registrationCode));
	}

	// ---------- ② 사용 등록 ----------

	// 기기에 표시된 번호를 연속 2회 입력 (첫 번호를 입력하고, 새 번호가 표시되면 그 번호를 입력)
	// 기기를 실제로 가지고 있는지, 기기 시계가 맞는지 확인한다
	@Transactional
	public void activate(Long memberId, String firstCode, String secondCode) {
		OtpDevice device = otpDeviceRepository.findByMemberIdForUpdate(memberId, OtpStatus.USABLE)
				.orElseThrow(() -> new BusinessException("발급받은 OTP가 없습니다. OTP를 먼저 발급받아 주세요."));
		if (device.getStatus() != OtpStatus.ISSUED) {
			throw new BusinessException("이미 사용 등록된 OTP입니다.");
		}

		byte[] seed = otpSeedCipher.decrypt(device.getSeedEncrypted());
		long current = TotpGenerator.step(clock.instant());

		// 두 번째 번호가 현재 구간 앞뒤 1구간 안에 있으면 통과 (첫 번호는 그 바로 앞 구간)
		for (long step = current - 2; step <= current; step++) {
			if (TotpGenerator.matches(seed, step, firstCode) && TotpGenerator.matches(seed, step + 1, secondCode)) {
				device.activate(step + 1, LocalDateTime.now(clock));
				return;
			}
		}
		throw new BusinessException("OTP 번호가 맞지 않습니다. 기기에 표시된 번호를 입력하고, 새 번호가 표시되면 그 번호를 이어서 입력해 주세요.");
	}

	// ---------- ③ 검증 (이체) ----------

	// 팀원2 이체 Service에서 호출 : 통과하면 그대로 끝, 아니면 BusinessException
	// 오류 횟수는 OtpCodeChecker 가 별도 트랜잭션으로 커밋하므로 이체가 롤백돼도 남는다
	// 이 메서드에는 @Transactional 을 붙이지 않는다 (이체 트랜잭션 안에서 불려도 결과만 보고 예외를 던짐)
	public void verify(Long memberId, String code) {
		OtpCheckResult result = otpCodeChecker.check(memberId, code);
		switch (result.type()) {
			case OK -> {
			}
			case MISMATCH -> throw new BusinessException("OTP 번호가 올바르지 않습니다. (오류 "
					+ result.failCount() + "/" + OtpDevice.MAX_FAIL_COUNT + "회)");
			case SUSPENDED_NOW -> throw new BusinessException("OTP 번호 오류 " + OtpDevice.MAX_FAIL_COUNT
					+ "회로 OTP 이용이 정지되었습니다.");
			case REUSED -> throw new BusinessException("이미 사용한 OTP 번호입니다. 새 번호가 표시되면 다시 입력해 주세요.");
			case NOT_REGISTERED -> throw new BusinessException("등록된 OTP가 없습니다. 보안설정에서 OTP를 발급받아 주세요.");
			case NOT_ACTIVATED -> throw new BusinessException("OTP 사용 등록을 먼저 완료해 주세요.");
			case SUSPENDED -> throw new BusinessException("OTP 번호 오류 " + OtpDevice.MAX_FAIL_COUNT
					+ "회로 정지된 OTP입니다. 정지 해제 또는 재발급이 필요합니다.");
		}
	}

	// ---------- 내부 ----------

	// 숫자 10자리 일련번호. 겹치면 다시 만든다 (100억 개 중 하나라 거의 겹치지 않음)
	private String newSerialNo() {
		for (int i = 0; i < 5; i++) {
			String serialNo = String.format("%0" + SERIAL_DIGITS + "d", random.nextLong(10_000_000_000L));
			if (!otpDeviceRepository.existsBySerialNo(serialNo)) {
				return serialNo;
			}
		}
		throw new IllegalStateException("OTP 일련번호를 만들지 못했습니다.");
	}

	// QR 코드용 TOTP 표준 형식 (Key Uri Format)
	private static String otpauthUri(String serialNo, String secret) {
		return "otpauth://totp/" + ISSUER + ":" + serialNo
				+ "?secret=" + secret
				+ "&issuer=" + ISSUER
				+ "&algorithm=SHA1&digits=" + TotpGenerator.DIGITS
				+ "&period=" + TotpGenerator.STEP_SECONDS;
	}
}
