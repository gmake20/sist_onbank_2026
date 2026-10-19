package org.doit.onbank.account.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

// 이체 화면 입력값 (당행 즉시이체)
@Getter
@Setter
public class TransferForm {

	@NotNull(message = "출금계좌를 선택하세요.")
	private Long fromAccountId;

	@NotBlank(message = "계좌 비밀번호를 입력하세요.")
	@Pattern(regexp = "\\d{4}", message = "계좌 비밀번호는 숫자 4자리입니다.")
	private String password;

	// 기준 금액을 넘는 타인 계좌 이체일 때만 필요하다. 필요한지는 서버(AccountTransferService)가 다시 판단
	@Pattern(regexp = "(\\d{6})?", message = "OTP 번호는 숫자 6자리입니다.")
	private String otpCode;

	@NotNull(message = "이체금액을 입력하세요.")
	@Min(value = 1, message = "이체금액은 1원 이상이어야 합니다.")
	private Long amount;

	@NotBlank(message = "입금계좌번호를 입력하세요.")
	@Size(max = 30, message = "입금계좌번호가 너무 깁니다.")
	private String toAccountNumber;

	@Size(max = 8, message = "보내는분 통장표시내용은 8자까지 입력할 수 있습니다.")
	private String senderMemo;		// 내 통장에 찍히는 내용 (비우면 받는 분 이름)

	@Size(max = 10, message = "받는분 통장표시내용은 10자까지 입력할 수 있습니다.")
	private String receiverMemo;	// 받는 분 통장에 찍히는 내용 (비우면 내 이름)
}
