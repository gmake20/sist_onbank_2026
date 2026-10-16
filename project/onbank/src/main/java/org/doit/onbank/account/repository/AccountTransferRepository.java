package org.doit.onbank.account.repository;

import java.util.Collection;
import java.util.List;

import org.doit.onbank.account.entity.AccountTransfer;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccountTransferRepository extends JpaRepository<AccountTransfer, Long> {

	// 최근입금계좌 : 내 계좌들에서 이체한 입금계좌번호를 마지막 이체가 최근인 순서로 (같은 계좌는 1번만)
	@Query("select t.depositAccountNumber as accountNumber, max(t.transferAt) as lastTransferAt"
			+ " from AccountTransfer t where t.accountId in :accountIds"
			+ " group by t.depositAccountNumber order by max(t.transferAt) desc")
	List<RecentDepositAccount> findRecentDepositAccounts(@Param("accountIds") Collection<Long> accountIds,
			Pageable pageable);
}
