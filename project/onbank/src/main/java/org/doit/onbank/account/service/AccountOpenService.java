package org.doit.onbank.account.service;

import java.time.LocalDate;

import org.doit.onbank.account.entity.Account;
import org.doit.onbank.account.entity.AccountType;
import org.doit.onbank.account.repository.AccountRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

// 계좌 개설
// 지금은 본인확인 없이 바로 정상(ACTIVE)으로 개설한다 (팀1 본인확인 기능이 나오면 개설대기(PENDING) 흐름을 붙인다)
@Service
@RequiredArgsConstructor
public class AccountOpenService {

	private final AccountRepository accountRepository;
	private final AccountNumberGenerator accountNumberGenerator;
	private final PasswordEncoder passwordEncoder;

	// 입출금 계좌(ON 통장) 개설 : 계좌 비밀번호는 숫자 4자리, BCrypt 로 저장
	@Transactional
	public Account openChecking(Long memberId, String rawPassword) {
		validatePassword(rawPassword);

		Account account = Account.open(memberId,
				accountNumberGenerator.generate(AccountType.CHECKING),
				AccountType.CHECKING,
				passwordEncoder.encode(rawPassword),
				LocalDate.now());
		return accountRepository.save(account);
	}

	private void validatePassword(String rawPassword) {
		if (rawPassword == null || !rawPassword.matches("\\d{4}")) {
			throw new IllegalArgumentException("계좌 비밀번호는 숫자 4자리입니다.");
		}
	}
}
