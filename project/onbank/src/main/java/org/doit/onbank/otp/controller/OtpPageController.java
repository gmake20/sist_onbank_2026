package org.doit.onbank.otp.controller;

import org.doit.onbank.common.exception.BusinessException;
import org.doit.onbank.member.security.LoginMember;
import org.doit.onbank.otp.dto.OtpIssueResult;
import org.doit.onbank.otp.entity.OtpDevice;
import org.doit.onbank.otp.service.OtpService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import lombok.RequiredArgsConstructor;

// 보안설정 > 가상 OTP : 발급 → 등록 코드 1회 표시 → 연속 2회 입력으로 사용 등록 → 번호 확인
// 사용중·정지 OTP는 분실 신고 후 다시 발급받는다
// /mybank/** 는 SecurityConfig 에서 로그인한 고객(ROLE_USER)만 접근 가능
// 처리 결과는 모두 PRG(리다이렉트 + flash)로 보여 준다 (새로고침해도 다시 발급·검증되지 않도록)
@Controller
@RequiredArgsConstructor
@RequestMapping("/mybank/security/otp")
public class OtpPageController {

	private static final String HOME = "redirect:/mybank/security/otp";

	private final OtpService otpService;

	// OTP 상태 화면 : 상태에 따라 발급 버튼 / 사용 등록 입력 / 번호 확인 입력을 보여 준다
	@GetMapping
	public String home(@AuthenticationPrincipal LoginMember me, Model model) {
		model.addAttribute("device", otpService.findCurrent(me.getMemberId()).orElse(null));
		model.addAttribute("maxFailCount", OtpDevice.MAX_FAIL_COUNT);
		return "security/otp";
	}

	@PostMapping("/issue")
	public String issue(@AuthenticationPrincipal LoginMember me, RedirectAttributes redirectAttributes) {
		try {
			OtpIssueResult issued = otpService.issue(me.getMemberId());
			redirectAttributes.addFlashAttribute("issued", issued);
			return "redirect:/mybank/security/otp/issued";
		} catch (BusinessException | IllegalStateException e) {
			redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
			return HOME;
		}
	}

	// 등록 코드 화면 : 발급 직후 한 번만 보인다. 결과가 없으면(새로고침, 주소 직접 입력) 상태 화면으로
	@GetMapping("/issued")
	public String issued(Model model) {
		if (!model.containsAttribute("issued")) {
			return HOME;
		}
		return "security/otp-issued";
	}

	@PostMapping("/activate")
	public String activate(@AuthenticationPrincipal LoginMember me,
			@RequestParam("firstCode") String firstCode, @RequestParam("secondCode") String secondCode,
			RedirectAttributes redirectAttributes) {
		try {
			otpService.activate(me.getMemberId(), firstCode.trim(), secondCode.trim());
			redirectAttributes.addFlashAttribute("successMessage", "OTP 사용 등록이 완료되었습니다. 이제 이체할 때 OTP 번호를 입력합니다.");
		} catch (BusinessException | IllegalStateException e) {
			redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
		}
		return HOME;
	}

	// 번호 확인 : 이체 화면에 OTP가 붙기 전까지 기기·앱이 맞게 등록됐는지 확인하는 용도
	// 실제 검증(otpService.verify)과 같으므로 틀리면 오류 횟수가 올라간다
	@PostMapping("/verify")
	public String verify(@AuthenticationPrincipal LoginMember me, @RequestParam("code") String code,
			RedirectAttributes redirectAttributes) {
		try {
			otpService.verify(me.getMemberId(), code.trim());
			redirectAttributes.addFlashAttribute("successMessage", "OTP 번호가 맞습니다.");
		} catch (BusinessException | IllegalStateException e) {
			redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
		}
		return HOME;
	}

	// 분실 신고 : 로그인 비밀번호를 다시 확인하고 LOST로 바꾼다 → 상태 화면에 "OTP 발급" 버튼이 다시 나온다
	@PostMapping("/lost")
	public String reportLost(@AuthenticationPrincipal LoginMember me, @RequestParam("password") String password,
			RedirectAttributes redirectAttributes) {
		try {
			otpService.reportLost(me.getMemberId(), password);
			redirectAttributes.addFlashAttribute("successMessage", "분실 신고가 완료되었습니다. 이전 OTP는 더 이상 사용할 수 없습니다. 새 OTP를 발급받아 주세요.");
		} catch (BusinessException | IllegalStateException e) {
			redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
		}
		return HOME;
	}
}
