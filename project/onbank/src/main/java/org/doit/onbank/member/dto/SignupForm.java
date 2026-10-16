package org.doit.onbank.member.dto;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

// 회원가입 입력값 : 형식 검증만 여기서 한다
// 아이디·휴대폰·이메일 중복 같은 업무 검증은 MemberService에서 처리
@Getter
@Setter
public class SignupForm {

	@NotBlank(message = "아이디를 입력하세요.")
	@Pattern(regexp = "^[a-z0-9]{5,20}$", message = "아이디는 영문 소문자·숫자 5~20자로 입력하세요.")
	private String loginId;

	@NotBlank(message = "비밀번호를 입력하세요.")
	@Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[!@#$%^&*]).{8,20}$",
			message = "비밀번호는 영문·숫자·특수문자(!@#$%^&*)를 모두 포함해 8~20자로 입력하세요.")
	private String password;

	@NotBlank(message = "비밀번호 확인을 입력하세요.")
	private String passwordConfirm;

	@NotBlank(message = "이름을 입력하세요.")
	@Size(max = 30, message = "이름은 30자 이하로 입력하세요.")
	private String name;

	@NotNull(message = "생년월일을 입력하세요.")
	@Past(message = "생년월일을 확인하세요.")
	@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)	// <input type="date"> 값 (yyyy-MM-dd)
	private LocalDate birthDate;

	// 숫자만 저장하므로 하이픈 없이 입력받는다
	@NotBlank(message = "휴대폰 번호를 입력하세요.")
	@Pattern(regexp = "^010\\d{8}$", message = "휴대폰 번호는 010으로 시작하는 숫자 11자리로 입력하세요.")
	private String phone;

	@NotBlank(message = "이메일을 입력하세요.")
	@Email(message = "이메일 형식이 올바르지 않습니다.")
	@Size(max = 100, message = "이메일은 100자 이하로 입력하세요.")
	private String email;

	@AssertTrue(message = "필수 약관에 동의해 주세요.")
	private boolean termsAgreed;

	private boolean marketingAgreed;	// 선택 약관
}
