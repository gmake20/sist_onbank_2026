package org.doit.onbank.admin.controller;

import org.doit.onbank.admin.security.AdminPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

// 관리자 영역의 로그인 화면과 홈. POST /admin/login(로그인 처리)은 Spring Security가 담당
@Controller
@RequestMapping("/admin")
public class AdminHomeController {

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
	public String home(@AuthenticationPrincipal AdminPrincipal me, Model model) {
		model.addAttribute("area", "관리자");
		model.addAttribute("me", me);
		model.addAttribute("roleName", me.getRole().name());
		model.addAttribute("logoutUrl", "/admin/logout");
		return "backoffice/home";
	}
}
