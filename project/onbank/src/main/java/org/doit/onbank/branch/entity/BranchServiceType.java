package org.doit.onbank.branch.entity;

// branch_service.service_code : 지점/ATM에서 이용 가능한 업무
// 계좌개설·대출상담·카드발급은 창구 업무라 ATM에는 등록할 수 없다 (등록 기능을 만들 때 Branch에서 검사)
public enum BranchServiceType {
	DEPOSIT,			// 입금
	WITHDRAW,			// 출금
	TRANSFER,			// 이체
	BALANCE_INQUIRY,	// 잔액조회
	ACCOUNT_OPENING,	// 계좌개설 (창구)
	LOAN_CONSULTING,	// 대출상담 (창구)
	CARD_ISSUANCE		// 카드발급 (창구)
}
