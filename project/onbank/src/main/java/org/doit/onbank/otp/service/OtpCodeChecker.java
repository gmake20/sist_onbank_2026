package org.doit.onbank.otp.service;

import java.time.Clock;

import org.doit.onbank.otp.entity.OtpDevice;
import org.doit.onbank.otp.entity.OtpStatus;
import org.doit.onbank.otp.repository.OtpDeviceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// OTP 번호 검증 (이체할 때 OtpService.verify 가 호출)
// REQUIRES_NEW : 이체 트랜잭션과 따로 커밋한다 → 이체가 롤백돼도 오류 횟수는 남는다
// OtpService 안의 메서드로 두면 프록시를 거치지 않아 REQUIRES_NEW 가 무시되므로 별도 Bean 으로 분리
// (AccountPasswordVerifier 와 같은 구조)
@Component
public class OtpCodeChecker {

	private final OtpDeviceRepository otpDeviceRepository;
	private final OtpSeedCipher otpSeedCipher;
	private final Clock clock;

	@Autowired
	public OtpCodeChecker(OtpDeviceRepository otpDeviceRepository, OtpSeedCipher otpSeedCipher) {
		this(otpDeviceRepository, otpSeedCipher, Clock.systemDefaultZone());
	}

	// 테스트에서 시각을 고정할 때 사용
	OtpCodeChecker(OtpDeviceRepository otpDeviceRepository, OtpSeedCipher otpSeedCipher, Clock clock) {
		this.otpDeviceRepository = otpDeviceRepository;
		this.otpSeedCipher = otpSeedCipher;
		this.clock = clock;
	}

	// 오류 횟수를 커밋해야 하므로 번호가 틀린 경우는 예외 대신 결과를 돌려준다
	// (이 메서드 안에서 예외를 던지면 REQUIRES_NEW 트랜잭션이 롤백되어 증가한 횟수가 사라진다)
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public OtpCheckResult check(Long memberId, String code) {
		OtpDevice device = otpDeviceRepository.findByMemberIdForUpdate(memberId, OtpStatus.USABLE).orElse(null);
		if (device == null) {
			return OtpCheckResult.of(OtpCheckResult.Type.NOT_REGISTERED);
		}
		if (device.getStatus() == OtpStatus.ISSUED) {
			return OtpCheckResult.of(OtpCheckResult.Type.NOT_ACTIVATED);
		}
		if (device.getStatus() == OtpStatus.SUSPENDED) {
			return OtpCheckResult.of(OtpCheckResult.Type.SUSPENDED);
		}

		byte[] seed = otpSeedCipher.decrypt(device.getSeedEncrypted());
		long current = TotpGenerator.step(clock.instant());
		boolean reused = false;

		// 기기 시계가 조금 틀릴 수 있으므로 앞뒤 1구간(±30초)까지 허용
		for (long step = current - 1; step <= current + 1; step++) {
			if (!TotpGenerator.matches(seed, step, code)) {
				continue;
			}
			if (device.isStepUsed(step)) {
				reused = true;	// 이미 쓴 번호 (같은 화면에서 두 번 제출 등) → 다른 구간도 마저 확인
				continue;
			}
			device.recordSuccess(step);
			return OtpCheckResult.of(OtpCheckResult.Type.OK);
		}
		if (reused) {
			return OtpCheckResult.of(OtpCheckResult.Type.REUSED);
		}

		boolean suspended = device.recordFailure();
		return new OtpCheckResult(suspended ? OtpCheckResult.Type.SUSPENDED_NOW : OtpCheckResult.Type.MISMATCH,
				device.getFailCount());
	}
}
