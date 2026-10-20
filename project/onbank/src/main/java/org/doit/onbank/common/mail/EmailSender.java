package org.doit.onbank.common.mail;

// 메일 발송 창구 : 어떤 메일 서비스를 쓰든 이 인터페이스에만 의존한다
// - onbank.mail.enabled=true  : SmtpEmailSender (spring.mail.* 설정으로 실제 발송)
// - 그 외 (prod 제외)          : ConsoleEmailSender (보내지 않고 콘솔에 출력)
public interface EmailSender {

	// to : 받는 사람 이메일 주소, text : 일반 텍스트 본문. 발송에 실패하면 예외를 던진다
	void send(String to, String subject, String text);
}
