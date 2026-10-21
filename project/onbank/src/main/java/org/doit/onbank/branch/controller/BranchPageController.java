package org.doit.onbank.branch.controller;

import org.doit.onbank.branch.entity.BranchServiceType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

// 지점/ATM 찾기 화면 (비회원 공개). 목록·지도 데이터는 화면이 /api/branches 로 받아 온다
// 카카오맵 JavaScript 키가 없으면 지도 없이 목록만 보여 준다
@Controller
public class BranchPageController {

	private final String kakaoJsKey;

	public BranchPageController(@Value("${onbank.kakao.js-key:}") String kakaoJsKey) {
		this.kakaoJsKey = kakaoJsKey;
	}

	@GetMapping("/branches")
	public String find(Model model) {
		model.addAttribute("kakaoJsKey", kakaoJsKey);
		model.addAttribute("serviceTypes", BranchServiceType.values());
		return "branch/find";
	}
}
