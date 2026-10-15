package org.doit.onbank.employee.controller;

import org.doit.onbank.employee.security.EmployeePrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

// 직원 영역의 로그인 화면과 홈. POST /employee/login(로그인 처리)은 Spring Security가 담당
@Controller
@RequestMapping("/employee")
public class EmployeeHomeController {

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
}
