package org.doit.onbank.account.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.doit.onbank.account.entity.AccountType;
import org.doit.onbank.account.repository.AccountRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccountNumberGeneratorTests {

	@Mock
	private AccountRepository accountRepository;

	@InjectMocks
	private AccountNumberGenerator accountNumberGenerator;

	@Test
	void generatesNumberWithPrefixByType() {
		given(accountRepository.existsByAccountNumber(anyString())).willReturn(false);

		assertThat(accountNumberGenerator.generate(AccountType.CHECKING)).matches("1002-\\d{3}-\\d{6}");
		assertThat(accountNumberGenerator.generate(AccountType.DEPOSIT)).matches("2002-\\d{3}-\\d{6}");
		assertThat(accountNumberGenerator.generate(AccountType.SAVINGS)).matches("3002-\\d{3}-\\d{6}");
		assertThat(accountNumberGenerator.generate(AccountType.LOAN)).matches("4002-\\d{3}-\\d{6}");
	}

	// 이미 있는 번호면 다시 만든다
	@Test
	void retriesWhenNumberExists() {
		given(accountRepository.existsByAccountNumber(anyString())).willReturn(true, true, false);

		String number = accountNumberGenerator.generate(AccountType.CHECKING);

		assertThat(number).matches("1002-\\d{3}-\\d{6}");
		verify(accountRepository, times(3)).existsByAccountNumber(anyString());
	}

	@Test
	void failsAfterMaxTry() {
		given(accountRepository.existsByAccountNumber(anyString())).willReturn(true);

		assertThatThrownBy(() -> accountNumberGenerator.generate(AccountType.CHECKING))
				.isInstanceOf(IllegalStateException.class);
		verify(accountRepository, times(5)).existsByAccountNumber(anyString());
	}

	@Test
	void rejectsMissingType() {
		assertThatThrownBy(() -> accountNumberGenerator.generate(null))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void normalizeAddsHyphensToThirteenDigits() {
		assertThat(AccountNumberGenerator.normalize(" 1002482915307 ")).isEqualTo("1002-482-915307");
		assertThat(AccountNumberGenerator.normalize("1002-482-915307")).isEqualTo("1002-482-915307");
		assertThat(AccountNumberGenerator.normalize("TEST-0001")).isEqualTo("TEST-0001");	// 테스트 계좌는 그대로
		assertThat(AccountNumberGenerator.normalize("10024829")).isEqualTo("10024829");		// 자릿수가 다르면 그대로
	}
}
