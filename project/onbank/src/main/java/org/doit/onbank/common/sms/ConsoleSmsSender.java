package org.doit.onbank.common.sms;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

// 개발용 : 실제로 보내지 않고 콘솔에 출력한다 (비용·발송 제한 없이 테스트)
// 인증번호가 로그에 남으므로 prod에서는 절대 사용하지 않는다
@Slf4j
@Component
@Profile("!prod")
public class ConsoleSmsSender implements SmsSender {

	@Override
	public void send(String to, String text) {
		log.info("[DEV SMS] to={} text={}", to, text);
	}
}
