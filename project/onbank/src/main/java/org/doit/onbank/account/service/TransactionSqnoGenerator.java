package org.doit.onbank.account.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

import org.doit.onbank.account.repository.TransactionHistoryRepository;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

// 거래 번호(transaction_sqno) 생성
// 형식 : yyyyMMddHHmmss-난수6자리 (예: 20261015143012-482915)
// transaction_sqno 에 UNIQUE 가 없으므로 이미 있는 번호면 다시 만든다
@Component
@RequiredArgsConstructor
public class TransactionSqnoGenerator {

	private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
	private static final int MAX_TRY = 5;

	private final TransactionHistoryRepository transactionHistoryRepository;

	public String generate(LocalDateTime now) {
		String prefix = now.format(FORMAT) + "-";
		for (int i = 0; i < MAX_TRY; i++) {
			String sqno = prefix + String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
			if (!transactionHistoryRepository.existsByTransactionSqno(sqno)) {
				return sqno;
			}
		}
		throw new IllegalStateException("거래 번호를 만들지 못했습니다. 잠시 후 다시 시도하세요.");
	}
}
