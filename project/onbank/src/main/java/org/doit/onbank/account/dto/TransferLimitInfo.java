package org.doit.onbank.account.dto;

// 이체한도 조회 결과 (화면의 "이체한도조회" 버튼, 이체 전 검사)
public record TransferLimitInfo(
		long dailyLimit,		// 1일 이체한도
		long onceLimit,			// 1회 이체한도
		long todayUsed) {		// 선택한 계좌에서 오늘 이체한 금액

	// 오늘 더 이체할 수 있는 금액
	public long remaining() {
		return Math.max(0L, dailyLimit - todayUsed);
	}
}
