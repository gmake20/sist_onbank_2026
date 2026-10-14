package org.doit.onbank.member.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

// 로그인 화면만 보여준다. POST /login(로그인 처리)은 Spring Security가 담당
@Controller
public class LoginController {

	@GetMapping("/login")
	public String loginForm() {
		return "auth/login";
	}
}
