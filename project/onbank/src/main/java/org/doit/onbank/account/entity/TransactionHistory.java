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

// 거래 내역 (계좌에서 발생한 입금, 출금, 이체 등의 모든 거래)
@Entity
@Table(name = "transaction_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)	// JPA용 기본 생성자
@ToString
public class TransactionHistory {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)	// AUTO_INCREMENT
	@Column(nullable = false)
	private Long transactionHistoryId;		// 거래 내역 식별자

	private Long accountId;					// 대상 계좌 ID (account 참조)

	@Column(length = 30)
	private String transactionType;			// 거래 유형 (TransactionType 상수)

	private Long transactionAmount;	// 거래 금액

	private Long balanceAfterTransaction;	// 거래 후 잔액

	@Column(length = 255)
	private String transactionContent;		// 거래 내용 (적요)

	private LocalDateTime transactionAt;	// 거래 일시

	@Column(length = 20)
	private String transactionStatus;		// 거래 상태 (TransactionStatus 상수)

	private Integer paymentRound;			// 납입 회차 (예적금/대출인 경우)

	@Column(length = 100)
	private String transactionSqno;			// 거래 고유 번호 (중복 방지 식별키)

	@Builder
	private TransactionHistory(Long accountId, String transactionType, Long transactionAmount,
			Long balanceAfterTransaction, String transactionContent, LocalDateTime transactionAt,
			String transactionStatus, Integer paymentRound, String transactionSqno) {
		this.accountId = accountId;
		this.transactionType = transactionType;
		this.transactionAmount = transactionAmount;
		this.balanceAfterTransaction = balanceAfterTransaction;
		this.transactionContent = transactionContent;
		this.transactionAt = transactionAt;
		this.transactionStatus = transactionStatus;
		this.paymentRound = paymentRound;
		this.transactionSqno = transactionSqno;
	}
}
