package org.doit.onbank.account.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.util.List;

import org.doit.onbank.account.entity.ScheduledTransfer;
import org.doit.onbank.account.entity.ScheduledTransferStatus;
import org.doit.onbank.account.repository.ScheduledTransferRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;

// ScheduledTransferRunner 단위 테스트 : 실행 순서와 실패 처리
// 확인하는 것 : 한 건이 실패해도 다음 건은 실행, 실패 사유 기록, 예상 못 한 오류는 일반 문구
@ExtendWith(MockitoExtension.class)
class ScheduledTransferRunnerTests {

	private static final Long MY_ID = 100L;
	private final LocalDateTime now = LocalDateTime.of(2026, 10, 21, 9, 30);

	@Mock
	private ScheduledTransferService scheduledTransferService;

	@Mock
	private ScheduledTransferRepository scheduledTransferRepository;

	@InjectMocks
	private ScheduledTransferRunner runner;

	// 3건 중 가운데가 잔액 부족 : 실패 사유를 남기고 나머지 2건은 실행
	@Test
	void runDueContinuesAfterFailure() {
		given(scheduledTransferRepository.findDueIds(ScheduledTransferStatus.WAITING, now)).willReturn(List.of(1L, 2L, 3L));
		given(scheduledTransferService.execute(1L, now, false)).willReturn(true);
		given(scheduledTransferService.execute(2L, now, false)).willThrow(new IllegalStateException("잔액이 부족합니다."));
		given(scheduledTransferService.execute(3L, now, false)).willReturn(true);

		int succeeded = runner.runDue(now);

		assertThat(succeeded).isEqualTo(2);
		verify(scheduledTransferService).recordFailure(2L, "잔액이 부족합니다.", now, false);
	}

	// DB 오류 같은 예상 못 한 오류 : 내부 메시지 대신 일반 문구를 남긴다
	@Test
	void unexpectedErrorRecordsGenericMessage() {
		given(scheduledTransferRepository.findDueIds(ScheduledTransferStatus.WAITING, now)).willReturn(List.of(1L));
		given(scheduledTransferService.execute(1L, now, false))
				.willThrow(new DataAccessResourceFailureException("Communications link failure"));

		runner.runDue(now);

		verify(scheduledTransferService).recordFailure(1L, ScheduledTransferRunner.UNKNOWN_FAILURE, now, false);
	}

	// 실행할 건이 없으면 아무것도 하지 않는다
	@Test
	void runDueWithNothingToDo() {
		given(scheduledTransferRepository.findDueIds(ScheduledTransferStatus.WAITING, now)).willReturn(List.of());

		assertThat(runner.runDue(now)).isZero();
		verify(scheduledTransferService, never()).execute(anyLong(), any(), any(Boolean.class));
	}

	// [지금 실행] : 대기 건이 아니면 실행하지 않는다
	@Test
	void runNowRejectsNotWaiting() {
		ScheduledTransfer cancelled = ScheduledTransfer.reserve(MY_ID, 1L, "1002-222-222222", 50_000L, null, null,
				now.plusDays(1), now);
		cancelled.cancel(now);
		given(scheduledTransferService.getMine(MY_ID, 7L)).willReturn(cancelled);

		assertThatThrownBy(() -> runner.runNow(MY_ID, 7L))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("취소");
		verify(scheduledTransferService, never()).execute(anyLong(), any(), any(Boolean.class));
	}
}
