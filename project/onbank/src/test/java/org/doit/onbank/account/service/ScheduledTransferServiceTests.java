package org.doit.onbank.account.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import org.doit.onbank.account.dto.TransferLimitInfo;
import org.doit.onbank.account.entity.Account;
import org.doit.onbank.account.entity.AccountStatus;
import org.doit.onbank.account.entity.ScheduleType;
import org.doit.onbank.account.entity.ScheduledTransfer;
import org.doit.onbank.account.entity.ScheduledTransferStatus;
import org.doit.onbank.account.repository.AccountRepository;
import org.doit.onbank.account.repository.ScheduledTransferRepository;
import org.doit.onbank.account.service.AccountTransferService.TransferTarget;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

// ScheduledTransferService 단위 테스트 : DB 대신 가짜(Mock) 사용
// 확인하는 것 : 등록 검사 순서(일시 → 계좌 상태 → 중복 → 인증), 저장되는 값, 취소 권한
@ExtendWith(MockitoExtension.class)
class ScheduledTransferServiceTests {

	private static final Long MY_ID = 100L;
	private static final String TO = "1002-222-222222";

	@Mock
	private ScheduledTransferRepository scheduledTransferRepository;

	@Mock
	private AccountRepository accountRepository;

	@Mock
	private AccountTransferService accountTransferService;

	@InjectMocks
	private ScheduledTransferService service;

	private Account account(Long id, Long memberId, String number, AccountStatus status) {
		Account account = Account.builder().memberId(memberId).accountNumber(number).accountStatus(status).balance(0L).build();
		ReflectionTestUtils.setField(account, "accountId", id);
		return account;
	}

	private LocalDateTime tomorrow() {
		return LocalDateTime.now().plusDays(1).withHour(9).withMinute(30).truncatedTo(ChronoUnit.MINUTES);
	}

	@Test
	void reserveAuthorizesAndSavesWaiting() {
		LocalDateTime at = tomorrow().withSecond(42);	// 초는 버리고 분 단위로 저장
		given(accountRepository.findByAccountIdAndMemberId(1L, MY_ID))
				.willReturn(Optional.of(account(1L, MY_ID, "1002-111-111111", AccountStatus.ACTIVE)));
		given(accountTransferService.authorize(MY_ID, 1L, "1234", null, TO, 50_000L))
				.willReturn(new TransferTarget(account(2L, 200L, TO, AccountStatus.ACTIVE), new TransferLimitInfo(0, 0, 0)));
		given(scheduledTransferRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));

		// 하이픈 없이 입력해도 저장은 DB 형식(하이픈 포함)으로
		ScheduledTransfer saved = service.reserve(MY_ID, 1L, "1234", null, "1002222222222", 50_000L, " 월세 ", "", at);

		assertThat(saved.getStatus()).isEqualTo(ScheduledTransferStatus.WAITING);
		assertThat(saved.getDepositAccountNumber()).isEqualTo(TO);
		assertThat(saved.getNextExecuteAt()).isEqualTo(at.truncatedTo(ChronoUnit.MINUTES));
		assertThat(saved.getSenderMemo()).isEqualTo("월세");
		assertThat(saved.getReceiverMemo()).isNull();
	}

	// 지난 일시 : 비밀번호를 확인하기 전에 거른다 (오류 횟수가 늘지 않게)
	@Test
	void reserveRejectsPastTimeBeforeAuth() {
		assertThatThrownBy(() -> service.reserve(MY_ID, 1L, "1234", null, TO, 50_000L, null, null,
				LocalDateTime.now().minusMinutes(1)))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("지금 이후");
		verify(accountTransferService, never()).authorize(anyLong(), anyLong(), any(), any(), any(), anyLong());
	}

	@Test
	void reserveRejectsTooFarAhead() {
		assertThatThrownBy(() -> service.reserve(MY_ID, 1L, "1234", null, TO, 50_000L, null, null,
				LocalDateTime.now().plusDays(ScheduledTransferService.MAX_DAYS_AHEAD + 1)))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("일 이내");
	}

	// 동결 계좌 : 등록 단계에서 막는다
	@Test
	void reserveRejectsFrozenAccount() {
		given(accountRepository.findByAccountIdAndMemberId(1L, MY_ID))
				.willReturn(Optional.of(account(1L, MY_ID, "1002-111-111111", AccountStatus.FROZEN)));

		assertThatThrownBy(() -> service.reserve(MY_ID, 1L, "1234", null, TO, 50_000L, null, null, tomorrow()))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("거래정지");
		verify(accountTransferService, never()).authorize(anyLong(), anyLong(), any(), any(), any(), anyLong());
	}

	@Test
	void reserveRejectsDuplicate() {
		LocalDateTime at = tomorrow();
		given(accountRepository.findByAccountIdAndMemberId(1L, MY_ID))
				.willReturn(Optional.of(account(1L, MY_ID, "1002-111-111111", AccountStatus.ACTIVE)));
		given(scheduledTransferRepository.existsByAccountIdAndDepositAccountNumberAndTransferAmountAndNextExecuteAtAndStatus(
				1L, TO, 50_000L, at, ScheduledTransferStatus.WAITING)).willReturn(true);

		assertThatThrownBy(() -> service.reserve(MY_ID, 1L, "1234", null, TO, 50_000L, null, null, at))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("이미 등록");
		verify(scheduledTransferRepository, never()).save(any());
	}

	// 남의 예약 : 찾을 수 없음으로 처리 (있는지 없는지도 알려 주지 않는다)
	@Test
	void cancelOthersIsNotFound() {
		given(scheduledTransferRepository.findByScheduledTransferIdAndMemberId(7L, MY_ID)).willReturn(Optional.empty());

		assertThatThrownBy(() -> service.cancel(MY_ID, 7L))
				.hasMessageContaining("찾을 수 없습니다");
	}

	// ---------- 실행 ----------

	private ScheduledTransfer saved(LocalDateTime executeAt) {
		ScheduledTransfer transfer = ScheduledTransfer.reserve(MY_ID, 1L, TO, 50_000L, "월세", null, executeAt,
				executeAt.minusDays(1));
		ReflectionTestUtils.setField(transfer, "scheduledTransferId", 7L);
		return transfer;
	}

	// 때가 된 대기 건 : 인증 없이 이체하고 완료로 바꾼다 (기록 유형은 SCHEDULED)
	@Test
	void executeTransfersAndMarksCompleted() {
		LocalDateTime now = LocalDateTime.of(2026, 10, 21, 9, 30);
		ScheduledTransfer transfer = saved(now);
		given(scheduledTransferRepository.findByIdForUpdate(7L)).willReturn(Optional.of(transfer));

		boolean done = service.execute(7L, now, false);

		assertThat(done).isTrue();
		verify(accountTransferService).transferPreAuthorized(MY_ID, 1L, TO, 50_000L, "월세", null, "SCHEDULED");
		assertThat(transfer.getStatus()).isEqualTo(ScheduledTransferStatus.COMPLETED);
		assertThat(transfer.getLastExecutedAt()).isEqualTo(now);
	}

	// 아직 때가 아니면 건너뛴다 (이체하지 않음)
	@Test
	void executeSkipsNotYetDue() {
		LocalDateTime now = LocalDateTime.of(2026, 10, 21, 9, 29);
		ScheduledTransfer transfer = saved(now.plusMinutes(1));
		given(scheduledTransferRepository.findByIdForUpdate(7L)).willReturn(Optional.of(transfer));

		assertThat(service.execute(7L, now, false)).isFalse();
		verify(accountTransferService, never()).transferPreAuthorized(anyLong(), anyLong(), any(), anyLong(), any(), any(), any());
		assertThat(transfer.getStatus()).isEqualTo(ScheduledTransferStatus.WAITING);
	}

	// 다른 서버가 먼저 처리한 건 : 잠근 뒤 다시 보니 이미 완료 → 두 번 이체하지 않는다
	@Test
	void executeSkipsAlreadyCompleted() {
		LocalDateTime now = LocalDateTime.of(2026, 10, 21, 9, 30);
		ScheduledTransfer transfer = saved(now);
		transfer.markSucceeded(now);
		given(scheduledTransferRepository.findByIdForUpdate(7L)).willReturn(Optional.of(transfer));

		assertThat(service.execute(7L, now, true)).isFalse();	// [지금 실행]이어도
		verify(accountTransferService, never()).transferPreAuthorized(anyLong(), anyLong(), any(), anyLong(), any(), any(), any());
	}

	// [지금 실행] : 때가 안 됐어도 대기 건이면 실행
	@Test
	void executeForceRunsBeforeTime() {
		LocalDateTime now = LocalDateTime.of(2026, 10, 21, 9, 0);
		ScheduledTransfer transfer = saved(now.plusDays(3));
		given(scheduledTransferRepository.findByIdForUpdate(7L)).willReturn(Optional.of(transfer));

		assertThat(service.execute(7L, now, true)).isTrue();
		assertThat(transfer.getStatus()).isEqualTo(ScheduledTransferStatus.COMPLETED);
	}

	// 이체가 실패하면 예외를 그대로 던진다 (상태는 바꾸지 않음 → 트랜잭션 롤백, 실패 기록은 Runner 가 따로)
	@Test
	void executePropagatesTransferFailure() {
		LocalDateTime now = LocalDateTime.of(2026, 10, 21, 9, 30);
		ScheduledTransfer transfer = saved(now);
		given(scheduledTransferRepository.findByIdForUpdate(7L)).willReturn(Optional.of(transfer));
		given(accountTransferService.transferPreAuthorized(anyLong(), anyLong(), any(), anyLong(), any(), any(), any()))
				.willThrow(new IllegalStateException("잔액이 부족합니다."));

		assertThatThrownBy(() -> service.execute(7L, now, false)).hasMessage("잔액이 부족합니다.");
		assertThat(transfer.getStatus()).isEqualTo(ScheduledTransferStatus.WAITING);
	}

	@Test
	void recordFailureOnlyWhenStillWaiting() {
		LocalDateTime now = LocalDateTime.of(2026, 10, 21, 9, 30);
		ScheduledTransfer waiting = saved(now);
		given(scheduledTransferRepository.findByIdForUpdate(7L)).willReturn(Optional.of(waiting));

		service.recordFailure(7L, "잔액이 부족합니다.", now, false);

		assertThat(waiting.getStatus()).isEqualTo(ScheduledTransferStatus.FAILED);
		assertThat(waiting.getLastResult()).isEqualTo("잔액이 부족합니다.");
	}

	// ---------- 정기이체 ----------

	private static final LocalTime NINE = LocalTime.of(9, 0);

	@Test
	void subscribeAuthorizesAndSavesFirstRound() {
		LocalDate endDate = LocalDate.now().plusYears(1);
		given(accountRepository.findByAccountIdAndMemberId(1L, MY_ID))
				.willReturn(Optional.of(account(1L, MY_ID, "1002-111-111111", AccountStatus.ACTIVE)));
		given(accountTransferService.authorize(MY_ID, 1L, "1234", null, TO, 50_000L))
				.willReturn(new TransferTarget(account(2L, 200L, TO, AccountStatus.ACTIVE), new TransferLimitInfo(0, 0, 0)));
		given(scheduledTransferRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));

		ScheduledTransfer saved = service.subscribe(MY_ID, 1L, "1234", null, "1002222222222", 50_000L, "적금", null,
				25, NINE, endDate);

		assertThat(saved.getScheduleType()).isEqualTo(ScheduleType.RECURRING);
		assertThat(saved.getStatus()).isEqualTo(ScheduledTransferStatus.WAITING);
		assertThat(saved.getTransferDay()).isEqualTo(25);
		assertThat(saved.getEndDate()).isEqualTo(endDate);
		assertThat(saved.getDepositAccountNumber()).isEqualTo(TO);
		// 첫 회차 : 지금 이후 가장 가까운 25일 09:00
		assertThat(saved.getNextExecuteAt()).isEqualTo(ScheduledTransfer.nextOccurrence(25, NINE, LocalDateTime.now()));
	}

	// 종료일이 첫 회차보다 빠르면 인증 전에 거른다
	@Test
	void subscribeRejectsEndDateBeforeFirstRound() {
		assertThatThrownBy(() -> service.subscribe(MY_ID, 1L, "1234", null, TO, 50_000L, null, null,
				25, NINE, LocalDate.now().minusDays(1)))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("종료일은 첫 이체일");
		verify(accountTransferService, never()).authorize(anyLong(), anyLong(), any(), any(), any(), anyLong());
	}

	@Test
	void subscribeRequiresDayAndTime() {
		assertThatThrownBy(() -> service.subscribe(MY_ID, 1L, "1234", null, TO, 50_000L, null, null, 25, null, null))
				.hasMessageContaining("날짜와 시각");
		assertThatThrownBy(() -> service.subscribe(MY_ID, 1L, "1234", null, TO, 50_000L, null, null, 32, NINE, null))
				.hasMessageContaining("1일부터 31일");
	}

	@Test
	void subscribeRejectsDuplicate() {
		given(accountRepository.findByAccountIdAndMemberId(1L, MY_ID))
				.willReturn(Optional.of(account(1L, MY_ID, "1002-111-111111", AccountStatus.ACTIVE)));
		given(scheduledTransferRepository.existsByAccountIdAndDepositAccountNumberAndTransferAmountAndNextExecuteAtAndStatus(
				any(), any(), any(), any(), any())).willReturn(true);

		assertThatThrownBy(() -> service.subscribe(MY_ID, 1L, "1234", null, TO, 50_000L, null, null, 25, NINE, null))
				.hasMessage("같은 내용의 정기이체가 이미 등록되어 있습니다.");
		verify(scheduledTransferRepository, never()).save(any());
	}

	private ScheduledTransfer savedRecurring(LocalDateTime round) {
		ScheduledTransfer transfer = ScheduledTransfer.subscribe(MY_ID, 1L, TO, 50_000L, null, null,
				round.getDayOfMonth(), round, null, round.minusDays(1));
		ReflectionTestUtils.setField(transfer, "scheduledTransferId", 7L);
		return transfer;
	}

	// 정기 회차 실행 : 기록 유형은 RECURRING, 실행 뒤에도 대기(다음 달)
	@Test
	void executeRecurringMovesToNextRound() {
		LocalDateTime now = LocalDateTime.of(2026, 10, 25, 9, 0);
		ScheduledTransfer transfer = savedRecurring(now);
		given(scheduledTransferRepository.findByIdForUpdate(7L)).willReturn(Optional.of(transfer));

		assertThat(service.execute(7L, now, false)).isTrue();
		verify(accountTransferService).transferPreAuthorized(MY_ID, 1L, TO, 50_000L, null, null, "RECURRING");
		assertThat(transfer.getStatus()).isEqualTo(ScheduledTransferStatus.WAITING);
		assertThat(transfer.getNextExecuteAt()).isEqualTo(LocalDateTime.of(2026, 11, 25, 9, 0));
	}

	// 다른 서버가 이 회차를 이미 성공시켜 다음 달로 넘어갔으면, 실패 기록으로 한 달을 더 건너뛰지 않는다
	@Test
	void recordFailureSkipsRecurringAlreadyMovedOn() {
		LocalDateTime now = LocalDateTime.of(2026, 10, 25, 9, 0);
		ScheduledTransfer transfer = savedRecurring(now);
		transfer.markSucceeded(now);	// 다른 서버가 먼저 처리 → 11-25
		given(scheduledTransferRepository.findByIdForUpdate(7L)).willReturn(Optional.of(transfer));

		service.recordFailure(7L, "잔액이 부족합니다.", now, false);

		assertThat(transfer.getNextExecuteAt()).isEqualTo(LocalDateTime.of(2026, 11, 25, 9, 0));
		assertThat(transfer.isLastSucceeded()).isTrue();
	}

	@Test
	void cancelChangesStatus() {
		ScheduledTransfer transfer = ScheduledTransfer.reserve(MY_ID, 1L, TO, 50_000L, null, null, tomorrow(), LocalDateTime.now());
		given(scheduledTransferRepository.findByScheduledTransferIdAndMemberId(7L, MY_ID)).willReturn(Optional.of(transfer));

		service.cancel(MY_ID, 7L);

		assertThat(transfer.getStatus()).isEqualTo(ScheduledTransferStatus.CANCELLED);
	}
}
