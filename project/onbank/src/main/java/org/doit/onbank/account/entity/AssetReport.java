package org.doit.onbank.account.entity;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

// 자산 리포트 (특정 기준년월의 자산 증감 및 항목별 리포트)
@Entity
@Table(name = "asset_report")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)	// JPA용 기본 생성자
@ToString
public class AssetReport {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)	// AUTO_INCREMENT
	@Column(nullable = false)
	private Long assetReportId;				// 자산 리포트 식별자

	private Long accountId;					// 기준 계좌 (또는 회원)

	private Long recentDepositAmount;		// 최근 입금액

	private Long recentWithdrawalAmount;	// 최근 출금액

	private Long savingsAmount;			// 저축금액

	private Long loanInterestRepaymentAmount;	// 대출 이자/상환액

	private Long investmentAmount;		// 투자 금액

	private Long utilityBillAmount;		// 공과금 지출액

	private Long maintenanceFeeAmount;	// 관리비 지출액

	// CHAR(6). String 기본 매핑은 VARCHAR라서 CHAR로 지정
	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(length = 6)
	private String standardYearMonth;			// 기준 년월 (예: '202610')

	@Builder
	private AssetReport(Long accountId, Long recentDepositAmount, Long recentWithdrawalAmount,
			Long savingsAmount, Long loanInterestRepaymentAmount, Long investmentAmount,
			Long utilityBillAmount, Long maintenanceFeeAmount, String standardYearMonth) {
		this.accountId = accountId;
		this.recentDepositAmount = recentDepositAmount;
		this.recentWithdrawalAmount = recentWithdrawalAmount;
		this.savingsAmount = savingsAmount;
		this.loanInterestRepaymentAmount = loanInterestRepaymentAmount;
		this.investmentAmount = investmentAmount;
		this.utilityBillAmount = utilityBillAmount;
		this.maintenanceFeeAmount = maintenanceFeeAmount;
		this.standardYearMonth = standardYearMonth;
	}
}
