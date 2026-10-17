package org.doit.onbank.otp.service;

// OtpCodeChecker 검증 결과. failCount 는 MISMATCH 일 때 지금까지의 연속 오류 횟수
public record OtpCheckResult(Type type, int failCount) {

	public enum Type {
		OK,				// 일치
		MISMATCH,		// 불일치 (오류 횟수 +1)
		SUSPENDED_NOW,	// 이번 오류로 10회가 되어 정지됨
		REUSED,			// 이미 사용한 구간의 번호 (오류 횟수는 올리지 않음)
		NOT_REGISTERED,	// 발급받은 OTP가 없음
		NOT_ACTIVATED,	// 발급만 받고 사용 등록 전
		SUSPENDED		// 이미 정지된 OTP
	}

	public static OtpCheckResult of(Type type) {
		return new OtpCheckResult(type, 0);
	}
}
