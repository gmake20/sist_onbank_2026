package org.doit.onbank.account.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.Optional;

import org.doit.onbank.account.entity.Account;
import org.doit.onbank.account.entity.AccountStatus;
import org.doit.onbank.account.entity.TransactionHistory;
import org.doit.onbank.account.entity.TransactionStatus;
import org.doit.onbank.account.entity.TransactionType;
import org.doit.onbank.account.repository.AccountRepository;
import org.doit.onbank.account.repository.TransactionHistoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

// AccountService 단위 테스트 : DB 대신 가짜(Mock) Repository 사용
// 확인하는 것 : 입출금 순서, 거래내역에 들어가는 값, 실패 시 잔액·거래내역이 바뀌지 않는지
// 확인하지 못하는 것 : 실제 잠금(FOR UPDATE), REQUIRES_NEW 커밋, SQL → DB 통합 테스트에서 확인
@ExtendWith(MockitoExtension.class)
class AccountServiceTests {

	private static final Long ACCOUNT_ID = 1L;
	private static final Long OWNER_ID = 100L;
	private static final String SQNO = "20261015143012-482915";

	@Mock
	private AccountRepository accountRepository;

	@Mock
	private TransactionHistoryRepository transactionHistoryRepository;

	@Mock
	private AccountPasswordVerifier accountPasswordVerifier;

	@Mock
	private TransactionSqnoGenerator transactionSqnoGenerator;

	@InjectMocks
	private AccountService accountService;

	private Account account(String status, long balance) {
		Account account = Account.builder()
				.memberId(OWNER_ID)
				.accountNumber("TEST-0001")
				.accountStatus(status)
				.balance(balance)
				.todayTransferAmount(0L)
				.build();
		ReflectionTestUtils.setField(account, "accountId", ACCOUNT_ID);	// DB가 넣어 주는 ID를 직접 설정
		return account;
	}

	// save()에 넘긴 객체를 그대로 돌려주도록 설정
	private void givenSaveReturnsArgument() {
		given(transactionHistoryRepository.save(any(TransactionHistory.class)))
				.willAnswer(invocation -> invocation.getArgument(0));
		given(transactionSqnoGenerator.generate(any())).willReturn(SQNO);
	}

	// ---------- 입금 ----------

	@Test
	void depositRecordsHistory() {
		Account account = account(AccountStatus.ACTIVE, 10_000L);
		given(accountRepository.findByIdForUpdate(ACCOUNT_ID)).willReturn(Optional.of(account));
		givenSaveReturnsArgument();

		TransactionHistory history = accountService.deposit(ACCOUNT_ID, 5_000L, "ATM 입금");

		assertThat(account.getBalance()).isEqualTo(15_000L);
		assertThat(history.getAccountId()).isEqualTo(ACCOUNT_ID);
		assertThat(history.getTransactionType()).isEqualTo(TransactionType.DEPOSIT);
		assertThat(history.getTransactionAmount()).isEqualTo(5_000L);
		assertThat(history.getBalanceAfterTransaction()).isEqualTo(15_000L);
		assertThat(history.getTransactionContent()).isEqualTo("ATM 입금");
		assertThat(history.getTransactionStatus()).isEqualTo(TransactionStatus.COMPLETED);
		assertThat(history.getTransactionSqno()).isEqualTo(SQNO);
		assertThat(history.getTransactionAt()).isEqualTo(account.getLastTransactionAt());
	}

	@Test
	void depositUsesDefaultContentWhenBlank() {
		Account account = account(AccountStatus.ACTIVE, 0L);
		given(accountRepository.findByIdForUpdate(ACCOUNT_ID)).willReturn(Optional.of(account));
		givenSaveReturnsArgument();

		TransactionHistory history = accountService.deposit(ACCOUNT_ID, 5_000L, " ");

		assertThat(history.getTransactionContent()).isEqualTo("입금");
	}

	@Test
	void depositFailsWhenAccountNotFound() {
		given(accountRepository.findByIdForUpdate(ACCOUNT_ID)).willReturn(Optional.empty());

		assertThatThrownBy(() -> accountService.deposit(ACCOUNT_ID, 5_000L, null))
				.hasMessageContaining("계좌가 없습니다");
		verify(transactionHistoryRepository, never()).save(any());
	}

	@Test
	void depositFailsWhenPendingAndNoHistorySaved() {
		Account account = account(AccountStatus.PENDING, 0L);
		given(accountRepository.findByIdForUpdate(ACCOUNT_ID)).willReturn(Optional.of(account));

		assertThatThrownBy(() -> accountService.deposit(ACCOUNT_ID, 5_000L, null))
				.isInstanceOf(IllegalStateException.class);
		assertThat(account.getBalance()).isZero();
		verify(transactionHistoryRepository, never()).save(any());
	}

	// ---------- 출금 ----------

	@Test
	void withdrawRecordsHistory() {
		Account account = account(AccountStatus.ACTIVE, 10_000L);
		given(accountPasswordVerifier.verify(ACCOUNT_ID, OWNER_ID, "1234")).willReturn(true);
		given(accountRepository.findByIdForUpdate(ACCOUNT_ID)).willReturn(Optional.of(account));
		givenSaveReturnsArgument();

		TransactionHistory history = accountService.withdraw(OWNER_ID, ACCOUNT_ID, 3_000L, "1234", null);

		assertThat(account.getBalance()).isEqualTo(7_000L);
		assertThat(history.getTransactionType()).isEqualTo(TransactionType.WITHDRAWAL);
		assertThat(history.getTransactionAmount()).isEqualTo(3_000L);
		assertThat(history.getBalanceAfterTransaction()).isEqualTo(7_000L);
		assertThat(history.getTransactionContent()).isEqualTo("출금");
		assertThat(history.getTransactionStatus()).isEqualTo(TransactionStatus.COMPLETED);
	}

	// 비밀번호가 틀리면 계좌를 잠그지도, 잔액을 바꾸지도 않는다
	@Test
	void withdrawFailsWhenPasswordWrong() {
		given(accountPasswordVerifier.verify(ACCOUNT_ID, OWNER_ID, "0000")).willReturn(false);

		assertThatThrownBy(() -> accountService.withdraw(OWNER_ID, ACCOUNT_ID, 3_000L, "0000", null))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("비밀번호");
		verify(accountRepository, never()).findByIdForUpdate(anyLong());
		verify(transactionHistoryRepository, never()).save(any());
	}

	@Test
	void withdrawFailsWhenBalanceInsufficientAndNoHistorySaved() {
		Account account = account(AccountStatus.ACTIVE, 1_000L);
		given(accountPasswordVerifier.verify(ACCOUNT_ID, OWNER_ID, "1234")).willReturn(true);
		given(accountRepository.findByIdForUpdate(ACCOUNT_ID)).willReturn(Optional.of(account));

		assertThatThrownBy(() -> accountService.withdraw(OWNER_ID, ACCOUNT_ID, 3_000L, "1234", null))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("잔액");
		assertThat(account.getBalance()).isEqualTo(1_000L);
		verify(transactionHistoryRepository, never()).save(any());
	}

	@Test
	void withdrawFailsWhenFrozen() {
		Account account = account(AccountStatus.FROZEN, 10_000L);
		given(accountPasswordVerifier.verify(ACCOUNT_ID, OWNER_ID, "1234")).willReturn(true);
		given(accountRepository.findByIdForUpdate(ACCOUNT_ID)).willReturn(Optional.of(account));

		assertThatThrownBy(() -> accountService.withdraw(OWNER_ID, ACCOUNT_ID, 3_000L, "1234", null))
				.isInstanceOf(IllegalStateException.class);
		assertThat(account.getBalance()).isEqualTo(10_000L);
		verify(transactionHistoryRepository, never()).save(any());
	}
}
