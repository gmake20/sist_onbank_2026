package org.doit.onbank.account.service;

import java.util.NoSuchElementException;
import java.util.function.Consumer;

import org.doit.onbank.account.entity.Account;
import org.doit.onbank.account.repository.AccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

// 계좌 상태 변경 : 관리자 쪽(팀1 본인확인 심사, 팀4 관리자 화면)이 호출하는 메서드
// 권한 확인과 관리자 로그 기록은 호출하는 쪽에서 한다
// 상태 규칙에 맞지 않으면 IllegalStateException, 없는 계좌면 NoSuchElementException
@Service
@RequiredArgsConstructor
public class AccountManageService {

	private final AccountRepository accountRepository;

	// 개설 승인 : PENDING → ACTIVE
	@Transactional
	public Account activatePending(Long accountId) {
		return change(accountId, Account::activate);
	}

	// 개설 반려 : PENDING → CLOSED
	@Transactional
	public Account rejectPending(Long accountId) {
		return change(accountId, Account::reject);
	}

	// 거래정지(동결) : ACTIVE → FROZEN
	@Transactional
	public Account freeze(Long accountId) {
		return change(accountId, Account::freeze);
	}

	// 거래정지 해제 : FROZEN → ACTIVE
	@Transactional
	public Account unfreeze(Long accountId) {
		return change(accountId, Account::unfreeze);
	}

	// 해지 : ACTIVE·FROZEN 이고 잔액 0일 때만 → CLOSED
	@Transactional
	public Account close(Long accountId) {
		return change(accountId, Account::close);
	}

	// 비밀번호 오류 잠금 해제 : 오류 횟수를 0으로
	@Transactional
	public Account unlockPassword(Long accountId) {
		return change(accountId, Account::resetPasswordFailCount);
	}

	// 거래 중인 계좌와 동시에 바뀌지 않도록 잠그고 변경 (변경 내용은 트랜잭션이 끝날 때 자동 저장)
	private Account change(Long accountId, Consumer<Account> action) {
		Account account = accountRepository.findByIdForUpdate(accountId)
				.orElseThrow(() -> new NoSuchElementException("계좌가 없습니다."));
		action.accept(account);
		return account;
	}
}
