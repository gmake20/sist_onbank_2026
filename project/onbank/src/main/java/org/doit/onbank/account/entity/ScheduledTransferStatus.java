package org.doit.onbank.account.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// scheduled_transfer.status 에 저장하는 값
// 예약이체 : WAITING → COMPLETED(성공) / FAILED(실패) / CANCELLED(취소)
// 정기이체 : 회차가 실패해도 WAITING 을 유지하고 last_result 에 사유를 남긴다. 중지하면 PAUSED, 해지하면 CANCELLED
@Getter
@RequiredArgsConstructor
public enum ScheduledTransferStatus {

	WAITING("대기"),
	COMPLETED("완료"),
	FAILED("실패"),
	CANCELLED("취소"),
	PAUSED("중지");

	private final String label;		// 화면에 보여 줄 한글 이름
}
