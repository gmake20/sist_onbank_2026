package org.doit.onbank.account.service;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import org.doit.onbank.account.entity.AccountType;
import org.doit.onbank.account.repository.AccountRepository;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

// 계좌번호(account_number) 생성
// 형식 : 유형별 앞자리4-난수3-난수6 (예: 1002-482-915307)
// 앞자리 : 입출금 1002 / 예금 2002 / 적금 3002 / 대출 4002
// account_number 는 UNIQUE 이므로 이미 있는 번호면 다시 만든다
@Component
@RequiredArgsConstructor
public class AccountNumberGenerator {

	private static final Map<AccountType, String> PREFIXES = Map.of(
			AccountType.CHECKING, "1002",
			AccountType.DEPOSIT, "2002",
			AccountType.SAVINGS, "3002",
			AccountType.LOAN, "4002");
	private static final int MAX_TRY = 5;

	private final AccountRepository accountRepository;

	public String generate(AccountType accountType) {
		String prefix = accountType == null ? null : PREFIXES.get(accountType);	// Map.of 는 get(null) 에서 예외가 나므로 먼저 거른다
		if (prefix == null) {
			throw new IllegalArgumentException("알 수 없는 계좌 유형입니다: " + accountType);
		}
		for (int i = 0; i < MAX_TRY; i++) {
			ThreadLocalRandom random = ThreadLocalRandom.current();
			String number = String.format("%s-%03d-%06d", prefix, random.nextInt(1_000), random.nextInt(1_000_000));
			if (!accountRepository.existsByAccountNumber(number)) {
				return number;
			}
		}
		throw new IllegalStateException("계좌번호를 만들지 못했습니다. 잠시 후 다시 시도하세요.");
	}

	// 사용자가 입력한 계좌번호 정리 : 숫자 13자리면 4-3-6 하이픈을 넣는다 ("1002482915307" → "1002-482-915307")
	// 화면에서 자동으로 넣지만, 스크립트가 꺼져 있거나 붙여넣기한 경우를 위해 서버도 맞춘다
	// 그 밖의 값(TEST-0001 같은 테스트 계좌)은 앞뒤 공백만 뺀다
	public static String normalize(String input) {
		if (input == null) {
			return null;
		}
		String trimmed = input.trim();
		if (trimmed.matches("\\d{13}")) {
			return trimmed.substring(0, 4) + "-" + trimmed.substring(4, 7) + "-" + trimmed.substring(7);
		}
		return trimmed;
	}
}
