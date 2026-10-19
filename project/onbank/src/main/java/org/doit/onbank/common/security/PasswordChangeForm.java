package org.doit.onbank.common.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

// 직원·관리자 비밀번호 변경 폼 (/employee/password, /admin/password 공용)
// 새 비밀번호 규칙은 고객 회원가입(SignupForm)과 같다
@Getter
@Setter
public class PasswordChangeForm {

	@NotBlank(message = "현재 비밀번호를 입력하세요.")
	private String currentPassword;

	@NotBlank(message = "새 비밀번호를 입력하세요.")
	@Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[!@#$%^&*]).{8,20}$",
			message = "비밀번호는 영문·숫자·특수문자(!@#$%^&*)를 모두 포함해 8~20자로 입력하세요.")
	private String newPassword;

	@NotBlank(message = "새 비밀번호 확인을 입력하세요.")
	private String newPasswordConfirm;
}
