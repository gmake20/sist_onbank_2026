package org.doit.onbank.account.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.LocalDate;

import org.doit.onbank.account.entity.Account;
import org.doit.onbank.account.entity.AccountStatus;
import org.doit.onbank.account.entity.AccountType;
import org.doit.onbank.account.repository.AccountRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

// AccountOpenService 단위 테스트 : DB 대신 가짜(Mock) 사용
@ExtendWith(MockitoExtension.class)
class AccountOpenServiceTests {

	private static final Long MEMBER_ID = 100L;

	@Mock
	private AccountRepository accountRepository;

	@Mock
	private AccountNumberGenerator accountNumberGenerator;

	@Mock
	private PasswordEncoder passwordEncoder;

	@InjectMocks
	private AccountOpenService accountOpenService;

	@Test
	void openCheckingSavesActiveAccountWithHashedPassword() {
		given(accountNumberGenerator.generate(AccountType.CHECKING)).willReturn("1002-123-456789");
		given(passwordEncoder.encode("1234")).willReturn("{bcrypt}hashed");
		given(accountRepository.save(any(Account.class))).willAnswer(invocation -> invocation.getArgument(0));

		Account account = accountOpenService.openChecking(MEMBER_ID, "1234");

		assertThat(account.getMemberId()).isEqualTo(MEMBER_ID);
		assertThat(account.getAccountNumber()).isEqualTo("1002-123-456789");
		assertThat(account.getAccountType()).isEqualTo(AccountType.CHECKING);
		assertThat(account.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
		assertThat(account.getPasswordHash()).isEqualTo("{bcrypt}hashed");	// 원래 비밀번호가 아니라 해시를 저장
		assertThat(account.getBalance()).isZero();
		assertThat(account.getTodayTransferAmount()).isZero();
		assertThat(account.getSignupDate()).isEqualTo(LocalDate.now());
	}

	@Test
	void openCheckingRejectsInvalidPassword() {
		for (String password : new String[] { null, "", "123", "12345", "abcd" }) {
			assertThatThrownBy(() -> accountOpenService.openChecking(MEMBER_ID, password))
					.isInstanceOf(IllegalArgumentException.class)
					.hasMessageContaining("4자리");
		}
		verify(accountNumberGenerator, never()).generate(any());
		verify(accountRepository, never()).save(any());
	}
}
