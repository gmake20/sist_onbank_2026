package org.doit.onbank;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing	// BaseEntity의 created_at, updated_at 자동 설정
public class OnbankApplication {

	public static void main(String[] args) {
		SpringApplication.run(OnbankApplication.class, args);
	}

}
