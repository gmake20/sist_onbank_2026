package org.doit.onbank.common.mail;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

// 실제 발송 : spring.mail.* (host, username, 앱 비밀번호 등) 설정으로 SMTP 서버를 통해 보낸다
// onbank.mail.enabled=true 일 때만 등록된다. 설정은 application-local.properties 에 둔다 (Git에 올리지 않음)
@Component
@ConditionalOnProperty(name = "onbank.mail.enabled", havingValue = "true")
public class SmtpEmailSender implements EmailSender {

	private final JavaMailSender mailSender;
	private final String from;

	public SmtpEmailSender(JavaMailSender mailSender, @Value("${spring.mail.username}") String from) {
		this.mailSender = mailSender;
		this.from = from;
	}

	@Override
	public void send(String to, String subject, String text) {
		SimpleMailMessage message = new SimpleMailMessage();
		message.setFrom(from);
		message.setTo(to);
		message.setSubject(subject);
		message.setText(text);
		mailSender.send(message);	// 실패하면 MailException
	}
}
