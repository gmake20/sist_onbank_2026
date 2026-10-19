package org.doit.onbank.account.controller;

import java.time.YearMonth;
import java.time.format.DateTimeParseException;

import org.doit.onbank.account.service.AssetReportService;
import org.doit.onbank.member.security.LoginMember;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import lombok.RequiredArgsConstructor;

// 자산관리 리포트 화면
// 헤더 메뉴 "자산관리"는 /asset 을 가리키므로 /mybank/assets 로 보낸다 (공용 헤더는 고치지 않음, /mybank/** 는 로그인 필요)
@Controller
@RequiredArgsConstructor
public class AssetReportPageController {

	private final AssetReportService assetReportService;

	@GetMapping("/asset")
	public String headerLink() {
		return "redirect:/mybank/assets";
	}

	// month : 'yyyy-MM' (없거나 형식이 틀리거나 선택 범위 밖이면 이번 달)
	@GetMapping("/mybank/assets")
	public String report(@AuthenticationPrincipal LoginMember me,
			@RequestParam(name = "month", required = false) String month, Model model) {
		model.addAttribute("report", assetReportService.getReport(me.getMemberId(), parse(month)));
		return "account/assets";
	}

	private YearMonth parse(String month) {
		if (month == null || month.isBlank()) {
			return null;
		}
		try {
			return YearMonth.parse(month.trim());
		} catch (DateTimeParseException e) {
			return null;
		}
	}
}
