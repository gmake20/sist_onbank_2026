package org.doit.onbank.account.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.NoSuchElementException;
import java.util.Objects;

import org.doit.onbank.account.dto.RecipientInfo;
import org.doit.onbank.account.dto.TransferLimitInfo;
import org.doit.onbank.account.dto.TransferReceipt;
import org.doit.onbank.account.entity.Account;
import org.doit.onbank.account.entity.AccountTransfer;
import org.doit.onbank.account.entity.TransactionHistory;
import org.doit.onbank.account.entity.TransactionStatus;
import org.doit.onbank.account.entity.TransactionType;
import org.doit.onbank.account.entity.TransferType;
import org.doit.onbank.account.repository.AccountRepository;
import org.doit.onbank.account.repository.AccountTransferRepository;
import org.doit.onbank.account.repository.MemberTransferLimit;
import org.doit.onbank.account.repository.TransactionHistoryRepository;
import org.doit.onbank.member.entity.Member;
import org.doit.onbank.member.repository.MemberRepository;
import org.doit.onbank.otp.entity.OtpStatus;
import org.doit.onbank.otp.service.OtpService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

// 계좌 이체 (당행 즉시이체)
// 잔액 변경, 거래내역 2행(보냄/받음), 이체 1행 기록을 한 트랜잭션에서 한다
// 이체 1건의 세 행은 같은 거래 번호(transaction_sqno)로 묶는다
@Service
@RequiredArgsConstructor
public class AccountTransferService {

	// members 한도 컬럼이 비어 있을 때 쓰는 기본 한도 (2026-10-15 사용자 결정)
	public static final long DEFAULT_DAILY_LIMIT = 5_000_000L;
	public static final long DEFAULT_ONCE_LIMIT = 1_000_000L;

	// 이 금액을 넘는 이체는 계좌 비밀번호에 더해 OTP 6자리를 요구한다 (2026-10-19 사용자 결정)
	// 이하는 계좌 비밀번호만, ONBANK 본인 계좌끼리 이체는 금액과 관계없이 OTP 생략
	public static final long OTP_REQUIRED_AMOUNT = 100_000L;

	private final AccountRepository accountRepository;
	private final AccountTransferRepository accountTransferRepository;
	private final TransactionHistoryRepository transactionHistoryRepository;
	private final MemberRepository memberRepository;
	private final AccountPasswordVerifier accountPasswordVerifier;
	private final TransactionSqnoGenerator transactionSqnoGenerator;
	private final OtpService otpService;

	// 이체 (기획서 11-1장 순서)
	// otpCode : 기준 금액을 넘는 타인 계좌 이체일 때만 확인한다 (그 외에는 null 이어도 됨)
	@Transactional
	public TransferReceipt transfer(Long memberId, Long fromAccountId, String rawPassword, String otpCode,
			String toAccountNumber, long amount, String senderMemo, String receiverMemo) {
		// 1. 입력 검증 (잠그기 전에 할 수 있는 것)
		if (amount <= 0) {
			throw new IllegalArgumentException("이체금액은 0보다 커야 합니다.");
		}
		Account toAccountInfo = findDepositAccount(toAccountNumber);
		if (Objects.equals(toAccountInfo.getAccountId(), fromAccountId)) {
			throw new IllegalArgumentException("출금계좌와 같은 계좌로는 이체할 수 없습니다.");
		}
		TransferLimitInfo limit = getLimit(memberId, null);
		if (amount > limit.onceLimit()) {
			throw new IllegalArgumentException("1회 이체한도("
					+ String.format("%,d", limit.onceLimit()) + "원)를 초과했습니다.");
		}
		// OTP가 필요한지는 화면이 보낸 값이 아니라 서버가 금액·받는 분으로 판단한다
		// 등록 여부는 비밀번호 확인 전에 본다 (OTP가 없어 어차피 못 하는 이체로 비밀번호 오류 횟수가 늘지 않게)
		boolean otpRequired = requiresOtp(memberId, toAccountInfo.getMemberId(), amount);
		if (otpRequired) {
			if (!isOtpReady(memberId)) {
				throw new OtpRequiredException(String.format("%,d", OTP_REQUIRED_AMOUNT)
						+ "원을 넘는 이체는 OTP 등록이 필요해요.");
			}
			if (otpCode == null || !otpCode.matches("\\d{6}")) {
				throw new IllegalArgumentException("OTP 번호 6자리를 입력하세요.");
			}
		}

		// 2. 계좌 비밀번호 검증 (REQUIRES_NEW) : 계좌를 잠그기 전에 한다
		//    순서가 바뀌면 이 트랜잭션이 잡은 잠금을 REQUIRES_NEW 트랜잭션이 기다리며 멈춘다
		if (!accountPasswordVerifier.verify(fromAccountId, memberId, rawPassword)) {
			throw new IllegalArgumentException("계좌 비밀번호가 일치하지 않습니다.");
		}

		// 3. OTP 검증 (팀1 OtpService) : 틀리면 BusinessException. 오류 횟수는 팀1이 별도 트랜잭션으로 저장
		//    비밀번호와 같은 이유로 계좌를 잠그기 전에 한다
		if (otpRequired) {
			otpService.verify(memberId, otpCode);
		}

		// 4. 두 계좌를 ID 오름차순으로 잠근다 (서로 반대 방향 이체가 동시에 와도 데드락이 나지 않게)
		Long toAccountId = toAccountInfo.getAccountId();
		Account first = findForUpdate(Math.min(fromAccountId, toAccountId));
		Account second = findForUpdate(Math.max(fromAccountId, toAccountId));
		Account from = fromAccountId.equals(first.getAccountId()) ? first : second;
		Account to = from == first ? second : first;

		if (!Objects.equals(from.getMemberId(), memberId)) {
			throw new IllegalArgumentException("본인 계좌만 이용할 수 있습니다.");
		}

		// 5. 1일 한도 : 이 계좌에서 오늘 이체한 금액 + 이번 금액 ≤ 1일 한도
		LocalDateTime now = LocalDateTime.now();
		long todayUsed = from.getTodayTransferAmountOn(now.toLocalDate());
		if (todayUsed + amount > limit.dailyLimit()) {
			throw new IllegalStateException("1일 이체한도를 초과했습니다. (오늘 남은 한도 "
					+ String.format("%,d", Math.max(0L, limit.dailyLimit() - todayUsed)) + "원)");
		}

		// 6. 잔액 변경 : 상태·잔액·비밀번호 5회 검사는 엔티티 메서드 안에서 한다
		from.transferOut(amount, now);
		to.transferIn(amount, now);

		// 7. 기록 : 이체 1행 + 거래내역 2행 (같은 거래 번호)
		String senderName = memberName(from.getMemberId());
		String recipientName = memberName(to.getMemberId());
		String senderContent = isBlank(senderMemo) ? recipientName : senderMemo.trim();		// 내 통장 표시
		String receiverContent = isBlank(receiverMemo) ? senderName : receiverMemo.trim();	// 받는 분 통장 표시
		String sqno = transactionSqnoGenerator.generate(now);

		accountTransferRepository.save(AccountTransfer.builder()
				.accountId(from.getAccountId())
				.withdrawalAccountNumber(from.getAccountNumber())
				.financialInstitutionId(null)	// NULL = 당행(ONBANK)
				.depositAccountNumber(to.getAccountNumber())
				.transferAmount(amount)
				.senderMemo(senderContent)
				.receiverMemo(receiverContent)
				.transferAt(now)
				.transferType(TransferType.IMMEDIATE)
				.transactionSqno(sqno)
				.build());
		record(from, TransactionType.TRANSFER_OUT, amount, senderContent, now, sqno);
		record(to, TransactionType.TRANSFER_IN, amount, receiverContent, now, sqno);

		// 8. 커밋 후 이벤트 발행 (팀4 알림용) : 이벤트 클래스 합의 후 추가

		return new TransferReceipt(from.getAccountId(), from.getAccountNumber(), to.getAccountNumber(), RecipientInfo.mask(recipientName),
				amount, from.getBalance(), senderContent, receiverContent, now, sqno);
	}

	// 입금계좌 확인 : 계좌번호로 받는 분 이름(가림)을 돌려준다 (이체 확인 단계)
	@Transactional(readOnly = true)
	public RecipientInfo findRecipient(String toAccountNumber) {
		Account account = findDepositAccount(toAccountNumber);
		return new RecipientInfo(account.getAccountNumber(), RecipientInfo.mask(memberName(account.getMemberId())));
	}

	// 이체한도 조회 : accountId 가 있으면 그 계좌에서 오늘 이체한 금액도 함께 (본인 계좌만)
	@Transactional(readOnly = true)
	public TransferLimitInfo getLimit(Long memberId, Long accountId) {
		MemberTransferLimit saved = accountRepository.findTransferLimit(memberId).orElse(null);
		long daily = (saved == null || saved.getDailyTransferLimit() == null)
				? DEFAULT_DAILY_LIMIT : saved.getDailyTransferLimit();
		long once = (saved == null || saved.getOnceTransferLimit() == null)
				? DEFAULT_ONCE_LIMIT : saved.getOnceTransferLimit();

		long todayUsed = 0L;
		if (accountId != null) {
			Account account = accountRepository.findById(accountId)
					.filter(a -> Objects.equals(a.getMemberId(), memberId))
					.orElseThrow(() -> new NoSuchElementException("계좌가 없습니다."));
			todayUsed = account.getTodayTransferAmountOn(LocalDate.now());
		}
		return new TransferLimitInfo(daily, once, todayUsed);
	}

	// 이체에 OTP가 필요한지 : 기준 금액 초과 + 다른 회원 계좌로 보낼 때
	public boolean requiresOtp(Long memberId, Long toAccountMemberId, long amount) {
		return amount > OTP_REQUIRED_AMOUNT && !Objects.equals(memberId, toAccountMemberId);
	}

	// 바로 쓸 수 있는 OTP가 있는지 (사용 등록까지 마친 ACTIVE). 정지된 OTP는 검증할 때 팀1 문구로 안내된다
	@Transactional(readOnly = true)
	public boolean isOtpReady(Long memberId) {
		return otpService.findCurrent(memberId)
				.map(device -> device.status() == OtpStatus.ACTIVE || device.status() == OtpStatus.SUSPENDED)
				.orElse(false);
	}

	// 입금받을 수 있는 계좌인지 확인 (없는 계좌, 개설대기·해지 계좌는 이체 불가)
	private Account findDepositAccount(String toAccountNumber) {
		if (isBlank(toAccountNumber)) {
			throw new IllegalArgumentException("입금계좌번호를 입력하세요.");
		}
		Account account = accountRepository.findByAccountNumber(toAccountNumber.trim())
				.orElseThrow(() -> new IllegalArgumentException("입금계좌를 찾을 수 없습니다. 계좌번호를 확인하세요."));
		if (!account.canDeposit()) {
			throw new IllegalArgumentException("입금할 수 없는 계좌입니다.");
		}
		return account;
	}

	private Account findForUpdate(Long accountId) {
		return accountRepository.findByIdForUpdate(accountId)
				.orElseThrow(() -> new NoSuchElementException("계좌가 없습니다."));
	}

	private String memberName(Long memberId) {
		return memberRepository.findById(memberId).map(Member::getName).orElse("");
	}

	// 거래내역 1행 기록 (잔액이 바뀐 뒤 호출 → 거래 후 잔액을 그대로 저장)
	private void record(Account account, String transactionType, long amount, String content,
			LocalDateTime now, String sqno) {
		transactionHistoryRepository.save(TransactionHistory.builder()
				.accountId(account.getAccountId())
				.transactionType(transactionType)
				.transactionAmount(amount)
				.balanceAfterTransaction(account.getBalance())
				.transactionContent(content)
				.transactionAt(now)
				.transactionStatus(TransactionStatus.COMPLETED)
				.transactionSqno(sqno)
				.build());
	}

	private boolean isBlank(String value) {
		return value == null || value.isBlank();
	}
}
