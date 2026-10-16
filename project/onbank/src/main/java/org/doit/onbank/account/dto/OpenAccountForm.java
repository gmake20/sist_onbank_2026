package org.doit.onbank.account.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

// 계좌 개설 화면 입력값 (입출금 ON 통장)
@Getter
@Setter
public class OpenAccountForm {

	@NotBlank(message = "계좌 비밀번호를 입력하세요.")
	@Pattern(regexp = "\\d{4}", message = "계좌 비밀번호는 숫자 4자리입니다.")
	private String password;

	@NotBlank(message = "계좌 비밀번호를 한 번 더 입력하세요.")
	private String passwordConfirm;

	@AssertTrue(message = "상품설명서와 약관에 동의해야 개설할 수 있습니다.")
	private boolean agreed;

	// 두 비밀번호가 같은지 (비어 있으면 위의 @NotBlank 오류만 보여 준다)
	@AssertTrue(message = "계좌 비밀번호가 서로 다릅니다.")
	public boolean isPasswordMatched() {
		if (password == null || password.isBlank() || passwordConfirm == null || passwordConfirm.isBlank()) {
			return true;
		}
		return password.equals(passwordConfirm);
	}
}
