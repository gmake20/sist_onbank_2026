package org.doit.onbank.account.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import java.util.Optional;

import org.doit.onbank.account.entity.Account;
import org.doit.onbank.account.entity.AccountStatus;
import org.doit.onbank.account.repository.AccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

// AccountPasswordVerifier 단위 테스트 : DB 대신 가짜(Mock) Repository 사용
// 트랜잭션(REQUIRES_NEW)은 Spring 없이 동작하지 않으므로 여기서는 검증 규칙만 확인한다
@ExtendWith(MockitoExtension.class)
class AccountPasswordVerifierTests {

	private static final Long ACCOUNT_ID = 1L;
	private static final Long OWNER_ID = 100L;

	@Mock
	private AccountRepository accountRepository;

	private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

	private AccountPasswordVerifier verifier;
	private Account account;

	@BeforeEach
	void setUp() {
		verifier = new AccountPasswordVerifier(accountRepository, passwordEncoder);
		account = Account.builder()
				.memberId(OWNER_ID)
				.accountStatus(AccountStatus.ACTIVE)
				.balance(0L)
				.passwordHash(passwordEncoder.encode("1234"))
				.build();
		given(accountRepository.findByIdForUpdate(ACCOUNT_ID)).willReturn(Optional.of(account));
	}

	@Test
	void correctPasswordReturnsTrueAndResetsFailCount() {
		account.increasePasswordFailCount();
		account.increasePasswordFailCount();

		boolean result = verifier.verify(ACCOUNT_ID, OWNER_ID, "1234");

		assertThat(result).isTrue();
		assertThat(account.getPasswordFailCount()).isZero();
	}

	// 틀리면 예외가 아니라 false → REQUIRES_NEW 트랜잭션이 롤백되지 않고 증가한 횟수가 커밋된다
	@Test
	void wrongPasswordReturnsFalseAndIncreasesFailCount() {
		boolean result = verifier.verify(ACCOUNT_ID, OWNER_ID, "0000");

		assertThat(result).isFalse();
		assertThat(account.getPasswordFailCount()).isEqualTo(1);
	}

	@Test
	void nullPasswordCountsAsWrong() {
		assertThat(verifier.verify(ACCOUNT_ID, OWNER_ID, null)).isFalse();
		assertThat(account.getPasswordFailCount()).isEqualTo(1);
	}

	// 남의 계좌면 거절하고 오류 횟수는 올리지 않는다 (남의 계좌를 잠그지 못하게)
	@Test
	void otherMembersAccountRejectedWithoutCounting() {
		assertThatThrownBy(() -> verifier.verify(ACCOUNT_ID, 999L, "0000"))
				.isInstanceOf(IllegalArgumentException.class);
		assertThat(account.getPasswordFailCount()).isZero();
	}

	// 이미 5회 틀린 계좌는 맞는 비밀번호를 넣어도 거절
	@Test
	void rejectedWhenFailLimitReachedEvenIfCorrect() {
		for (int i = 0; i < Account.MAX_PASSWORD_FAIL_COUNT; i++) {
			account.increasePasswordFailCount();
		}

		assertThatThrownBy(() -> verifier.verify(ACCOUNT_ID, OWNER_ID, "1234"))
				.isInstanceOf(IllegalStateException.class);
		assertThat(account.getPasswordFailCount()).isEqualTo(Account.MAX_PASSWORD_FAIL_COUNT);
	}
}
