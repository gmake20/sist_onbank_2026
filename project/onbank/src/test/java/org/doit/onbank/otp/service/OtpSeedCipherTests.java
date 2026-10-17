package org.doit.onbank.otp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;

import org.junit.jupiter.api.Test;

class OtpSeedCipherTests {

	static final String TEST_KEY = Base64.getEncoder().encodeToString(new byte[32]);	// 테스트 전용 키
	private static final byte[] SEED = "12345678901234567890".getBytes();

	private final OtpSeedCipher cipher = new OtpSeedCipher(TEST_KEY);

	@Test
	void encryptThenDecrypt() {
		String stored = cipher.encrypt(SEED);

		assertThat(cipher.decrypt(stored)).isEqualTo(SEED);
		assertThat(stored.length()).isLessThanOrEqualTo(255);	// otp_device.seed_encrypted VARCHAR(255)
	}

	// IV를 매번 새로 만들므로 같은 시드도 저장값이 달라진다
	@Test
	void sameSeedGivesDifferentCiphertext() {
		assertThat(cipher.encrypt(SEED)).isNotEqualTo(cipher.encrypt(SEED));
	}

	// GCM 인증 태그 : DB 값이 바뀌었거나 키가 다르면 복호화 실패
	@Test
	void tamperedOrWrongKeyFails() {
		String stored = cipher.encrypt(SEED);
		byte[] tampered = Base64.getDecoder().decode(stored);
		tampered[tampered.length - 1] ^= 1;

		assertThatThrownBy(() -> cipher.decrypt(Base64.getEncoder().encodeToString(tampered)))
				.isInstanceOf(IllegalStateException.class);

		byte[] otherKey = new byte[32];
		otherKey[0] = 1;
		OtpSeedCipher other = new OtpSeedCipher(Base64.getEncoder().encodeToString(otherKey));
		assertThatThrownBy(() -> other.decrypt(stored)).isInstanceOf(IllegalStateException.class);
	}

	// 키가 없어도 객체는 만들어진다 (앱 시작은 됨). 사용할 때만 오류
	@Test
	void blankKeyFailsOnlyWhenUsed() {
		OtpSeedCipher noKey = new OtpSeedCipher("");

		assertThatThrownBy(() -> noKey.encrypt(SEED))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("onbank.otp.seed-key");
	}

	@Test
	void wrongKeyLengthFailsImmediately() {
		assertThatThrownBy(() -> new OtpSeedCipher(Base64.getEncoder().encodeToString(new byte[16])))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
