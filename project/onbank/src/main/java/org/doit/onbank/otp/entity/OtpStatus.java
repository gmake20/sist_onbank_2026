package org.doit.onbank.otp.entity;

import java.util.List;

// 가상 OTP 기기 상태 (otp_device.status)
// 발급(ISSUED) ─연속 2회 입력→ 사용중(ACTIVE) ─오류 10회→ 정지(SUSPENDED)
//                                   └─분실 신고→ 분실(LOST)
// 등록 전에 다시 발급받으면 이전 기기는 폐기(DISPOSED)
public enum OtpStatus {
	ISSUED,		// 발급 (사용 등록 전)
	ACTIVE,		// 사용중
	SUSPENDED,	// 정지 (오류 10회)
	LOST,		// 분실
	DISPOSED;	// 폐기

	// 회원당 1개만 있을 수 있는 상태 (DB 유일 인덱스 ux_otp_device_one_usable 와 같은 기준)
	public static final List<OtpStatus> USABLE = List.of(ISSUED, ACTIVE, SUSPENDED);
}
