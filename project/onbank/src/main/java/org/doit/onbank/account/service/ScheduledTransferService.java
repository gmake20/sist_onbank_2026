package org.doit.onbank.account.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.NoSuchElementException;

import org.doit.onbank.account.entity.Account;
import org.doit.onbank.account.entity.ScheduleType;
import org.doit.onbank.account.entity.ScheduledTransfer;
import org.doit.onbank.account.entity.ScheduledTransferStatus;
import org.doit.onbank.account.repository.AccountRepository;
import org.doit.onbank.account.repository.ScheduledTransferRepository;
import org.doit.onbank.account.service.AccountTransferService.TransferTarget;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

// 예약·정기이체 등록·조회·취소 (기획서 5.4, 5.5)
// 등록할 때 즉시이체와 같은 검증·인증(계좌 비밀번호, 10만 원 초과 타인 이체면 OTP)을 하고 저장만 한다
// 실제 이체는 실행 일시에 스케줄러가 AccountTransferService.transferPreAuthorized() 로 한다 (4단계)
@Service
@RequiredArgsConstructor
public class ScheduledTransferService {

	public static final int MAX_DAYS_AHEAD = 365;	// 예약은 1년 이내로만

	private final ScheduledTransferRepository scheduledTransferRepository;
	private final AccountRepository accountRepository;
	private final AccountTransferService accountTransferService;

	// 예약이체 등록
	// 순서 : 실행 일시 → 출금계좌 상태 → 중복 → 비밀번호·OTP → 저장
	//        (돈과 상관없는 검사를 먼저 해서, 어차피 등록할 수 없는 요청으로 비밀번호 오류 횟수나 OTP가 소모되지 않게)
	@Transactional
	public ScheduledTransfer reserve(Long memberId, Long fromAccountId, String rawPassword, String otpCode,
			String toAccountNumber, long amount, String senderMemo, String receiverMemo, LocalDateTime executeAt) {
		LocalDateTime now = LocalDateTime.now();

		// 1. 실행 일시 : 분 단위로 맞추고, 지금 이후 ~ 1년 이내만
		if (executeAt == null) {
			throw new IllegalArgumentException("예약 일시를 선택하세요.");
		}
		LocalDateTime at = executeAt.truncatedTo(ChronoUnit.MINUTES);
		if (!at.isAfter(now)) {
			throw new IllegalArgumentException("예약 일시는 지금 이후로 선택하세요.");
		}
		if (at.isAfter(now.plusDays(MAX_DAYS_AHEAD))) {
			throw new IllegalArgumentException("예약이체는 " + MAX_DAYS_AHEAD + "일 이내로만 등록할 수 있습니다.");
		}

		// 2~4. 출금계좌 상태 → 중복 → 비밀번호·OTP
		TransferTarget target = checkAndAuthorize(ScheduleType.SCHEDULED, memberId, fromAccountId, rawPassword, otpCode,
				toAccountNumber, amount, at);

		// 5. 저장 : 받는 계좌번호는 DB에 있는 형식 그대로 (하이픈 포함)
		return scheduledTransferRepository.save(ScheduledTransfer.reserve(memberId, fromAccountId,
				target.toAccount().getAccountNumber(), amount, trimToNull(senderMemo), trimToNull(receiverMemo), at, now));
	}

	// 정기이체 등록 (기획서 5.5) : 매월 transferDay일 time 에 이체. endDate 가 없으면 해지할 때까지
	// 순서와 인증은 예약이체와 같다 (회차 일시 → 출금계좌 상태 → 중복 → 비밀번호·OTP → 저장)
	@Transactional
	public ScheduledTransfer subscribe(Long memberId, Long fromAccountId, String rawPassword, String otpCode,
			String toAccountNumber, long amount, String senderMemo, String receiverMemo,
			Integer transferDay, LocalTime time, LocalDate endDate) {
		LocalDateTime now = LocalDateTime.now();

		// 1. 회차 : 매월 1~31일, 시각은 분 단위. 첫 회차는 지금 이후 가장 가까운 그날 (없는 날이면 말일)
		if (transferDay == null || time == null) {
			throw new IllegalArgumentException("정기이체 날짜와 시각을 선택하세요.");
		}
		if (transferDay < 1 || transferDay > 31) {
			throw new IllegalArgumentException("정기이체 날짜는 1일부터 31일 사이로 선택하세요.");
		}
		LocalDateTime first = ScheduledTransfer.nextOccurrence(transferDay, time.truncatedTo(ChronoUnit.MINUTES), now);
		if (endDate != null && endDate.isBefore(first.toLocalDate())) {
			throw new IllegalArgumentException("종료일은 첫 이체일(" + first.toLocalDate() + ") 이후로 선택하세요.");
		}

		// 2~4. 출금계좌 상태 → 중복 → 비밀번호·OTP
		TransferTarget target = checkAndAuthorize(ScheduleType.RECURRING, memberId, fromAccountId, rawPassword, otpCode,
				toAccountNumber, amount, first);

		// 5. 저장
		return scheduledTransferRepository.save(ScheduledTransfer.subscribe(memberId, fromAccountId,
				target.toAccount().getAccountNumber(), amount, trimToNull(senderMemo), trimToNull(receiverMemo),
				transferDay, first, endDate, now));
	}

	// 예약·정기 등록 공통 검사 (돈과 상관없는 검사를 먼저 해서, 어차피 등록할 수 없는 요청으로 비밀번호 오류 횟수나 OTP가 소모되지 않게)
	private TransferTarget checkAndAuthorize(ScheduleType type, Long memberId, Long fromAccountId, String rawPassword,
			String otpCode, String toAccountNumber, long amount, LocalDateTime executeAt) {
		// 출금계좌 : 본인 계좌이고 지금 출금할 수 있는 상태인지 (동결·해지 계좌로는 등록하지 않는다)
		Account from = accountRepository.findByAccountIdAndMemberId(fromAccountId, memberId)
				.orElseThrow(() -> new IllegalArgumentException("본인 계좌만 이용할 수 있습니다."));
		if (!from.canWithdraw()) {
			throw new IllegalStateException("출금할 수 없는 계좌입니다. (" + from.getAccountStatus().getLabel() + ")");
		}

		// 중복 : 출금계좌·입금계좌·금액·(다음) 실행 일시가 모두 같은 대기 건이 있으면 등록하지 않는다
		String toNumber = AccountNumberGenerator.normalize(toAccountNumber);
		if (scheduledTransferRepository.existsByAccountIdAndDepositAccountNumberAndTransferAmountAndNextExecuteAtAndStatus(
				fromAccountId, toNumber, amount, executeAt, ScheduledTransferStatus.WAITING)) {
			throw new IllegalArgumentException("같은 내용의 " + type.getLabel() + "가 이미 등록되어 있습니다.");
		}

		// 즉시이체와 같은 검증·인증 (금액, 받는 계좌, 1회 한도, 비밀번호, OTP). 잔액은 실행할 때 본다
		return accountTransferService.authorize(memberId, fromAccountId, rawPassword, otpCode, toNumber, amount);
	}

	// 내 예약·정기이체 목록 (최근 등록 순)
	@Transactional(readOnly = true)
	public List<ScheduledTransfer> getMine(Long memberId) {
		return scheduledTransferRepository.findByMemberIdOrderByScheduledTransferIdDesc(memberId);
	}

	// 취소(정기이체는 해지) : 본인 것만, 대기·중지 상태만 (상태 규칙은 엔티티 cancel() 이 검사)
	@Transactional
	public ScheduledTransfer cancel(Long memberId, Long scheduledTransferId) {
		ScheduledTransfer transfer = scheduledTransferRepository
				.findByScheduledTransferIdAndMemberId(scheduledTransferId, memberId)
				.orElseThrow(() -> new NoSuchElementException("예약이체를 찾을 수 없습니다."));
		transfer.cancel(LocalDateTime.now());
		return transfer;
	}

	// 1건 조회 (본인 것만) : [지금 실행] 결과를 보여 줄 때
	@Transactional(readOnly = true)
	public ScheduledTransfer getMine(Long memberId, Long scheduledTransferId) {
		return scheduledTransferRepository.findByScheduledTransferIdAndMemberId(scheduledTransferId, memberId)
				.orElseThrow(() -> new NoSuchElementException("예약이체를 찾을 수 없습니다."));
	}

	// ---------- 실행 (ScheduledTransferRunner 가 호출) ----------

	// 1건 실행 : 행을 잠그고 → 아직 실행할 건인지 다시 보고 → 이체 → 완료 표시
	// 이체와 완료 표시를 한 트랜잭션으로 커밋한다 → "이체는 됐는데 상태는 대기"인 채로 남아 다음에 또 이체되는 일이 없다
	// 이체가 실패하면 예외가 그대로 나가 전체가 롤백된다 (잔액·기록·상태 모두 그대로). 실패 기록은 recordFailure() 가 따로 한다
	// force : [지금 실행](시연용)일 때 true → 실행 일시를 기다리지 않고 대기 건이면 실행
	// 돌려주는 값 : 실제로 이체했으면 true, 이미 처리됐거나 아직 때가 아니라 건너뛰었으면 false
	@Transactional
	public boolean execute(Long scheduledTransferId, LocalDateTime now, boolean force) {
		ScheduledTransfer transfer = scheduledTransferRepository.findByIdForUpdate(scheduledTransferId).orElse(null);
		if (transfer == null) {
			return false;
		}
		boolean runnable = force ? transfer.getStatus() == ScheduledTransferStatus.WAITING : transfer.isDue(now);
		if (!runnable) {
			return false;	// 다른 서버나 [지금 실행]이 먼저 처리했거나, 그사이 취소됨
		}
		accountTransferService.transferPreAuthorized(transfer.getMemberId(), transfer.getAccountId(),
				transfer.getDepositAccountNumber(), transfer.getTransferAmount(),
				transfer.getSenderMemo(), transfer.getReceiverMemo(), transfer.getScheduleType().getTransferType());
		transfer.markSucceeded(now);
		return true;
	}

	// 실패 기록 : execute() 가 롤백된 뒤 새 트랜잭션에서 실패 사유를 남긴다
	// 다시 잠그고 아직 실행할 건일 때만 기록한다 (그사이 다른 서버가 성공시켰으면 덮어쓰지 않음)
	// 정기이체는 성공해도 대기로 남으므로 상태만 보면 안 된다 → 스케줄러 실행이면 아직 이 회차가 때가 된 상태인지(isDue) 본다
	//   (다른 서버가 이 회차를 성공시켰으면 다음 달로 넘어가 있어 isDue 가 false)
	@Transactional
	public void recordFailure(Long scheduledTransferId, String reason, LocalDateTime now, boolean force) {
		scheduledTransferRepository.findByIdForUpdate(scheduledTransferId)
				.filter(transfer -> force ? transfer.getStatus() == ScheduledTransferStatus.WAITING : transfer.isDue(now))
				.ifPresent(transfer -> transfer.markFailed(reason, now));
	}

	private static String trimToNull(String value) {
		return (value == null || value.isBlank()) ? null : value.trim();
	}
}
