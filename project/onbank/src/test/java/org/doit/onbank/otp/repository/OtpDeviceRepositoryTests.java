package org.doit.onbank.otp.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.doit.onbank.member.entity.Member;
import org.doit.onbank.member.repository.MemberRepository;
import org.doit.onbank.otp.entity.OtpDevice;
import org.doit.onbank.otp.entity.OtpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;

// 공용 DB의 otp_device 테이블과 엔티티가 맞는지, 조회·제약이 동작하는지 확인
// @DataJpaTest 는 테스트마다 롤백하므로 테스트 회원과 OTP는 공용 DB에 남지 않는다
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class OtpDeviceRepositoryTests {

	@Autowired
	private OtpDeviceRepository otpDeviceRepository;

	@Autowired
	private MemberRepository memberRepository;

	private final String suffix = String.valueOf(System.currentTimeMillis() % 100_000_000);
	private Long memberId;

	@BeforeEach
	void saveMember() {
		memberId = memberRepository.saveAndFlush(Member.builder()
				.loginId("otp" + suffix)
				.password("not-used")
				.name("OTP테스트")
				.email("otp" + suffix + "@onbank.test")
				.phone("010" + suffix)
				.birthDate(LocalDate.of(2000, 1, 1))
				.termsAgreedAt(LocalDateTime.now())
				.build()).getMemberId();
	}

	private OtpDevice newDevice(String serialNo) {
		return OtpDevice.builder()
				.memberId(memberId)
				.serialNo(serialNo)
				.seedEncrypted("dummy-encrypted-seed")
				.build();
	}

	@Test
	void saveAndFindUsableDevice() {
		OtpDevice saved = otpDeviceRepository.saveAndFlush(newDevice("T" + suffix));

		assertThat(saved.getIssuedAt()).isNotNull();	// Auditing 으로 채워짐 (NOT NULL 컬럼)
		assertThat(otpDeviceRepository.findByMemberIdForUpdate(memberId, OtpStatus.USABLE))
				.get().extracting(OtpDevice::getOtpDeviceId).isEqualTo(saved.getOtpDeviceId());
		assertThat(otpDeviceRepository.existsBySerialNo("T" + suffix)).isTrue();
	}

	// 사용 등록 후 상태·등록일시가 저장된다 (ck_otp_device_activated : ACTIVE 면 activated_at 필수)
	@Test
	void activatedDeviceSaved() {
		OtpDevice device = otpDeviceRepository.saveAndFlush(newDevice("T" + suffix));
		device.activate(56_666_667L, LocalDateTime.now());

		otpDeviceRepository.flush();

		assertThat(otpDeviceRepository.findByMemberIdForUpdate(memberId, OtpStatus.USABLE))
				.get().extracting(OtpDevice::getStatus).isEqualTo(OtpStatus.ACTIVE);
	}

	// 폐기된 기기는 사용 가능한 OTP로 조회되지 않는다
	@Test
	void disposedDeviceNotFound() {
		OtpDevice device = otpDeviceRepository.saveAndFlush(newDevice("T" + suffix));
		device.dispose();
		otpDeviceRepository.flush();

		assertThat(otpDeviceRepository.findByMemberIdForUpdate(memberId, OtpStatus.USABLE)).isEmpty();
	}

	// 회원당 사용 가능한 OTP는 1개 (ux_otp_device_one_usable)
	@Test
	void secondUsableDeviceRejected() {
		otpDeviceRepository.saveAndFlush(newDevice("T" + suffix));

		assertThatThrownBy(() -> otpDeviceRepository.saveAndFlush(newDevice("U" + suffix)))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	// 폐기한 뒤에는 새로 발급할 수 있다 (OtpService.issue 의 재발급 흐름)
	@Test
	void newDeviceAllowedAfterDispose() {
		OtpDevice old = otpDeviceRepository.saveAndFlush(newDevice("T" + suffix));
		old.dispose();
		otpDeviceRepository.flush();

		OtpDevice renewed = otpDeviceRepository.saveAndFlush(newDevice("U" + suffix));

		assertThat(renewed.getOtpDeviceId()).isNotNull();
	}
}
