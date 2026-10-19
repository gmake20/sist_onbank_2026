package org.doit.onbank.member.controller;

import org.doit.onbank.member.security.LoginMember;
import org.doit.onbank.member.service.MemberService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import lombok.RequiredArgsConstructor;

// 마이페이지 (화면 29번) : 회원정보 조회
// 와이어프레임은 /mypage 지만, 로그인 고객 전용 영역(/mybank/**, SecurityConfig)에 맞춰 /mybank/profile 로 둔다
// TODO 회원정보 수정, 비밀번호 변경, 회원 탈퇴
@Controller
@RequiredArgsConstructor
@RequestMapping("/mybank/profile")
public class MyPageController {

	private final MemberService memberService;

	// 세션의 LoginMember는 로그인 시점 값이라 DB에서 다시 읽어 최신 정보를 보여 준다
	@GetMapping
	public String profile(@AuthenticationPrincipal LoginMember me, Model model) {
		model.addAttribute("info", memberService.getMyInfo(me.getMemberId()));
		return "mypage/profile";
	}
}
