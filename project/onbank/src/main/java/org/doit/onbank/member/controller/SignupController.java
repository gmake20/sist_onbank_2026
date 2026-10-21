package org.doit.onbank.member.controller;

import org.doit.onbank.common.exception.BusinessException;
import org.doit.onbank.member.dto.SignupForm;
import org.doit.onbank.member.service.EmailVerificationService;
import org.doit.onbank.member.service.MemberService;
import org.doit.onbank.member.service.PhoneVerificationService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

// 회원가입 화면과 처리
// 휴대폰 인증은 화면에서 /api/phone/** (PhoneVerifyApiController)로 먼저 끝내고,
// 가입 POST에서 세션의 인증 완료 여부를 서버가 다시 확인한다 (화면만 막으면 우회 가능)
// onbank.phone-verification.enabled=false 이면 인증 없이 통과 (문자 서비스 연동 전)
// 이메일 인증도 같은 방식 : /api/email/** (EmailVerifyApiController), onbank.email-verification.enabled
@Controller
@RequiredArgsConstructor
public class SignupController {

	private final MemberService memberService;
	private final PhoneVerificationService phoneVerificationService;
	private final EmailVerificationService emailVerificationService;

	@GetMapping("/signup")
	public String signupForm(Model model) {
		model.addAttribute("form", new SignupForm());
		model.addAttribute("phoneVerificationEnabled", phoneVerificationService.isEnabled());
		model.addAttribute("phoneVerified", false);
		model.addAttribute("emailVerificationEnabled", emailVerificationService.isEnabled());
		model.addAttribute("emailVerified", false);
		return "auth/signup";
	}

	@PostMapping("/signup")
	public String signup(@Valid @ModelAttribute("form") SignupForm form, BindingResult bindingResult,
			HttpSession session, Model model, RedirectAttributes redirectAttributes) {
		boolean verificationEnabled = phoneVerificationService.isEnabled();
		boolean phoneVerified = phoneVerificationService.isVerified(form.getPhone(), session);
		model.addAttribute("phoneVerificationEnabled", verificationEnabled);
		model.addAttribute("phoneVerified", phoneVerified);
		boolean emailVerificationEnabled = emailVerificationService.isEnabled();
		boolean emailVerified = emailVerificationService.isVerified(form.getEmail(), session);
		model.addAttribute("emailVerificationEnabled", emailVerificationEnabled);
		model.addAttribute("emailVerified", emailVerified);

		if (form.getPasswordConfirm() != null && !form.getPasswordConfirm().equals(form.getPassword())) {
			bindingResult.rejectValue("passwordConfirm", "mismatch", "비밀번호가 일치하지 않습니다.");
		}
		if (verificationEnabled && !bindingResult.hasFieldErrors("phone") && !phoneVerified) {
			bindingResult.rejectValue("phone", "notVerified", "휴대폰 인증을 완료해 주세요.");
		}
		if (emailVerificationEnabled && !bindingResult.hasFieldErrors("email") && !emailVerified) {
			bindingResult.rejectValue("email", "notVerified", "이메일 인증을 완료해 주세요.");
		}
		if (bindingResult.hasErrors()) {
			return "auth/signup";
		}

		try {
			memberService.signup(form);
		} catch (BusinessException e) {
			bindingResult.reject("signupFail", e.getMessage());
			return "auth/signup";
		}

		phoneVerificationService.clear(form.getPhone(), session);
		emailVerificationService.clear(form.getEmail(), session);
		redirectAttributes.addFlashAttribute("msg", "가입이 완료되었습니다. 로그인해 주세요.");
		return "redirect:/login";
	}
}
