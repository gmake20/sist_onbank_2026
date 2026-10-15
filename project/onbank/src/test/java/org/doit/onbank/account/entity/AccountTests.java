package org.doit.onbank.account.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

// Account 엔티티 단위 테스트 : Spring, DB 없이 자바 객체만으로 실행
class AccountTests {

	private final LocalDateTime now = LocalDateTime.of(2026, 10, 15, 14, 30);

	private Account account(String status, long balance) {
		return Account.builder()
				.memberId(1L)
				.accountNumber("TEST-0001")
				.accountStatus(status)
				.balance(balance)
				.todayTransferAmount(0L)
				.build();
	}

	// ---------- 입금 ----------

	@Test
	void depositIncreasesBalance() {
		Account account = account(AccountStatus.ACTIVE, 10_000L);

		account.deposit(5_000L, now);

		assertThat(account.getBalance()).isEqualTo(15_000L);
		assertThat(account.getLastTransactionAt()).isEqualTo(now);
	}

	@Test
	void depositAllowedWhenFrozen() {
		Account account = account(AccountStatus.FROZEN, 0L);

		account.deposit(5_000L, now);

		assertThat(account.getBalance()).isEqualTo(5_000L);
	}

	@Test
	void depositRejectedWhenPendingOrClosed() {
		for (String status : new String[] { AccountStatus.PENDING, AccountStatus.CLOSED }) {
			Account account = account(status, 0L);

			assertThatThrownBy(() -> account.deposit(5_000L, now))
					.isInstanceOf(IllegalStateException.class);
			assertThat(account.getBalance()).isZero();
		}
	}

	@Test
	void depositRejectedWhenAmountNotPositive() {
		Account account = account(AccountStatus.ACTIVE, 0L);

		assertThatThrownBy(() -> account.deposit(0L, now)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> account.deposit(-1L, now)).isInstanceOf(IllegalArgumentException.class);
	}

	// ---------- 출금 ----------

	@Test
	void withdrawDecreasesBalance() {
		Account account = account(AccountStatus.ACTIVE, 10_000L);

		account.withdraw(3_000L, now);

		assertThat(account.getBalance()).isEqualTo(7_000L);
		assertThat(account.getLastTransactionAt()).isEqualTo(now);
	}

	@Test
	void withdrawAllBalance() {
		Account account = account(AccountStatus.ACTIVE, 10_000L);

		account.withdraw(10_000L, now);

		assertThat(account.getBalance()).isZero();
	}

	@Test
	void withdrawRejectedWhenBalanceInsufficient() {
		Account account = account(AccountStatus.ACTIVE, 10_000L);

		assertThatThrownBy(() -> account.withdraw(10_001L, now))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("잔액");
		assertThat(account.getBalance()).isEqualTo(10_000L);
	}

	@Test
	void withdrawRejectedWhenNotActive() {
		for (String status : new String[] { AccountStatus.FROZEN, AccountStatus.PENDING, AccountStatus.CLOSED }) {
			Account account = account(status, 10_000L);

			assertThatThrownBy(() -> account.withdraw(1_000L, now))
					.isInstanceOf(IllegalStateException.class);
			assertThat(account.getBalance()).isEqualTo(10_000L);
		}
	}

	@Test
	void withdrawRejectedWhenPasswordFailLimitReached() {
		Account account = account(AccountStatus.ACTIVE, 10_000L);
		for (int i = 0; i < Account.MAX_PASSWORD_FAIL_COUNT; i++) {
			account.increasePasswordFailCount();
		}

		assertThatThrownBy(() -> account.withdraw(1_000L, now))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("비밀번호");
		assertThat(account.getBalance()).isEqualTo(10_000L);
	}

	// 현금 출금은 이체 한도와 별개 → 당일 누적 이체 금액이 늘지 않는다
	@Test
	void withdrawDoesNotAddTodayTransferAmount() {
		Account account = account(AccountStatus.ACTIVE, 10_000L);

		account.withdraw(3_000L, now);

		assertThat(account.getTodayTransferAmount()).isZero();
	}

	// ---------- 비밀번호 오류 횟수 ----------

	@Test
	void passwordFailCountStopsAtMax() {
		Account account = account(AccountStatus.ACTIVE, 0L);

		for (int i = 0; i < Account.MAX_PASSWORD_FAIL_COUNT + 2; i++) {
			account.increasePasswordFailCount();
		}

		assertThat(account.getPasswordFailCount()).isEqualTo(Account.MAX_PASSWORD_FAIL_COUNT);
		assertThat(account.isPasswordFailLimitReached()).isTrue();

		account.resetPasswordFailCount();
		assertThat(account.getPasswordFailCount()).isZero();
	}

	// ---------- 날짜가 바뀌면 당일 누적 이체 금액 초기화 ----------

	@Test
	void touchTransactionResetsTodayTransferAmountOnNewDay() {
		Account account = Account.builder()
				.accountStatus(AccountStatus.ACTIVE)
				.balance(0L)
				.todayTransferAmount(500_000L)
				.lastTransactionAt(now.minusDays(1))	// 어제 거래
				.build();

		account.touchTransaction(now);

		assertThat(account.getTodayTransferAmount()).isZero();
		assertThat(account.getLastTransactionAt()).isEqualTo(now);
	}

	@Test
	void touchTransactionKeepsTodayTransferAmountOnSameDay() {
		Account account = Account.builder()
				.accountStatus(AccountStatus.ACTIVE)
				.balance(0L)
				.todayTransferAmount(500_000L)
				.lastTransactionAt(now.minusHours(1))	// 오늘 1시간 전 거래
				.build();

		account.touchTransaction(now);

		assertThat(account.getTodayTransferAmount()).isEqualTo(500_000L);
	}
}
