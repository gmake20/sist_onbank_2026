package org.doit.onbank.member.controller;

import org.doit.onbank.common.exception.BusinessException;
import org.doit.onbank.common.security.PasswordChangeForm;
import org.doit.onbank.member.dto.MyInfo;
import org.doit.onbank.member.dto.ProfileEditForm;
import org.doit.onbank.member.security.LoginMember;
import org.doit.onbank.member.service.EmailVerificationService;
import org.doit.onbank.member.service.MemberPasswordService;
import org.doit.onbank.member.service.MemberService;
import org.doit.onbank.member.service.MemberWithdrawService;
import org.doit.onbank.member.service.PhoneVerificationService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

// 마이페이지 (화면 29번) : 회원정보 조회·수정, 비밀번호 변경, 회원 탈퇴
// 와이어프레임은 /mypage 지만, 로그인 고객 전용 영역(/mybank/**, SecurityConfig)에 맞춰 /mybank/profile 로 둔다
// 회원정보 수정의 휴대폰·이메일 인증은 회원가입과 같은 API(/api/phone/**, /api/email/**)를 쓰고,
// 저장할 때 바뀐 값이 인증됐는지 서버가 다시 확인한다 (SignupController와 같은 방식)
@Controller
@RequiredArgsConstructor
@RequestMapping("/mybank/profile")
public class MyPageController {

	private final MemberService memberService;
	private final MemberPasswordService memberPasswordService;
	private final MemberWithdrawService memberWithdrawService;
	private final PhoneVerificationService phoneVerificationService;
	private final EmailVerificationService emailVerificationService;

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

	// ---------- 회원정보 수정 ----------

	@GetMapping("/edit")
	public String editForm(@AuthenticationPrincipal LoginMember me, HttpSession session, Model model) {
		MyInfo info = memberService.getMyInfo(me.getMemberId());
		ProfileEditForm form = ProfileEditForm.of(info);
		addEditAttributes(info, form, session, model);
		model.addAttribute("form", form);
		return "mypage/edit";
	}

	@PostMapping("/edit")
	public String edit(@AuthenticationPrincipal LoginMember me,
			@Valid @ModelAttribute("form") ProfileEditForm form, BindingResult bindingResult,
			HttpSession session, Model model, RedirectAttributes redirectAttributes) {
		MyInfo info = memberService.getMyInfo(me.getMemberId());
		addEditAttributes(info, form, session, model);

		// 바뀐 값만 인증 필요 (인증을 쓰지 않는 설정이면 통과)
		if (phoneVerificationService.isEnabled() && !bindingResult.hasFieldErrors("phone")
				&& !form.getPhone().equals(info.phone())
				&& !phoneVerificationService.isVerified(form.getPhone(), session)) {
			bindingResult.rejectValue("phone", "notVerified", "바꾼 휴대폰 번호의 인증을 완료해 주세요.");
		}
		if (emailVerificationService.isEnabled() && !bindingResult.hasFieldErrors("email")
				&& MemberService.isEmailChanged(info.email(), form.getEmail())
				&& !emailVerificationService.isVerified(form.getEmail(), session)) {
			bindingResult.rejectValue("email", "notVerified", "바꾼 이메일의 인증을 완료해 주세요.");
		}
		if (bindingResult.hasErrors()) {
			return "mypage/edit";
		}

		try {
			memberService.updateProfile(me.getMemberId(), form);
		} catch (BusinessException e) {
			bindingResult.reject("editFail", e.getMessage());
			return "mypage/edit";
		}

		phoneVerificationService.clear(form.getPhone(), session);
		emailVerificationService.clear(form.getEmail(), session);
		redirectAttributes.addFlashAttribute("message", "회원정보가 수정되었습니다.");
		return "redirect:/mybank/profile";
	}

	// 인증 칸 표시용 : 지금 입력값이 기존 값이거나 이미 인증했으면 "인증 완료"로 보여 준다
	private void addEditAttributes(MyInfo info, ProfileEditForm form, HttpSession session, Model model) {
		model.addAttribute("info", info);
		model.addAttribute("phoneVerificationEnabled", phoneVerificationService.isEnabled());
		model.addAttribute("phoneVerified", form.getPhone() != null && !form.getPhone().equals(info.phone())
				&& phoneVerificationService.isVerified(form.getPhone(), session));
		model.addAttribute("emailVerificationEnabled", emailVerificationService.isEnabled());
		model.addAttribute("emailVerified", MemberService.isEmailChanged(info.email(), form.getEmail())
				&& emailVerificationService.isVerified(form.getEmail(), session));
	}

	// ---------- 회원 탈퇴 ----------

	// 탈퇴할 수 없는 사유(남은 잔액, 예적금, 대출 등)가 있으면 미리 보여 주고 탈퇴 버튼을 막는다
	@GetMapping("/withdraw")
	public String withdrawForm(@AuthenticationPrincipal LoginMember me, Model model) {
		model.addAttribute("blockers", memberWithdrawService.findBlockers(me.getMemberId()));
		return "mypage/withdraw";
	}

	// 탈퇴하면 바로 로그아웃 (탈퇴 회원은 다시 로그인할 수 없음)
	@PostMapping("/withdraw")
	public String withdraw(@AuthenticationPrincipal LoginMember me,
			@RequestParam(name = "password", required = false) String password,
			@RequestParam(name = "agreed", defaultValue = "false") boolean agreed,
			HttpServletRequest request, HttpServletResponse response, Model model) {
		if (!agreed) {
			model.addAttribute("errorMessage", "탈퇴 안내 사항에 동의해 주세요.");
		} else {
			try {
				memberWithdrawService.withdraw(me.getMemberId(), password);
				new SecurityContextLogoutHandler().logout(request, response,
						SecurityContextHolder.getContext().getAuthentication());
				return "redirect:/login?withdrawn";
			} catch (BusinessException e) {
				model.addAttribute("errorMessage", e.getMessage());
			}
		}
		model.addAttribute("blockers", memberWithdrawService.findBlockers(me.getMemberId()));
		return "mypage/withdraw";
	}
}
