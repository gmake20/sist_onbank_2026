package org.doit.onbank.member.controller;

import org.doit.onbank.common.exception.BusinessException;
import org.doit.onbank.common.security.PasswordChangeForm;
import org.doit.onbank.member.security.LoginMember;
import org.doit.onbank.member.service.MemberPasswordService;
import org.doit.onbank.member.service.MemberService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

// 마이페이지 (화면 29번) : 회원정보 조회, 비밀번호 변경
// 와이어프레임은 /mypage 지만, 로그인 고객 전용 영역(/mybank/**, SecurityConfig)에 맞춰 /mybank/profile 로 둔다
// TODO 회원정보 수정, 회원 탈퇴
@Controller
@RequiredArgsConstructor
@RequestMapping("/mybank/profile")
public class MyPageController {

	private final MemberService memberService;
	private final MemberPasswordService memberPasswordService;

	// 세션의 LoginMember는 로그인 시점 값이라 DB에서 다시 읽어 최신 정보를 보여 준다
	@GetMapping
	public String profile(@AuthenticationPrincipal LoginMember me, Model model) {
		model.addAttribute("info", memberService.getMyInfo(me.getMemberId()));
		return "mypage/profile";
	}

	// 비밀번호 변경 화면. 임시 비밀번호(비밀번호 찾기)로 로그인하면 로그인 직후 이 화면으로 온다
	@GetMapping("/password")
	public String passwordForm(@AuthenticationPrincipal LoginMember me, Model model) {
		model.addAttribute("form", new PasswordChangeForm());
		model.addAttribute("tempPassword", me.isTempPassword());
		return "mypage/password";
	}

	// 변경에 성공하면 로그아웃 → 새 비밀번호로 다시 로그인 (세션의 로그인 정보에 임시 비밀번호 표시가 남아 있기 때문)
	@PostMapping("/password")
	public String changePassword(@AuthenticationPrincipal LoginMember me,
			@Valid @ModelAttribute("form") PasswordChangeForm form, BindingResult bindingResult,
			HttpServletRequest request, HttpServletResponse response, Model model) {
		if (!bindingResult.hasFieldErrors("newPassword") && form.getNewPasswordConfirm() != null
				&& !form.getNewPasswordConfirm().equals(form.getNewPassword())) {
			bindingResult.rejectValue("newPasswordConfirm", "mismatch", "새 비밀번호가 일치하지 않습니다.");
		}
		if (!bindingResult.hasErrors()) {
			try {
				memberPasswordService.changePassword(me.getMemberId(), form);
				new SecurityContextLogoutHandler().logout(request, response,
						SecurityContextHolder.getContext().getAuthentication());
				return "redirect:/login?changed";
			} catch (BusinessException e) {
				bindingResult.reject("invalid", e.getMessage());
			}
		}
		model.addAttribute("tempPassword", me.isTempPassword());
		return "mypage/password";
	}
}
