package org.doit.onbank.account.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

// 출금 화면 입력값 (ATM 출금 mock)
@Getter
@Setter
public class WithdrawForm {

	@NotNull(message = "금액을 입력하세요.")
	@Min(value = 1, message = "금액은 1원 이상이어야 합니다.")
	private Long amount;

	@NotBlank(message = "계좌 비밀번호를 입력하세요.")
	@Pattern(regexp = "\\d{4}", message = "계좌 비밀번호는 숫자 4자리입니다.")
	private String password;

	@Size(max = 20, message = "적요는 20자까지 입력할 수 있습니다.")
	private String content;		// 통장 표시 내용 (비우면 "출금")
}
