package org.doit.onbank.account.service;

// 기준 금액을 넘는 이체인데 사용 중인 OTP가 없을 때 (미발급, 발급만 받고 사용 등록 전)
// 화면은 이 예외를 받으면 오류 문구와 함께 [OTP 등록하기] 버튼을 보여 준다
public class OtpRequiredException extends IllegalStateException {

	public OtpRequiredException(String message) {
		super(message);
	}
}
