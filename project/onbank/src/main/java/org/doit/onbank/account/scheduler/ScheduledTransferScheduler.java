package org.doit.onbank.account.scheduler;

import java.time.LocalDateTime;

import org.doit.onbank.account.service.ScheduledTransferRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

// 예약이체 스케줄러 : 매분 0초에 실행할 때가 된 예약이체를 실행한다 (기획서 5.4 "Spring Scheduler")
// @EnableScheduling 은 공용 config/SchedulingConfig (팀3, 10-20) 에 있으므로 여기에는 두지 않는다
// 끄기 : application-local.properties 에 onbank.scheduled-transfer.enabled=false (없으면 켜짐, 이 작업만 꺼짐)
// 공용 DB를 쓰므로 팀원 여러 명이 서버를 띄우면 스케줄러도 여러 개 돈다
//   → 같은 건을 두 번 이체하지 않도록 ScheduledTransferService.execute() 가 행을 잠그고 상태를 다시 본다
@Component
@ConditionalOnProperty(name = "onbank.scheduled-transfer.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Log4j2
public class ScheduledTransferScheduler {

	private final ScheduledTransferRunner scheduledTransferRunner;

	@Scheduled(cron = "0 * * * * *")	// 초 분 시 일 월 요일 : 매분 0초
	public void runDueTransfers() {
		int succeeded = scheduledTransferRunner.runDue(LocalDateTime.now());
		if (succeeded > 0) {
			log.info("예약이체 {}건 실행", succeeded);
		}
	}
}
