package org.doit.onbank.account.repository;

// 자산 리포트 : 회원의 월별 입출금 집계 (AssetReportRepository.findMonthlyCashFlows)
// 본인 계좌끼리 이체한 금액은 입금·출금에서 뺀다 (돈이 밖으로 나가거나 들어온 것이 아니므로)
public interface MonthlyCashFlow {

	String getYm();						// 기준년월 'yyyyMM'

	Long getInAmount();					// 입금 + 이체 받음 (본인 계좌끼리 제외)

	Long getOutAmount();				// 출금 + 이체 보냄 (본인 계좌끼리 제외)

	Long getSavingsAmount();			// 예금·적금 계좌로 들어온 금액 (본인 계좌에서 옮긴 것 포함)

	Long getUtilityBillAmount();		// 메모에 공과금·전기·가스·수도·통신·세금이 들어간 출금

	Long getMaintenanceFeeAmount();		// 메모에 관리비가 들어간 출금
}
