package org.doit.onbank.account.service;

import java.util.NoSuchElementException;
import java.util.Objects;

import org.doit.onbank.account.entity.Account;
import org.doit.onbank.account.repository.AccountRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

// 계좌 비밀번호 검증
// REQUIRES_NEW : 출금 트랜잭션과 따로 커밋한다 → 출금이 롤백돼도 오류 횟수는 남는다
// AccountService 안의 메서드로 두면 프록시를 거치지 않아 REQUIRES_NEW 가 무시되므로 별도 Bean 으로 분리
@Component
@RequiredArgsConstructor
public class AccountPasswordVerifier {

	private final AccountRepository accountRepository;
	private final PasswordEncoder passwordEncoder;

	// 일치하면 true (오류 횟수 0으로 초기화), 틀리면 false (오류 횟수 1 증가)
	// 오류 횟수를 커밋해야 하므로 비밀번호가 틀린 경우는 예외 대신 false 를 돌려준다
	// (이 메서드 안에서 예외를 던지면 REQUIRES_NEW 트랜잭션이 롤백되어 증가한 횟수가 사라진다)
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public boolean verify(Long accountId, Long memberId, String rawPassword) {
		Account account = accountRepository.findByIdForUpdate(accountId)
				.orElseThrow(() -> new NoSuchElementException("계좌가 없습니다."));

		// 본인 계좌가 아니면 오류 횟수를 올리지 않는다 (남의 계좌를 잠그는 것 방지)
		if (!Objects.equals(account.getMemberId(), memberId)) {
			throw new IllegalArgumentException("본인 계좌만 이용할 수 있습니다.");
		}
		if (account.isPasswordFailLimitReached()) {
			throw new IllegalStateException("계좌 비밀번호 오류 " + Account.MAX_PASSWORD_FAIL_COUNT + "회로 이용할 수 없습니다.");
		}

		if (rawPassword != null && passwordEncoder.matches(rawPassword, account.getPasswordHash())) {
			account.resetPasswordFailCount();
			return true;
		}
		account.increasePasswordFailCount();
		return false;
	}
}
