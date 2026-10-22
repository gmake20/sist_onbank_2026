package org.doit.onbank.member.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

// 마이페이지 > 회원정보 수정 입력값 : 형식 검증만 여기서 한다 (SignupForm과 같은 규칙)
// 바뀐 값의 인증·중복 검사와 현재 비밀번호 확인은 MemberService에서 처리
@Getter
@Setter
public class ProfileEditForm {

	@NotBlank(message = "휴대폰 번호를 입력하세요.")
	@Pattern(regexp = "^010\\d{8}$", message = "휴대폰 번호는 010으로 시작하는 숫자 11자리로 입력하세요.")
	private String phone;

	@NotBlank(message = "이메일을 입력하세요.")
	@Email(message = "이메일 형식이 올바르지 않습니다.")
	@Size(max = 100, message = "이메일은 100자 이하로 입력하세요.")
	private String email;

	private boolean marketingAgreed;

	@NotBlank(message = "현재 비밀번호를 입력하세요.")
	private String currentPassword;

	// 수정 화면 처음 열 때 지금 값으로 채운다
	public static ProfileEditForm of(MyInfo info) {
		ProfileEditForm form = new ProfileEditForm();
		form.setPhone(info.phone());
		form.setEmail(info.email());
		form.setMarketingAgreed(info.marketingAgree());
		return form;
	}
}
