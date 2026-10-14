package org.doit.onbank.account.entity;

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

// 금융기관 코드 (타행 이체, 즐겨찾기에서 사용)
@Entity
@Table(name = "financial_institution_code")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)	// JPA용 기본 생성자
@ToString
public class FinancialInstitution {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)	// AUTO_INCREMENT
	@Column(nullable = false)
	private Long financialInstitutionId;

	@Column(length = 100)
	private String financialInstitutionName;		// 예: 신한은행, 국민은행

	@Column(length = 20)
	private String financialInstitutionCategoryCode;	// 은행, 증권 등

	@Builder
	private FinancialInstitution(String financialInstitutionName, String financialInstitutionCategoryCode) {
		this.financialInstitutionName = financialInstitutionName;
		this.financialInstitutionCategoryCode = financialInstitutionCategoryCode;
	}
}
