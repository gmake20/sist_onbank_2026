package org.doit.onbank.otp.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

// RFC 6238 부록 B의 공식 테스트 값으로 계산이 표준과 같은지 확인
// RFC 값은 8자리라 뒤 6자리와 비교한다 (6자리 = 같은 값을 10^6 으로 나눈 나머지)
class TotpGeneratorTests {

	private static final byte[] RFC_SEED = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);

	@ParameterizedTest
	@CsvSource({
			"59,          287082",
			"1111111109,  081804",
			"1111111111,  050471",
			"1234567890,  005924",
			"2000000000,  279037",
			"20000000000, 353130"
	})
	void matchesRfc6238Vectors(long epochSecond, String expected) {
		long step = TotpGenerator.step(Instant.ofEpochSecond(epochSecond));

		assertThat(TotpGenerator.generate(RFC_SEED, step)).isEqualTo(expected);
	}

	@Test
	void stepChangesEvery30Seconds() {
		assertThat(TotpGenerator.step(Instant.ofEpochSecond(0))).isZero();
		assertThat(TotpGenerator.step(Instant.ofEpochSecond(29))).isZero();
		assertThat(TotpGenerator.step(Instant.ofEpochSecond(30))).isEqualTo(1);
	}

	@Test
	void matchesChecksCodeAndFormat() {
		long step = TotpGenerator.step(Instant.ofEpochSecond(1234567890));

		assertThat(TotpGenerator.matches(RFC_SEED, step, "005924")).isTrue();
		assertThat(TotpGenerator.matches(RFC_SEED, step, "005925")).isFalse();
		assertThat(TotpGenerator.matches(RFC_SEED, step, "5924")).isFalse();	// 앞자리 0 생략은 틀림
		assertThat(TotpGenerator.matches(RFC_SEED, step, null)).isFalse();
	}
}
