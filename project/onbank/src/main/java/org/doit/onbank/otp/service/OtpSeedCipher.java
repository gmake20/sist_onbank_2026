package org.doit.onbank.otp.service;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

// OTP 시드 암호화 (AES-256-GCM)
// 시드는 서버가 번호 계산에 다시 써야 하므로 해시가 아니라 되돌릴 수 있는 암호화로 저장한다
// 저장 형식 : Base64( IV 12바이트 + 암호문 + 인증 태그 16바이트 )
// 키는 Git에 올리지 않고 onbank.otp.seed-key (환경변수 OTP_SEED_KEY) 로 넣는다
// 공용 DB를 쓰므로 팀원 모두 같은 키를 써야 다른 사람이 발급한 OTP도 검증된다
@Component
public class OtpSeedCipher {

	private static final String TRANSFORMATION = "AES/GCM/NoPadding";
	private static final int IV_BYTES = 12;
	private static final int TAG_BITS = 128;

	private final SecretKey key;	// 키를 넣지 않았으면 null
	private final SecureRandom random = new SecureRandom();

	// 키가 비어 있어도 앱은 시작한다 (OTP를 쓰지 않는 팀원의 개발 환경이 멈추지 않도록)
	// 대신 OTP 발급·검증을 할 때 오류가 난다
	public OtpSeedCipher(@Value("${onbank.otp.seed-key:}") String base64Key) {
		if (base64Key == null || base64Key.isBlank()) {
			this.key = null;
			return;
		}
		byte[] keyBytes = Base64.getDecoder().decode(base64Key.trim());
		if (keyBytes.length != 32) {
			throw new IllegalArgumentException("onbank.otp.seed-key 는 32바이트(Base64 44글자)여야 합니다.");
		}
		this.key = new SecretKeySpec(keyBytes, "AES");
	}

	public String encrypt(byte[] seed) {
		byte[] iv = new byte[IV_BYTES];
		random.nextBytes(iv);	// 같은 키로 IV를 다시 쓰면 GCM이 깨지므로 매번 새로 만든다
		try {
			Cipher cipher = Cipher.getInstance(TRANSFORMATION);
			cipher.init(Cipher.ENCRYPT_MODE, requireKey(), new GCMParameterSpec(TAG_BITS, iv));
			byte[] encrypted = cipher.doFinal(seed);
			return Base64.getEncoder().encodeToString(
					ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted).array());
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException("OTP 시드를 암호화하지 못했습니다.", e);
		}
	}

	// 키가 다르거나 DB 값이 바뀌었으면 인증 태그가 맞지 않아 실패한다
	public byte[] decrypt(String stored) {
		byte[] data = Base64.getDecoder().decode(stored);
		try {
			Cipher cipher = Cipher.getInstance(TRANSFORMATION);
			cipher.init(Cipher.DECRYPT_MODE, requireKey(), new GCMParameterSpec(TAG_BITS, data, 0, IV_BYTES));
			return cipher.doFinal(data, IV_BYTES, data.length - IV_BYTES);
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException("OTP 시드를 복호화하지 못했습니다. onbank.otp.seed-key 가 발급할 때와 같은지 확인하세요.", e);
		}
	}

	private SecretKey requireKey() {
		if (key == null) {
			throw new IllegalStateException("OTP 암호화 키(onbank.otp.seed-key)가 설정되지 않았습니다. application-local.properties 를 확인하세요.");
		}
		return key;
	}
}
