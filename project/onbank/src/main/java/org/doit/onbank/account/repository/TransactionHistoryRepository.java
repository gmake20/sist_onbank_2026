package org.doit.onbank.account.repository;

import java.util.List;
import java.util.Optional;

import org.doit.onbank.account.entity.TransactionHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionHistoryRepository extends JpaRepository<TransactionHistory, Long> {

	// 거래내역 조회 : 최신 거래부터 (같은 시각이면 나중에 기록된 행부터)
	List<TransactionHistory> findByAccountIdOrderByTransactionAtDescTransactionHistoryIdDesc(Long accountId);

	// 거래 번호 중복 확인 (transaction_sqno 에 UNIQUE 가 없으므로 코드에서 확인)
	boolean existsByTransactionSqno(String transactionSqno);
	
	
}
