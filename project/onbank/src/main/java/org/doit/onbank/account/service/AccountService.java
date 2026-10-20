package org.doit.onbank.account.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;

import org.doit.onbank.account.entity.Account;
import org.doit.onbank.account.entity.TransactionHistory;
import org.doit.onbank.account.entity.TransactionStatus;
import org.doit.onbank.account.entity.TransactionType;
import org.doit.onbank.account.entity.AccountStatus;
import org.doit.onbank.account.entity.AccountType;
import org.doit.onbank.account.repository.AccountRepository;
import org.doit.onbank.account.repository.TransactionHistoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

// 계좌 입출금
// 잔액은 이 클래스의 메서드에서만 바꾼다. 잔액 변경과 거래내역 기록은 항상 같은 트랜잭션에서 한다
@Service
@RequiredArgsConstructor
public class AccountService {

	private static final String DEFAULT_DEPOSIT_CONTENT = "입금";
	private static final String DEFAULT_WITHDRAWAL_CONTENT = "출금";

	private final AccountRepository accountRepository;
	private final TransactionHistoryRepository transactionHistoryRepository;
	private final AccountPasswordVerifier accountPasswordVerifier;
	private final TransactionSqnoGenerator transactionSqnoGenerator;

	// 입금 : 본인 확인과 비밀번호 없이 가능 (다른 사람이 넣어 주는 돈, 대출금 입금 등)
	@Transactional
	public TransactionHistory deposit(Long accountId, long amount, String content) {
		Account account = findForUpdate(accountId);
		LocalDateTime now = LocalDateTime.now();

		account.deposit(amount, now);

		return record(account, TransactionType.DEPOSIT, amount,
				contentOrDefault(content, DEFAULT_DEPOSIT_CONTENT), now);
	}

	// 출금 : 본인 계좌 + 계좌 비밀번호 필요
	@Transactional
	public TransactionHistory withdraw(Long memberId, Long accountId, long amount, String rawPassword, String content) {
		// 비밀번호 검증을 계좌 잠금보다 먼저 한다
		// 순서가 바뀌면 이 트랜잭션이 잡은 잠금을 REQUIRES_NEW 트랜잭션이 기다리며 멈춘다
		if (!accountPasswordVerifier.verify(accountId, memberId, rawPassword)) {
			throw new IllegalArgumentException("계좌 비밀번호가 일치하지 않습니다.");
		}

		Account account = findForUpdate(accountId);
		if (!Objects.equals(account.getMemberId(), memberId)) {
			throw new IllegalArgumentException("본인 계좌만 이용할 수 있습니다.");
		}
		LocalDateTime now = LocalDateTime.now();

		account.withdraw(amount, now);

		return record(account, TransactionType.WITHDRAWAL, amount,
				contentOrDefault(content, DEFAULT_WITHDRAWAL_CONTENT), now);
	}

	// 내 계좌 목록
	@Transactional(readOnly = true)
	public List<Account> getMyAccounts(Long memberId) {
		return accountRepository.findByMemberIdOrderByAccountIdAsc(memberId);
	}

	// 내 계좌 1개 (남의 계좌면 없는 계좌와 같은 메시지 → 다른 사람 계좌가 있는지 알 수 없게)
	@Transactional(readOnly = true)
	public Account getMyAccount(Long memberId, Long accountId) {
		return accountRepository.findById(accountId)
				.filter(account -> Objects.equals(account.getMemberId(), memberId))
				.orElseThrow(() -> new NoSuchElementException("계좌가 없습니다."));
	}

	// 내 계좌의 거래내역 (최신순)
	@Transactional(readOnly = true)
	public List<TransactionHistory> getMyHistories(Long memberId, Long accountId) {
		getMyAccount(memberId, accountId);
		return transactionHistoryRepository.findByAccountIdOrderByTransactionAtDescTransactionHistoryIdDesc(accountId);
	}

	private Account findForUpdate(Long accountId) {
		return accountRepository.findByIdForUpdate(accountId)
				.orElseThrow(() -> new NoSuchElementException("계좌가 없습니다."));
	}

	// 거래내역 1행 기록 (잔액이 바뀐 뒤 호출 → 거래 후 잔액을 그대로 저장)
	private TransactionHistory record(Account account, String transactionType, long amount,
			String content, LocalDateTime now) {
		TransactionHistory history = TransactionHistory.builder()
				.accountId(account.getAccountId())
				.transactionType(transactionType)
				.transactionAmount(amount)
				.balanceAfterTransaction(account.getBalance())
				.transactionContent(content)
				.transactionAt(now)
				.transactionStatus(TransactionStatus.COMPLETED)
				.transactionSqno(transactionSqnoGenerator.generate(now))
				.build();
		return transactionHistoryRepository.save(history);
	}

	private String contentOrDefault(String content, String defaultContent) {
		return (content == null || content.isBlank()) ? defaultContent : content;
	}

	// 10/17 이지훈 추가
	@Transactional(readOnly = true)
	public List<Account> getDisbursementAccounts(Long memberId) {

		return accountRepository
				.findByMemberIdAndAccountTypeAndAccountStatus(
						memberId,
						AccountType.CHECKING,
						AccountStatus.ACTIVE
				);
	}
}
