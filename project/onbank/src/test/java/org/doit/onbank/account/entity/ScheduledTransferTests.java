package org.doit.onbank.account.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import org.junit.jupiter.api.Test;

// ScheduledTransfer 상태 변경 규칙 (DB 없이 엔티티만)
class ScheduledTransferTests {

	private final LocalDateTime now = LocalDateTime.of(2026, 10, 20, 15, 0);

	private ScheduledTransfer reserved(LocalDateTime executeAt) {
		return ScheduledTransfer.reserve(1L, 10L, "1002-222-222222", 50_000L, null, null, executeAt, now);
	}

	@Test
	void reserveStartsWaiting() {
		ScheduledTransfer transfer = reserved(now.plusMinutes(10));

		assertThat(transfer.getScheduleType()).isEqualTo(ScheduleType.SCHEDULED);
		assertThat(transfer.getStatus()).isEqualTo(ScheduledTransferStatus.WAITING);
		assertThat(transfer.getCreatedAt()).isEqualTo(now);
	}

	@Test
	void isDueOnlyWhenWaitingAndTimeHasCome() {
		ScheduledTransfer transfer = reserved(now.plusMinutes(10));

		assertThat(transfer.isDue(now)).isFalse();					// 아직 10분 전
		assertThat(transfer.isDue(now.plusMinutes(10))).isTrue();	// 정각
		transfer.cancel(now);
		assertThat(transfer.isDue(now.plusMinutes(20))).isFalse();	// 취소하면 실행하지 않음
	}

	@Test
	void cancelOnlyWaiting() {
		ScheduledTransfer done = reserved(now);
		done.markSucceeded(now);

		assertThatThrownBy(() -> done.cancel(now))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("완료");
	}

	@Test
	void markFailedKeepsReasonWithinColumnLength() {
		ScheduledTransfer transfer = reserved(now);

		transfer.markFailed("잔액이 부족합니다.".repeat(30), now);

		assertThat(transfer.getStatus()).isEqualTo(ScheduledTransferStatus.FAILED);
		assertThat(transfer.getLastResult()).hasSize(ScheduledTransfer.MAX_RESULT_LENGTH);
		assertThat(transfer.getLastExecutedAt()).isEqualTo(now);
	}

	// ---------- 정기이체 회차 계산 ----------

	private static final LocalTime NINE = LocalTime.of(9, 0);

	@Test
	void nextOccurrenceThisMonthIfNotYetPassed() {
		assertThat(ScheduledTransfer.nextOccurrence(25, NINE, now))	// 10-20 15:00 기준 25일
				.isEqualTo(LocalDateTime.of(2026, 10, 25, 9, 0));
	}

	@Test
	void nextOccurrenceNextMonthIfPassed() {
		assertThat(ScheduledTransfer.nextOccurrence(20, NINE, now))	// 오늘 09:00 은 이미 지남
				.isEqualTo(LocalDateTime.of(2026, 11, 20, 9, 0));
		assertThat(ScheduledTransfer.nextOccurrence(20, NINE, LocalDateTime.of(2026, 10, 20, 9, 0)))	// 같은 시각도 지난 것
				.isEqualTo(LocalDateTime.of(2026, 11, 20, 9, 0));
	}

	// 그 달에 없는 날은 말일 : 31일 → 11월 30일, 2월 28일(윤년 29일)
	@Test
	void nextOccurrenceUsesLastDayOfShortMonth() {
		assertThat(ScheduledTransfer.nextOccurrence(31, NINE, LocalDateTime.of(2026, 11, 1, 0, 0)))
				.isEqualTo(LocalDateTime.of(2026, 11, 30, 9, 0));
		assertThat(ScheduledTransfer.nextOccurrence(31, NINE, LocalDateTime.of(2027, 2, 1, 0, 0)))
				.isEqualTo(LocalDateTime.of(2027, 2, 28, 9, 0));
		assertThat(ScheduledTransfer.nextOccurrence(30, NINE, LocalDateTime.of(2028, 2, 1, 0, 0)))
				.isEqualTo(LocalDateTime.of(2028, 2, 29, 9, 0));
	}

	// 12월 다음은 다음 해 1월
	@Test
	void nextOccurrenceCrossesYear() {
		assertThat(ScheduledTransfer.nextOccurrence(10, NINE, LocalDateTime.of(2026, 12, 15, 0, 0)))
				.isEqualTo(LocalDateTime.of(2027, 1, 10, 9, 0));
	}

	// ---------- 정기이체 실행 후 상태 ----------

	private ScheduledTransfer recurring(int day, LocalDateTime firstAt, LocalDate endDate) {
		return ScheduledTransfer.subscribe(1L, 10L, "1002-222-222222", 50_000L, null, null, day, firstAt, endDate, now);
	}

	@Test
	void subscribeStartsWaitingAsRecurring() {
		ScheduledTransfer transfer = recurring(25, LocalDateTime.of(2026, 10, 25, 9, 0), null);

		assertThat(transfer.getScheduleType()).isEqualTo(ScheduleType.RECURRING);
		assertThat(transfer.getStatus()).isEqualTo(ScheduledTransferStatus.WAITING);
		assertThat(transfer.getTransferDay()).isEqualTo(25);
		assertThat(transfer.isRecurring()).isTrue();
	}

	// 성공 : 대기 그대로, 다음 달 같은 날·시각으로
	@Test
	void recurringSuccessMovesToNextMonth() {
		LocalDateTime round = LocalDateTime.of(2026, 10, 25, 9, 0);
		ScheduledTransfer transfer = recurring(25, round, null);

		transfer.markSucceeded(round);

		assertThat(transfer.getStatus()).isEqualTo(ScheduledTransferStatus.WAITING);
		assertThat(transfer.getNextExecuteAt()).isEqualTo(LocalDateTime.of(2026, 11, 25, 9, 0));
		assertThat(transfer.isLastSucceeded()).isTrue();
		assertThat(transfer.getLastExecutedAt()).isEqualTo(round);
	}

	// 실패 : 그 회차만 사유를 남기고 대기 유지, 다음 달로
	@Test
	void recurringFailureKeepsWaitingAndMovesOn() {
		LocalDateTime round = LocalDateTime.of(2026, 10, 25, 9, 0);
		ScheduledTransfer transfer = recurring(25, round, null);

		transfer.markFailed("잔액이 부족합니다.", round);

		assertThat(transfer.getStatus()).isEqualTo(ScheduledTransferStatus.WAITING);
		assertThat(transfer.getNextExecuteAt()).isEqualTo(LocalDateTime.of(2026, 11, 25, 9, 0));
		assertThat(transfer.getLastResult()).isEqualTo("실패 : 잔액이 부족합니다.");
		assertThat(transfer.isLastSucceeded()).isFalse();
	}

	// 31일 정기이체 : 11월은 30일에 실행했어도 12월은 다시 31일
	@Test
	void recurringReturnsToTransferDayAfterShortMonth() {
		LocalDateTime round = LocalDateTime.of(2026, 11, 30, 9, 0);
		ScheduledTransfer transfer = recurring(31, round, null);

		transfer.markSucceeded(round);

		assertThat(transfer.getNextExecuteAt()).isEqualTo(LocalDateTime.of(2026, 12, 31, 9, 0));
	}

	// [지금 실행]으로 미리 실행 : 이번 회차를 쓴 것이므로 그다음 달로
	@Test
	void recurringEarlyRunSkipsThatRound() {
		ScheduledTransfer transfer = recurring(25, LocalDateTime.of(2026, 11, 25, 9, 0), null);

		transfer.markSucceeded(LocalDateTime.of(2026, 10, 21, 14, 0));

		assertThat(transfer.getNextExecuteAt()).isEqualTo(LocalDateTime.of(2026, 12, 25, 9, 0));
	}

	// 서버가 몇 달 꺼져 있었으면 밀린 회차를 몰아서 이체하지 않고 지금 이후 회차로
	@Test
	void recurringAfterLongOutageSkipsMissedRounds() {
		ScheduledTransfer transfer = recurring(25, LocalDateTime.of(2026, 7, 25, 9, 0), null);

		transfer.markSucceeded(LocalDateTime.of(2026, 10, 21, 9, 1));

		assertThat(transfer.getNextExecuteAt()).isEqualTo(LocalDateTime.of(2026, 10, 25, 9, 0));
	}

	// 다음 회차가 종료일을 넘으면 완료
	@Test
	void recurringCompletesAfterEndDate() {
		LocalDateTime round = LocalDateTime.of(2026, 12, 25, 9, 0);
		ScheduledTransfer transfer = recurring(25, round, LocalDate.of(2027, 1, 24));

		transfer.markSucceeded(round);

		assertThat(transfer.getStatus()).isEqualTo(ScheduledTransferStatus.COMPLETED);
		assertThat(transfer.getNextExecuteAt()).isEqualTo(round);	// 마지막 회차 일시 그대로
		assertThat(transfer.isDue(round.plusMonths(1))).isFalse();
	}

	// 종료일 당일 회차는 실행한다
	@Test
	void recurringRunsOnEndDate() {
		LocalDateTime round = LocalDateTime.of(2026, 12, 25, 9, 0);
		ScheduledTransfer transfer = recurring(25, round, LocalDate.of(2027, 1, 25));

		transfer.markSucceeded(round);

		assertThat(transfer.getStatus()).isEqualTo(ScheduledTransferStatus.WAITING);
		assertThat(transfer.getNextExecuteAt()).isEqualTo(LocalDateTime.of(2027, 1, 25, 9, 0));
	}

	@Test
	void recurringCanBeCancelled() {
		ScheduledTransfer transfer = recurring(25, LocalDateTime.of(2026, 10, 25, 9, 0), null);

		transfer.cancel(now);

		assertThat(transfer.getStatus()).isEqualTo(ScheduledTransferStatus.CANCELLED);
	}
}
