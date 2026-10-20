package org.doit.onbank.common.security;

import java.security.SecureRandom;

import org.springframework.stereotype.Component;

// 임시 비밀번호 생성 : 헷갈리는 글자(I, l, O, 0, 1)를 뺀 영문·숫자 10자리
// 임시 비밀번호는 로그에 남기지 않는다
@Component
public class TempPasswordGenerator {

	private static final String CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
	private static final int LENGTH = 10;

	private final SecureRandom random = new SecureRandom();

	public String generate() {
		StringBuilder sb = new StringBuilder(LENGTH);
		for (int i = 0; i < LENGTH; i++) {
			sb.append(CHARS.charAt(random.nextInt(CHARS.length())));
		}
		return sb.toString();
	}
}
