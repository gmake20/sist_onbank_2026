package org.doit.onbank.account.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.doit.onbank.account.entity.ScheduledTransfer;
import org.doit.onbank.account.entity.ScheduledTransferStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface ScheduledTransferRepository extends JpaRepository<ScheduledTransfer, Long> {

	// 내 예약·정기이체 목록 : 최근 등록한 것부터
	List<ScheduledTransfer> findByMemberIdOrderByScheduledTransferIdDesc(Long memberId);

	// 중복 등록 확인 : 출금계좌·입금계좌·금액·실행 일시가 모두 같은 대기 건 (이체 화면 안내 "중복등록이 불가합니다")
	boolean existsByAccountIdAndDepositAccountNumberAndTransferAmountAndNextExecuteAtAndStatus(Long accountId,
			String depositAccountNumber, Long transferAmount, LocalDateTime nextExecuteAt, ScheduledTransferStatus status);

	// 취소 등 : 본인 것인지 함께 확인
	Optional<ScheduledTransfer> findByScheduledTransferIdAndMemberId(Long scheduledTransferId, Long memberId);

	// 스케줄러 : 실행할 때가 된 대기 건의 ID (실행 일시 순)
	@Query("select s.scheduledTransferId from ScheduledTransfer s"
			+ " where s.status = :status and s.nextExecuteAt <= :now order by s.nextExecuteAt")
	List<Long> findDueIds(@Param("status") ScheduledTransferStatus status, @Param("now") LocalDateTime now);

	// 스케줄러 : 1건을 잠그고 다시 조회 (SELECT ... FOR UPDATE)
	// 서버가 두 대 떠 있거나 [지금 실행]과 스케줄러가 겹쳐도 같은 건을 두 번 이체하지 않도록 잠근 뒤 상태를 다시 본다
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select s from ScheduledTransfer s where s.scheduledTransferId = :id")
	Optional<ScheduledTransfer> findByIdForUpdate(@Param("id") Long id);
}
