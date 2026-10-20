package org.doit.onbank.account.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;

import org.doit.onbank.account.entity.ScheduledTransfer;
import org.doit.onbank.account.entity.ScheduledTransferStatus;
import org.doit.onbank.account.repository.ScheduledTransferRepository;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

// 예약이체 실행 순서 담당 : 실행할 건을 찾아 1건씩 실행하고, 실패하면 사유를 남긴다
// 이 클래스에는 @Transactional 을 붙이지 않는다
//   1건씩 따로 커밋해야 한 건이 실패해도 나머지가 실행되고,
//   실패한 트랜잭션은 롤백 전용이 되어 같은 트랜잭션에서는 실패 기록도 저장할 수 없기 때문
//   (트랜잭션은 ScheduledTransferService.execute() / recordFailure() 가 각각 연다)
@Service
@RequiredArgsConstructor
@Log4j2
public class ScheduledTransferRunner {

	static final String UNKNOWN_FAILURE = "일시적인 오류로 이체하지 못했습니다.";

	private final ScheduledTransferService scheduledTransferService;
	private final ScheduledTransferRepository scheduledTransferRepository;

	// 실행할 때가 된 대기 건을 모두 실행 (스케줄러가 1분마다 호출). 돌려주는 값 : 이체에 성공한 건수
	public int runDue(LocalDateTime now) {
		List<Long> ids = scheduledTransferRepository.findDueIds(ScheduledTransferStatus.WAITING, now);
		int succeeded = 0;
		for (Long id : ids) {
			try {
				if (runOne(id, now, false)) {
					succeeded++;
				}
			} catch (RuntimeException e) {
				// 실패 기록조차 못 한 경우 (DB 연결 문제 등) : 다음 건은 계속 실행하고, 이 건은 다음 분에 다시 시도된다
				log.error("예약이체 {} 처리 중 오류", id, e);
			}
		}
		return succeeded;
	}

	// [지금 실행] (시연용) : 본인의 대기 건을 실행 일시를 기다리지 않고 바로 실행한 뒤 결과를 돌려준다
	public ScheduledTransfer runNow(Long memberId, Long scheduledTransferId) {
		ScheduledTransfer transfer = scheduledTransferService.getMine(memberId, scheduledTransferId);
		if (transfer.getStatus() != ScheduledTransferStatus.WAITING) {
			throw new IllegalStateException("대기 중인 예약이체만 실행할 수 있습니다. (현재 상태: " + transfer.getStatus().getLabel() + ")");
		}
		runOne(scheduledTransferId, LocalDateTime.now(), true);
		return scheduledTransferService.getMine(memberId, scheduledTransferId);	// 바뀐 상태·결과를 다시 읽는다
	}

	// 1건 실행 : 이체 실패(잔액 부족, 한도 초과, 계좌 동결 등)는 사유를 남기고 false
	boolean runOne(Long id, LocalDateTime now, boolean force) {
		try {
			boolean done = scheduledTransferService.execute(id, now, force);
			if (done) {
				log.info("예약이체 {} 실행 완료", id);
			}
			return done;
		} catch (IllegalArgumentException | IllegalStateException | NoSuchElementException e) {
			// 사용자에게 보여 줄 수 있는 업무 오류 : 문구를 그대로 사유로 남긴다
			log.info("예약이체 {} 실행 실패 : {}", id, e.getMessage());
			scheduledTransferService.recordFailure(id, e.getMessage(), now);
			return false;
		} catch (RuntimeException e) {
			// 예상하지 못한 오류 : 내부 메시지는 로그에만 남기고 화면에는 일반 문구
			log.error("예약이체 {} 실행 중 예상하지 못한 오류", id, e);
			scheduledTransferService.recordFailure(id, UNKNOWN_FAILURE, now);
			return false;
		}
	}
}
