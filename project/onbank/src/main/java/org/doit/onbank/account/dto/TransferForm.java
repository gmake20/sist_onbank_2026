package org.doit.onbank.account.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import org.springframework.format.annotation.DateTimeFormat;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

// 이체 화면 입력값 (당행 즉시이체 / 예약이체 / 정기이체)
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

	// 예약이체 일시 : [예약이체일지정]을 눌렀을 때만 전송된다 (둘 다 비어 있으면 즉시이체)
	@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
	private LocalDate executeDate;		// <input type="date"> : 2026-10-21

	@DateTimeFormat(pattern = "HH:mm")	// 브라우저가 보내는 형식 그대로 (ISO.TIME 은 초·시간대까지 붙여 다시 그릴 때 어긋남)
	private LocalTime executeTime;		// <input type="time"> : 09:30

	// 정기이체 : [정기]를 골랐을 때만 전송된다 (매월 transferDay일 recurringTime, endDate 는 선택)
	@Min(value = 1, message = "정기이체 날짜는 1일부터 31일 사이로 선택하세요.")
	@Max(value = 31, message = "정기이체 날짜는 1일부터 31일 사이로 선택하세요.")
	private Integer transferDay;		// <select> 1~31

	@DateTimeFormat(pattern = "HH:mm")
	private LocalTime recurringTime;	// <input type="time"> : 09:00

	@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
	private LocalDate endDate;			// 비우면 해지할 때까지

	public boolean isScheduled() {
		return executeDate != null || executeTime != null;
	}

	public boolean isRecurring() {
		return transferDay != null || recurringTime != null;
	}

	// 날짜·시각 중 하나라도 비어 있으면 null (Service 가 "예약 일시를 선택하세요"로 안내)
	public LocalDateTime getExecuteAt() {
		return (executeDate == null || executeTime == null) ? null : LocalDateTime.of(executeDate, executeTime);
	}
}
