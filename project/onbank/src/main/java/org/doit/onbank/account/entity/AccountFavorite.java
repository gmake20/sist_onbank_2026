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

// 계좌 즐겨찾기 (자주 쓰는 이체 대상 계좌)
@Entity
@Table(name = "account_favorite")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)	// JPA용 기본 생성자
@ToString
public class AccountFavorite {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)	// AUTO_INCREMENT
	@Column(nullable = false)
	private Long accountFavoriteId;			// 즐겨찾기 식별자

	private Long accountId;					// 회원(또는 본인 계좌) ID

	private Long financialInstitutionId;	// 상대방 금융기관 ID

	@Column(length = 30)
	private String recipientAccountNumber;	// 상대방 계좌번호

	@Column(length = 100)
	private String recipientAccountName;	// 상대방 예금주명

	@Column(length = 255)
	private String memo;					// 메모

	@Column(length = 100)
	private String favoriteAlias;			// 즐겨찾기 별칭

	private LocalDateTime favoriteCreatedAt;	// 등록 일시

	private Integer sortOrder;				// 정렬 순서

	@Builder
	private AccountFavorite(Long accountId, Long financialInstitutionId, String recipientAccountNumber,
			String recipientAccountName, String memo, String favoriteAlias, LocalDateTime favoriteCreatedAt,
			Integer sortOrder) {
		this.accountId = accountId;
		this.financialInstitutionId = financialInstitutionId;
		this.recipientAccountNumber = recipientAccountNumber;
		this.recipientAccountName = recipientAccountName;
		this.memo = memo;
		this.favoriteAlias = favoriteAlias;
		this.favoriteCreatedAt = favoriteCreatedAt;
		this.sortOrder = sortOrder;
	}
}
