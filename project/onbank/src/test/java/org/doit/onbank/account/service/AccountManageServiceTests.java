package org.doit.onbank.account.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import java.util.NoSuchElementException;
import java.util.Optional;

import org.doit.onbank.account.entity.Account;
import org.doit.onbank.account.entity.AccountStatus;
import org.doit.onbank.account.repository.AccountRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

// AccountManageService 단위 테스트 : 계좌를 잠금 조회한 뒤 상태를 바꾸는지
@ExtendWith(MockitoExtension.class)
class AccountManageServiceTests {

	private static final Long ACCOUNT_ID = 1L;

	@Mock
	private AccountRepository accountRepository;

	@InjectMocks
	private AccountManageService accountManageService;

	private Account givenAccount(AccountStatus status) {
		Account account = Account.builder()
				.memberId(100L)
				.accountNumber("TEST-0001")
				.accountStatus(status)
				.balance(0L)
				.todayTransferAmount(0L)
				.build();
		given(accountRepository.findByIdForUpdate(ACCOUNT_ID)).willReturn(Optional.of(account));
		return account;
	}

	@Test
	void activatePending() {
		givenAccount(AccountStatus.PENDING);

		assertThat(accountManageService.activatePending(ACCOUNT_ID).getAccountStatus())
				.isEqualTo(AccountStatus.ACTIVE);
	}

	@Test
	void rejectPending() {
		givenAccount(AccountStatus.PENDING);

		assertThat(accountManageService.rejectPending(ACCOUNT_ID).getAccountStatus())
				.isEqualTo(AccountStatus.CLOSED);
	}

	@Test
	void freezeAndUnfreeze() {
		givenAccount(AccountStatus.ACTIVE);

		assertThat(accountManageService.freeze(ACCOUNT_ID).getAccountStatus()).isEqualTo(AccountStatus.FROZEN);
		assertThat(accountManageService.unfreeze(ACCOUNT_ID).getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
	}

	@Test
	void close() {
		givenAccount(AccountStatus.ACTIVE);

		assertThat(accountManageService.close(ACCOUNT_ID).getAccountStatus()).isEqualTo(AccountStatus.CLOSED);
	}

	@Test
	void unlockPassword() {
		Account account = givenAccount(AccountStatus.ACTIVE);
		for (int i = 0; i < Account.MAX_PASSWORD_FAIL_COUNT; i++) {
			account.increasePasswordFailCount();
		}

		accountManageService.unlockPassword(ACCOUNT_ID);

		assertThat(account.getPasswordFailCount()).isZero();
	}

	@Test
	void failsWhenStatusNotAllowed() {
		givenAccount(AccountStatus.ACTIVE);

		assertThatThrownBy(() -> accountManageService.activatePending(ACCOUNT_ID))
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void failsWhenAccountNotFound() {
		given(accountRepository.findByIdForUpdate(ACCOUNT_ID)).willReturn(Optional.empty());

		assertThatThrownBy(() -> accountManageService.freeze(ACCOUNT_ID))
				.isInstanceOf(NoSuchElementException.class);
	}
}
