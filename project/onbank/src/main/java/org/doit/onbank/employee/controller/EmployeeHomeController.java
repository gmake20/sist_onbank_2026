package org.doit.onbank.employee.controller;

import org.doit.onbank.employee.security.EmployeePrincipal;
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

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

// 직원 영역의 로그인 화면, 홈, 비밀번호 변경. POST /employee/login(로그인 처리)은 Spring Security가 담당
@Controller
@RequestMapping("/employee")
@RequiredArgsConstructor
public class EmployeeHomeController {

	private final BackOfficePasswordService backOfficePasswordService;

	@GetMapping("/login")
	public String loginForm(Model model) {
		model.addAttribute("area", "직원");
		model.addAttribute("loginUrl", "/employee/login");
		model.addAttribute("idParam", "employeeNo");
		model.addAttribute("idLabel", "사번");
		return "backoffice/login";
	}

	// 직원 홈 (대시보드는 팀원4가 이 자리에 만든다)
	@GetMapping
	public String home(@AuthenticationPrincipal EmployeePrincipal me, Model model) {
		model.addAttribute("area", "직원");
		model.addAttribute("me", me);
		model.addAttribute("roleName", me.getRole().name());
		model.addAttribute("logoutUrl", "/employee/logout");
		return "backoffice/home";
	}

	// 비밀번호 변경 화면 (직원·관리자 공용 화면). 임시 비밀번호면 로그인 직후 이 화면으로 온다
	@GetMapping("/password")
	public String passwordForm(@AuthenticationPrincipal EmployeePrincipal me, Model model) {
		model.addAttribute("form", new PasswordChangeForm());
		addPasswordPageAttributes(me, model);
		return "backoffice/password";
	}

	// 변경에 성공하면 로그아웃 → 새 비밀번호로 다시 로그인 (세션의 로그인 정보에 임시 비밀번호 표시가 남아 있기 때문)
	@PostMapping("/password")
	public String changePassword(@AuthenticationPrincipal EmployeePrincipal me,
			@Valid @ModelAttribute("form") PasswordChangeForm form, BindingResult bindingResult,
			HttpServletRequest request, HttpServletResponse response, Model model) {
		if (!bindingResult.hasFieldErrors("newPassword") && form.getNewPasswordConfirm() != null
				&& !form.getNewPasswordConfirm().equals(form.getNewPassword())) {
			bindingResult.rejectValue("newPasswordConfirm", "mismatch", "새 비밀번호가 일치하지 않습니다.");
		}
		if (!bindingResult.hasErrors()) {
			try {
				backOfficePasswordService.changeEmployeePassword(me.getEmployeeId(), form);
				new SecurityContextLogoutHandler().logout(request, response,
						SecurityContextHolder.getContext().getAuthentication());
				return "redirect:/employee/login?changed";
			} catch (BusinessException e) {
				bindingResult.reject("invalid", e.getMessage());
			}
		}
		addPasswordPageAttributes(me, model);
		return "backoffice/password";
	}

	private void addPasswordPageAttributes(EmployeePrincipal me, Model model) {
		model.addAttribute("area", "직원");
		model.addAttribute("passwordUrl", "/employee/password");
		model.addAttribute("homeUrl", "/employee");
		model.addAttribute("logoutUrl", "/employee/logout");
		model.addAttribute("tempPassword", me.isTempPassword());
	}
}
