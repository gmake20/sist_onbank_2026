package org.doit.onbank.account.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;

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
}
