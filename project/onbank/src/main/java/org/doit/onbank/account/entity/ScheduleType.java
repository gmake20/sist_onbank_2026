package org.doit.onbank.account.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// scheduled_transfer.schedule_type 에 저장하는 값
// 실행된 이체의 account_transfer.transfer_type 도 같은 이름(TransferType.SCHEDULED / RECURRING)으로 남긴다
@Getter
@RequiredArgsConstructor
public enum ScheduleType {

	SCHEDULED("예약이체", TransferType.SCHEDULED),	// 지정한 일시에 1회
	RECURRING("정기이체", TransferType.RECURRING);	// 매월 지정한 날 반복

	private final String label;			// 화면에 보여 줄 한글 이름
	private final String transferType;	// 실행할 때 account_transfer.transfer_type 에 기록할 값
}
