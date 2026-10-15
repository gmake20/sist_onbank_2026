package org.doit.onbank.account.dto;

// 입금계좌 확인 결과 (이체 확인 단계에서 받는 분 이름 표시)
// 다른 사람 이름이 그대로 노출되지 않도록 가운데 글자를 가린다 (예: 홍*동)
public record RecipientInfo(String accountNumber, String maskedName) {

	public static String mask(String name) {
		if (name == null || name.isBlank()) {
			return "";
		}
		if (name.length() == 1) {
			return name;
		}
		if (name.length() == 2) {
			return name.charAt(0) + "*";
		}
		return name.charAt(0) + "*".repeat(name.length() - 2) + name.charAt(name.length() - 1);
	}
}
