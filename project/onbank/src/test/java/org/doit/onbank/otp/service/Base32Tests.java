package org.doit.onbank.otp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

// RFC 4648 10장의 공식 테스트 값 (끝의 '=' 는 생략한 형태)
class Base32Tests {

	@ParameterizedTest
	@CsvSource({
			"f,      MY",
			"fo,     MZXQ",
			"foo,    MZXW6",
			"foob,   MZXW6YQ",
			"fooba,  MZXW6YTB",
			"foobar, MZXW6YTBOI"
	})
	void matchesRfc4648Vectors(String plain, String encoded) {
		byte[] bytes = plain.getBytes(StandardCharsets.US_ASCII);

		assertThat(Base32.encode(bytes)).isEqualTo(encoded);
		assertThat(Base32.decode(encoded)).isEqualTo(bytes);
	}

	@Test
	void roundTripSeed() {
		byte[] seed = new byte[20];
		new SecureRandom().nextBytes(seed);

		String code = Base32.encode(seed);

		assertThat(code).hasSize(32);
		assertThat(Base32.decode(code)).isEqualTo(seed);
	}

	// 사람이 입력할 때 : 소문자, 띄어쓰기, 하이픈, '=' 허용
	@Test
	void decodeIgnoresCaseSpacesAndPadding() {
		assertThat(Base32.decode("mzxw 6ytb-oi======")).isEqualTo("foobar".getBytes(StandardCharsets.US_ASCII));
	}

	@Test
	void decodeRejectsInvalidCharacter() {
		assertThatThrownBy(() -> Base32.decode("MZXW1"))	// 1은 Base32에 없음
				.isInstanceOf(IllegalArgumentException.class);
	}
}
