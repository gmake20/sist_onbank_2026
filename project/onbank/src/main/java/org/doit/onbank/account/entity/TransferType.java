package org.doit.onbank.account.entity;

// account_transfer.transfer_type 에 저장하는 값
// 당행·타행 구분은 financial_institution_id 로 한다 (NULL 이면 당행 ONBANK)
public final class TransferType {

	public static final String IMMEDIATE = "IMMEDIATE";	// 즉시이체 (팀2 이체 화면)
	public static final String SCHEDULED = "SCHEDULED";	// 예약이체 (팀2 예약이체 실행)
	public static final String RECURRING = "RECURRING";	// 정기(자동)이체 (팀2 정기이체 실행)

	private TransferType() {
	}
}
