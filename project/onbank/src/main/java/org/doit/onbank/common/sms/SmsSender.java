package org.doit.onbank.common.sms;

// 문자 발송 창구 : 어떤 문자 서비스를 쓰든 이 인터페이스에만 의존한다
// - 개발(prod 외) : ConsoleSmsSender (보내지 않고 콘솔에 출력)
// - 배포(prod)   : ApiSmsSender (다음 단계 : 문자 서비스 선택 후 구현, docs/auth.md 3장 ③)
//                  구현 전까지 prod에는 발송기가 없으며, onbank.phone-verification.enabled=false 로 둔다
public interface SmsSender {

	// to : 숫자만 있는 휴대폰 번호. 발송에 실패하면 예외를 던진다
	void send(String to, String text);
}
