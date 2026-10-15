package org.doit.onbank.account.repository;

import java.util.List;
import java.util.Optional;

import org.doit.onbank.account.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface AccountRepository extends JpaRepository<Account, Long> {

	// 입금·출금·이체 : 계좌 행을 잠그고 조회 (SELECT ... FOR UPDATE)
	// 트랜잭션이 끝날 때까지 같은 계좌의 다른 거래는 기다린다 → 동시 출금으로 잔액이 틀어지는 것을 막음
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select a from Account a where a.accountId = :accountId")
	Optional<Account> findByIdForUpdate(@Param("accountId") Long accountId);

	// 계좌번호로 조회 (이체 받는 계좌 확인 등)
	Optional<Account> findByAccountNumber(String accountNumber);

	// 계좌 목록 : 회원의 계좌를 개설 순서대로
	List<Account> findByMemberIdOrderByAccountIdAsc(Long memberId);

	// 이체한도 : members 의 한도 컬럼 2개만 조회 (팀원1 테이블, Member 엔티티에 필드가 없어 SQL 로 직접 읽음)
	@Query(value = "select daily_transfer_limit as dailyTransferLimit, once_transfer_limit as onceTransferLimit"
			+ " from members where member_id = :memberId", nativeQuery = true)
	Optional<MemberTransferLimit> findTransferLimit(@Param("memberId") Long memberId);
}
