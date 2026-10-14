package org.doit.onbank.account.entity;

import java.time.LocalDateTime;

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

// 계좌 이체 (송금 및 수취 정보 상세)
@Entity
@Table(name = "account_transfer")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)	// JPA용 기본 생성자
@ToString
public class AccountTransfer {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)	// AUTO_INCREMENT
	@Column(nullable = false)
	private Long accountTransferId;			// 이체 식별자

	private Long accountId;					// 출금 계좌 ID

	@Column(length = 30)
	private String withdrawalAccountNumber;	// 출금 계좌번호

	private Long financialInstitutionId;	// 입금 금융기관 ID (financial_institution_code 참조)

	@Column(length = 30)
	private String depositAccountNumber;	// 입금 계좌번호

	private Long transferAmount;		// 이체 금액

	@Column(length = 100)
	private String senderMemo;				// 보내는 분 통장 표시 내용

	@Column(length = 100)
	private String receiverMemo;			// 받는 분 통장 표시 내용

	private LocalDateTime transferAt;		// 이체 일시

	@Column(length = 30)
	private String transferType;			// 이체 유형 (즉시이체, 예약이체 등)

	@Column(length = 100)
	private String transactionSqno;			// 거래 고유 번호

	@Builder
	private AccountTransfer(Long accountId, String withdrawalAccountNumber, Long financialInstitutionId,
			String depositAccountNumber, Long transferAmount, String senderMemo, String receiverMemo,
			LocalDateTime transferAt, String transferType, String transactionSqno) {
		this.accountId = accountId;
		this.withdrawalAccountNumber = withdrawalAccountNumber;
		this.financialInstitutionId = financialInstitutionId;
		this.depositAccountNumber = depositAccountNumber;
		this.transferAmount = transferAmount;
		this.senderMemo = senderMemo;
		this.receiverMemo = receiverMemo;
		this.transferAt = transferAt;
		this.transferType = transferType;
		this.transactionSqno = transactionSqno;
	}
}
