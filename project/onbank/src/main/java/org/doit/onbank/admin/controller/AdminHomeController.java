package org.doit.onbank.admin.controller;

import org.doit.onbank.admin.security.AdminPrincipal;
import org.doit.onbank.admin.service.AdminDashboardService;
import org.doit.onbank.common.exception.BusinessException;
import org.doit.onbank.common.security.BackOfficePasswordService;
import org.doit.onbank.common.security.PasswordChangeForm;
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

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

// 관리자 영역의 로그인 화면, 홈, 비밀번호 변경. POST /admin/login(로그인 처리)은 Spring Security가 담당
@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminHomeController {

	private final BackOfficePasswordService backOfficePasswordService;
	private final AdminDashboardService adminDashboardService;	// DSH-001 요약 지표, DSH-002 거래량 추이 (팀원4)

	@GetMapping("/login")
	public String loginForm(Model model) {
		model.addAttribute("area", "관리자");
		model.addAttribute("loginUrl", "/admin/login");
		model.addAttribute("idParam", "loginId");
		model.addAttribute("idLabel", "아이디");
		return "backoffice/login";
	}

	// 관리자 홈 (대시보드는 팀원4가 이 자리에 만든다)
	@GetMapping
	// public String home(@AuthenticationPrincipal AdminPrincipal me, Model model) {
	public String home(@AuthenticationPrincipal AdminPrincipal me, Model model,
			@RequestParam(defaultValue = "7") int range) {
		model.addAttribute("area", "관리자");
		model.addAttribute("me", me);
		model.addAttribute("roleName", me.getRole().name());
		model.addAttribute("logoutUrl", "/admin/logout");
		model.addAttribute("summary", adminDashboardService.summary());
		int days = AdminDashboardService.normalizeRange(range);	// 7·30 외 값은 7
		model.addAttribute("range", days);
		model.addAttribute("trend", adminDashboardService.trend(days));
		// return "backoffice/home";
		return "backoffice/admin/dashboard";
	}

	// 비밀번호 변경 화면 (직원·관리자 공용 화면). 임시 비밀번호면 로그인 직후 이 화면으로 온다
	@GetMapping("/password")
	public String passwordForm(@AuthenticationPrincipal AdminPrincipal me, Model model) {
		model.addAttribute("form", new PasswordChangeForm());
		addPasswordPageAttributes(me, model);
		return "backoffice/password";
	}

	// 변경에 성공하면 로그아웃 → 새 비밀번호로 다시 로그인 (세션의 로그인 정보에 임시 비밀번호 표시가 남아 있기 때문)
	@PostMapping("/password")
	public String changePassword(@AuthenticationPrincipal AdminPrincipal me,
			@Valid @ModelAttribute("form") PasswordChangeForm form, BindingResult bindingResult,
			HttpServletRequest request, HttpServletResponse response, Model model) {
		if (!bindingResult.hasFieldErrors("newPassword") && form.getNewPasswordConfirm() != null
				&& !form.getNewPasswordConfirm().equals(form.getNewPassword())) {
			bindingResult.rejectValue("newPasswordConfirm", "mismatch", "새 비밀번호가 일치하지 않습니다.");
		}
		if (!bindingResult.hasErrors()) {
			try {
				backOfficePasswordService.changeAdminPassword(me.getAdminId(), form);
				new SecurityContextLogoutHandler().logout(request, response,
						SecurityContextHolder.getContext().getAuthentication());
				return "redirect:/admin/login?changed";
			} catch (BusinessException e) {
				bindingResult.reject("invalid", e.getMessage());
			}
		}
		addPasswordPageAttributes(me, model);
		return "backoffice/password";
	}

	private void addPasswordPageAttributes(AdminPrincipal me, Model model) {
		model.addAttribute("area", "관리자");
		model.addAttribute("passwordUrl", "/admin/password");
		model.addAttribute("homeUrl", "/admin");
		model.addAttribute("logoutUrl", "/admin/logout");
		model.addAttribute("tempPassword", me.isTempPassword());
	}
}
