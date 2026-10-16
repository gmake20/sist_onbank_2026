package org.doit.onbank.account.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

// Account 개설·상태 변경 단위 테스트 : Spring, DB 없이 자바 객체만으로 실행
class AccountStatusChangeTests {

	private Account account(String status, long balance) {
		return Account.builder()
				.memberId(1L)
				.accountNumber("TEST-0001")
				.accountStatus(status)
				.balance(balance)
				.todayTransferAmount(0L)
				.build();
	}

	// ---------- 개설 ----------

	@Test
	void openSetsInitialValues() {
		LocalDate today = LocalDate.of(2026, 10, 16);

		Account account = Account.open(1L, "1002-123-456789", AccountType.CHECKING, "hash", today);

		assertThat(account.getMemberId()).isEqualTo(1L);
		assertThat(account.getAccountNumber()).isEqualTo("1002-123-456789");
		assertThat(account.getAccountType()).isEqualTo(AccountType.CHECKING);
		assertThat(account.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
		assertThat(account.getPasswordHash()).isEqualTo("hash");
		assertThat(account.getBalance()).isZero();
		assertThat(account.getTodayTransferAmount()).isZero();
		assertThat(account.getPasswordFailCount()).isZero();
		assertThat(account.getSignupDate()).isEqualTo(today);
		assertThat(account.getLastTransactionAt()).isNull();
	}

	// ---------- 승인·반려 ----------

	@Test
	void activateChangesPendingToActive() {
		Account account = account(AccountStatus.PENDING, 0L);

		account.activate();

		assertThat(account.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
	}

	@Test
	void rejectChangesPendingToClosed() {
		Account account = account(AccountStatus.PENDING, 0L);

		account.reject();

		assertThat(account.getAccountStatus()).isEqualTo(AccountStatus.CLOSED);
	}

	@Test
	void activateAndRejectOnlyWhenPending() {
		for (String status : new String[] { AccountStatus.ACTIVE, AccountStatus.FROZEN, AccountStatus.CLOSED }) {
			Account account = account(status, 0L);

			assertThatThrownBy(account::activate).isInstanceOf(IllegalStateException.class);
			assertThatThrownBy(account::reject).isInstanceOf(IllegalStateException.class);
			assertThat(account.getAccountStatus()).isEqualTo(status);
		}
	}

	// ---------- 동결·해제 ----------

	@Test
	void freezeAndUnfreeze() {
		Account account = account(AccountStatus.ACTIVE, 10_000L);

		account.freeze();
		assertThat(account.getAccountStatus()).isEqualTo(AccountStatus.FROZEN);

		account.unfreeze();
		assertThat(account.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
	}

	@Test
	void freezeOnlyWhenActive() {
		Account account = account(AccountStatus.PENDING, 0L);

		assertThatThrownBy(account::freeze).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void unfreezeOnlyWhenFrozen() {
		Account account = account(AccountStatus.ACTIVE, 0L);

		assertThatThrownBy(account::unfreeze).isInstanceOf(IllegalStateException.class);
	}

	// ---------- 해지 ----------

	@Test
	void closeWhenBalanceZero() {
		for (String status : new String[] { AccountStatus.ACTIVE, AccountStatus.FROZEN }) {
			Account account = account(status, 0L);

			account.close();

			assertThat(account.getAccountStatus()).isEqualTo(AccountStatus.CLOSED);
		}
	}

	@Test
	void closeRejectedWhenBalanceRemains() {
		Account account = account(AccountStatus.ACTIVE, 1L);

		assertThatThrownBy(account::close)
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("잔액");
		assertThat(account.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
	}

	@Test
	void closeRejectedWhenPendingOrClosed() {
		for (String status : new String[] { AccountStatus.PENDING, AccountStatus.CLOSED }) {
			Account account = account(status, 0L);

			assertThatThrownBy(account::close).isInstanceOf(IllegalStateException.class);
		}
	}
}
