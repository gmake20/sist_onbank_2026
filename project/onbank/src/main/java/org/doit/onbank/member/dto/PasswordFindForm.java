package org.doit.onbank.member.dto;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

// 비밀번호 찾기 (화면 5번) 입력값. 네 값이 모두 가입 정보와 일치해야 임시 비밀번호를 보낸다
@Getter
@Setter
public class PasswordFindForm {

	@NotBlank(message = "아이디를 입력하세요.")
	private String loginId;

	@NotBlank(message = "이름을 입력하세요.")
	private String name;

	@NotNull(message = "생년월일을 입력하세요.")
	@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)	// <input type="date"> 값 (yyyy-MM-dd)
	private LocalDate birthDate;

	@NotBlank(message = "이메일을 입력하세요.")
	@Email(message = "이메일 형식이 올바르지 않습니다.")
	private String email;
}
