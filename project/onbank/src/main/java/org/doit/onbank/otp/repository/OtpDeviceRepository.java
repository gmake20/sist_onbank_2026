package org.doit.onbank.otp.repository;

import java.util.Collection;
import java.util.Optional;

import org.doit.onbank.otp.entity.OtpDevice;
import org.doit.onbank.otp.entity.OtpStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface OtpDeviceRepository extends JpaRepository<OtpDevice, Long> {

	// 회원의 사용 가능한 OTP 1개를 잠그고 조회 (statuses 에는 OtpStatus.USABLE 을 넘긴다)
	// 같은 번호로 동시에 두 번 검증해도 한쪽만 통과하도록, 트랜잭션이 끝날 때까지 다른 검증은 기다린다
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select d from OtpDevice d where d.memberId = :memberId and d.status in :statuses")
	Optional<OtpDevice> findByMemberIdForUpdate(@Param("memberId") Long memberId,
			@Param("statuses") Collection<OtpStatus> statuses);

	// 화면 표시용 조회 (잠그지 않음)
	Optional<OtpDevice> findByMemberIdAndStatusIn(Long memberId, Collection<OtpStatus> statuses);

	// 일련번호 중복 검사 (uk_otp_device_serial)
	boolean existsBySerialNo(String serialNo);
}
