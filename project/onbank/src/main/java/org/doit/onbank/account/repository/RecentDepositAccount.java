package org.doit.onbank.account.repository;

import java.time.LocalDateTime;

// 최근입금계좌 조회 결과 (account_transfer 에서 입금계좌번호별 마지막 이체 일시)
public interface RecentDepositAccount {

	String getAccountNumber();

	LocalDateTime getLastTransferAt();
}
