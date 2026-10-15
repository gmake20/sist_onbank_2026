package org.doit.onbank.account.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;

import org.doit.onbank.account.repository.TransactionHistoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TransactionSqnoGeneratorTests {

	private final LocalDateTime now = LocalDateTime.of(2026, 10, 15, 14, 30, 12);

	@Mock
	private TransactionHistoryRepository transactionHistoryRepository;

	@InjectMocks
	private TransactionSqnoGenerator generator;

	@Test
	void formatIsDateTimeAndSixDigits() {
		given(transactionHistoryRepository.existsByTransactionSqno(anyString())).willReturn(false);

		String sqno = generator.generate(now);

		assertThat(sqno).matches("20261015143012-\\d{6}");
	}

	// 이미 있는 번호가 나오면 다시 만든다
	@Test
	void retriesWhenDuplicated() {
		given(transactionHistoryRepository.existsByTransactionSqno(anyString())).willReturn(true, true, false);

		String sqno = generator.generate(now);

		assertThat(sqno).startsWith("20261015143012-");
		verify(transactionHistoryRepository, times(3)).existsByTransactionSqno(anyString());
	}

	@Test
	void failsAfterMaxTries() {
		given(transactionHistoryRepository.existsByTransactionSqno(anyString())).willReturn(true);

		assertThatThrownBy(() -> generator.generate(now)).isInstanceOf(IllegalStateException.class);
	}
}
