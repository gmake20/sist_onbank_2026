package org.doit.onbank.common.mail;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

// 메일 발송 수동 확인용. 환경변수 MAIL_TEST_TO 가 있을 때만 실행된다 (평소 테스트에서는 건너뜀)
// onbank.mail.enabled=true 면 실제로 발송되고, 아니면 콘솔에 출력된다
// 실행 (PowerShell) : $env:MAIL_TEST_TO="받을주소@gmail.com"; ./gradlew test --tests "*EmailSenderSendTests"
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "MAIL_TEST_TO", matches = ".+")
class EmailSenderSendTests {

	@Autowired
	EmailSender emailSender;

	@Test
	void sendTestMail() {
		emailSender.send(System.getenv("MAIL_TEST_TO"), "[OnBank] 메일 발송 테스트",
				"OnBank 메일 발송 설정이 정상입니다.\n발송기 : " + emailSender.getClass().getSimpleName());
	}
}
