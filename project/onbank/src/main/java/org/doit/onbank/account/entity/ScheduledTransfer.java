package org.doit.onbank.account.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

// 예약·정기이체 (기획서 5.4 예약이체, 5.5 정기이체)
// 등록할 때 계좌 비밀번호(·OTP)를 확인하고, 실행은 스케줄러가 인증 없이 한다
// 상태 변경은 아래 메서드로만 한다 (Service 에서 setter 로 값을 바꾸지 않도록)
@Entity
@Table(name = "scheduled_transfer")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)	// JPA용 기본 생성자
@ToString
public class ScheduledTransfer {

	public static final int MAX_RESULT_LENGTH = 200;	// last_result VARCHAR(200)
	public static final String SUCCESS_RESULT = "이체 완료";	// 실행 성공 시 last_result
	static final String RECURRING_FAILURE_PREFIX = "실패 : ";	// 정기 회차 실패는 상태가 대기로 남으므로 결과 문구로 구분

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)	// AUTO_INCREMENT
	@Column(nullable = false)
	private Long scheduledTransferId;		// 예약·정기이체 식별자

	private Long memberId;					// 등록한 회원

	private Long accountId;					// 출금 계좌

	@Column(length = 30)
	private String depositAccountNumber;	// 입금 계좌번호

	private Long transferAmount;			// 이체 금액

	@Column(length = 100)
	private String senderMemo;				// 보내는 분 통장 표시 내용 (비우면 실행할 때 받는 분 이름)

	@Column(length = 100)
	private String receiverMemo;			// 받는 분 통장 표시 내용 (비우면 실행할 때 내 이름)

	@Enumerated(EnumType.STRING)
	@Column(length = 20)
	private ScheduleType scheduleType;		// 예약 / 정기

	private LocalDateTime nextExecuteAt;	// 다음 실행 일시

	private Integer transferDay;			// 정기 : 매월 실행일 1~31

	private LocalDate endDate;				// 정기 : 종료일 (NULL 이면 해지할 때까지)

	@Enumerated(EnumType.STRING)
	@Column(length = 20)
	private ScheduledTransferStatus status;	// 상태

	private LocalDateTime lastExecutedAt;	// 마지막 실행 일시

	@Column(length = MAX_RESULT_LENGTH)
	private String lastResult;				// 마지막 실행 결과 (실패 사유 등)

	private LocalDateTime createdAt;		// 등록 일시

	private LocalDateTime updatedAt;		// 수정 일시

	@Builder
	private ScheduledTransfer(Long memberId, Long accountId, String depositAccountNumber, Long transferAmount,
			String senderMemo, String receiverMemo, ScheduleType scheduleType, LocalDateTime nextExecuteAt,
			Integer transferDay, LocalDate endDate, ScheduledTransferStatus status, LocalDateTime createdAt) {
		this.memberId = memberId;
		this.accountId = accountId;
		this.depositAccountNumber = depositAccountNumber;
		this.transferAmount = transferAmount;
		this.senderMemo = senderMemo;
		this.receiverMemo = receiverMemo;
		this.scheduleType = scheduleType;
		this.nextExecuteAt = nextExecuteAt;
		this.transferDay = transferDay;
		this.endDate = endDate;
		this.status = status;
		this.createdAt = createdAt;
		this.updatedAt = createdAt;
	}

	// 예약이체 등록 : 대기(WAITING) 상태로 만든다. 값 검사(미래 일시, 금액 등)는 Service 에서 한다
	public static ScheduledTransfer reserve(Long memberId, Long accountId, String depositAccountNumber, long amount,
			String senderMemo, String receiverMemo, LocalDateTime executeAt, LocalDateTime now) {
		return ScheduledTransfer.builder()
				.memberId(memberId)
				.accountId(accountId)
				.depositAccountNumber(depositAccountNumber)
				.transferAmount(amount)
				.senderMemo(senderMemo)
				.receiverMemo(receiverMemo)
				.scheduleType(ScheduleType.SCHEDULED)
				.nextExecuteAt(executeAt)
				.status(ScheduledTransferStatus.WAITING)
				.createdAt(now)
				.build();
	}

	// 정기이체 등록 : 대기(WAITING) 상태, 첫 회차 일시는 Service 가 nextOccurrence() 로 계산해서 넘긴다
	public static ScheduledTransfer subscribe(Long memberId, Long accountId, String depositAccountNumber, long amount,
			String senderMemo, String receiverMemo, int transferDay, LocalDateTime firstExecuteAt, LocalDate endDate,
			LocalDateTime now) {
		return ScheduledTransfer.builder()
				.memberId(memberId)
				.accountId(accountId)
				.depositAccountNumber(depositAccountNumber)
				.transferAmount(amount)
				.senderMemo(senderMemo)
				.receiverMemo(receiverMemo)
				.scheduleType(ScheduleType.RECURRING)
				.nextExecuteAt(firstExecuteAt)
				.transferDay(transferDay)
				.endDate(endDate)
				.status(ScheduledTransferStatus.WAITING)
				.createdAt(now)
				.build();
	}

	// 정기이체 회차 계산 : after 보다 뒤인 가장 가까운 "매월 transferDay일 time"
	// 이번 달 그 일시가 아직 안 왔으면 이번 달, 지났으면(같은 시각 포함) 다음 달
	// 그 달에 없는 날(예: 31일, 2월 29~30일)이면 그 달 말일
	public static LocalDateTime nextOccurrence(int transferDay, LocalTime time, LocalDateTime after) {
		YearMonth month = YearMonth.from(after);
		LocalDateTime candidate = occurrenceIn(month, transferDay, time);
		return candidate.isAfter(after) ? candidate : occurrenceIn(month.plusMonths(1), transferDay, time);
	}

	private static LocalDateTime occurrenceIn(YearMonth month, int transferDay, LocalTime time) {
		return month.atDay(Math.min(transferDay, month.lengthOfMonth())).atTime(time);
	}

	public boolean isRecurring() {
		return scheduleType == ScheduleType.RECURRING;
	}

	// 마지막 실행이 성공했는지 : 정기이체는 성공해도 상태가 대기로 남으므로 결과 문구로 본다
	public boolean isLastSucceeded() {
		return SUCCESS_RESULT.equals(lastResult);
	}

	// 실행할 때가 됐는지 : 대기 상태이고 실행 일시가 지났으면
	public boolean isDue(LocalDateTime now) {
		return status == ScheduledTransferStatus.WAITING && nextExecuteAt != null && !nextExecuteAt.isAfter(now);
	}

	// 취소 : 대기·중지 상태만. 실행이 끝난 건(완료·실패)이나 이미 취소한 건은 취소할 수 없다
	public void cancel(LocalDateTime now) {
		if (status != ScheduledTransferStatus.WAITING && status != ScheduledTransferStatus.PAUSED) {
			throw new IllegalStateException("대기 중인 이체만 취소할 수 있습니다. (현재 상태: " + status.getLabel() + ")");
		}
		this.status = ScheduledTransferStatus.CANCELLED;
		this.updatedAt = now;
	}

	// 실행 성공 : 예약이체는 완료로 끝난다. 정기이체는 다음 회차로 넘어간다
	public void markSucceeded(LocalDateTime now) {
		this.lastExecutedAt = now;
		this.lastResult = SUCCESS_RESULT;
		this.updatedAt = now;
		if (isRecurring()) {
			moveToNextRound(now);
		} else {
			this.status = ScheduledTransferStatus.COMPLETED;
		}
	}

	// 실행 실패 : 예약이체는 실패로 끝난다. 사유는 목록에 보여 준다
	// 정기이체는 그 회차만 사유를 남기고 다음 회차로 넘어간다 (재시도 없음, 상태는 대기 유지)
	public void markFailed(String reason, LocalDateTime now) {
		String text = (reason == null || reason.isBlank()) ? "이체 실패" : reason;
		this.lastExecutedAt = now;
		this.updatedAt = now;
		if (isRecurring()) {
			this.lastResult = truncate(RECURRING_FAILURE_PREFIX + text);
			moveToNextRound(now);
		} else {
			this.status = ScheduledTransferStatus.FAILED;
			this.lastResult = truncate(text);
		}
	}

	// 다음 회차 : 이번 회차 일시와 지금 중 늦은 쪽 다음의 회차
	//   제때 실행 → 다음 달 / [지금 실행]으로 미리 실행 → 이번 회차를 쓴 것이므로 그다음 달
	//   서버가 몇 달 꺼져 있었으면 밀린 회차를 한꺼번에 이체하지 않고 지금 이후 회차로 넘어간다
	// 종료일을 넘으면 완료로 끝낸다 (next_execute_at 은 마지막 회차 일시로 둔다)
	private void moveToNextRound(LocalDateTime now) {
		LocalDateTime base = nextExecuteAt.isAfter(now) ? nextExecuteAt : now;
		LocalDateTime next = nextOccurrence(transferDay, nextExecuteAt.toLocalTime(), base);
		if (endDate != null && next.toLocalDate().isAfter(endDate)) {
			this.status = ScheduledTransferStatus.COMPLETED;
			return;
		}
		this.nextExecuteAt = next;
	}

	private static String truncate(String text) {
		return text.length() <= MAX_RESULT_LENGTH ? text : text.substring(0, MAX_RESULT_LENGTH);
	}
}
