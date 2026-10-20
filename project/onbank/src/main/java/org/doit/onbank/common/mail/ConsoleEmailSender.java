package org.doit.onbank.common.mail;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

// 개발용 : 실제로 보내지 않고 콘솔에 출력한다 (SMTP 설정이 없는 팀원도 테스트 가능)
// 임시 비밀번호 등이 로그에 남으므로 prod에서는 절대 사용하지 않는다
@Slf4j
@Component
@Profile("!prod")
@ConditionalOnProperty(name = "onbank.mail.enabled", havingValue = "false", matchIfMissing = true)
public class ConsoleEmailSender implements EmailSender {

	@Override
	public void send(String to, String subject, String text) {
		log.info("[DEV MAIL] to={} subject={}\n{}", to, subject, text);
	}
}
