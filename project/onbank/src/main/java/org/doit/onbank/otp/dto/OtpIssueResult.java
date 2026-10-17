package org.doit.onbank.otp.dto;

// OTP 발급 결과 : 화면에 딱 한 번만 보여 주고 버린다 (서버는 시드를 다시 내보내지 않음)
// registrationCode : 가상 OTP 기기에 직접 입력하는 등록 코드 (시드의 Base32, 32글자)
// otpauthUri       : QR 코드로 만들 값. Google Authenticator 같은 OTP 앱으로도 등록할 수 있다
public record OtpIssueResult(String serialNo, String registrationCode, String otpauthUri) {

	// 화면 표시용 : 4글자씩 띄어 쓴다 (예: ABCD EFGH ...). 띄어 쓴 채로 입력해도 등록된다
	public String formattedRegistrationCode() {
		return registrationCode.replaceAll("(.{4})(?!$)", "$1 ");
	}
}
