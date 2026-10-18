package org.doit.onbank.otp.service;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

// TOTP 번호 계산 (RFC 6238, HMAC-SHA1, 30초, 6자리)
// Google Authenticator 등 OTP 앱과 같은 방식이라 그 앱으로 등록해도 같은 번호가 나온다
// 상태가 없는 계산이라 static 메서드로만 둔다
public final class TotpGenerator {

	public static final int STEP_SECONDS = 30;
	public static final int DIGITS = 6;
	private static final int MODULUS = 1_000_000;	// 10^DIGITS

	private TotpGenerator() {
	}

	// 시각 → 30초 구간 번호 (1970-01-01 00:00:00 UTC부터 몇 번째 30초인지)
	public static long step(Instant now) {
		return Math.floorDiv(now.getEpochSecond(), STEP_SECONDS);
	}

	// 구간 번호 → 6자리 번호 (앞자리 0 포함, 예: "005924")
	public static String generate(byte[] seed, long step) {
		byte[] hash = hmacSha1(seed, ByteBuffer.allocate(Long.BYTES).putLong(step).array());

		// RFC 4226 dynamic truncation : 마지막 바이트의 하위 4비트 위치에서 4바이트를 읽는다
		int offset = hash[hash.length - 1] & 0x0f;
		int binary = ((hash[offset] & 0x7f) << 24)
				| ((hash[offset + 1] & 0xff) << 16)
				| ((hash[offset + 2] & 0xff) << 8)
				| (hash[offset + 3] & 0xff);

		return String.format("%0" + DIGITS + "d", binary % MODULUS);
	}

	// 입력한 번호가 그 구간의 번호와 같은지 (비교 시간이 일정해 글자별로 맞춰 보는 공격을 막음)
	public static boolean matches(byte[] seed, long step, String code) {
		if (code == null || code.length() != DIGITS) {
			return false;
		}
		return MessageDigest.isEqual(generate(seed, step).getBytes(), code.getBytes());
	}

	private static byte[] hmacSha1(byte[] key, byte[] message) {
		try {
			Mac mac = Mac.getInstance("HmacSHA1");
			mac.init(new SecretKeySpec(key, "HmacSHA1"));
			return mac.doFinal(message);
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException("HmacSHA1 을 사용할 수 없습니다.", e);	// 모든 JDK에 있으므로 발생하지 않음
		}
	}
}
