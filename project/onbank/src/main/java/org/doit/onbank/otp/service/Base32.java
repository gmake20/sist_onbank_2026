package org.doit.onbank.otp.service;

import java.io.ByteArrayOutputStream;

// Base32 인코딩 (RFC 4648, 끝의 '=' 생략)
// OTP 등록 코드와 QR(otpauth://)의 secret 값은 Base32 로 쓰는 것이 표준이다
// (JDK에는 Base64만 있어서 직접 구현)
public final class Base32 {

	private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

	private Base32() {
	}

	public static String encode(byte[] data) {
		StringBuilder sb = new StringBuilder((data.length * 8 + 4) / 5);
		int buffer = 0;
		int bits = 0;
		for (byte b : data) {
			buffer = (buffer << 8) | (b & 0xff);
			bits += 8;
			while (bits >= 5) {
				sb.append(ALPHABET.charAt((buffer >> (bits - 5)) & 0x1f));
				bits -= 5;
			}
		}
		if (bits > 0) {
			sb.append(ALPHABET.charAt((buffer << (5 - bits)) & 0x1f));
		}
		return sb.toString();
	}

	// 사람이 직접 입력하는 경우를 위해 소문자, 공백, 하이픈, '=' 를 허용한다
	public static byte[] decode(String text) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		int buffer = 0;
		int bits = 0;
		for (char c : text.toUpperCase().toCharArray()) {
			if (c == ' ' || c == '-' || c == '=') {
				continue;
			}
			int value = ALPHABET.indexOf(c);
			if (value < 0) {
				throw new IllegalArgumentException("Base32 에 없는 글자입니다: " + c);
			}
			buffer = (buffer << 5) | value;
			bits += 5;
			if (bits >= 8) {
				out.write((buffer >> (bits - 8)) & 0xff);
				bits -= 8;
			}
		}
		return out.toByteArray();
	}
}
