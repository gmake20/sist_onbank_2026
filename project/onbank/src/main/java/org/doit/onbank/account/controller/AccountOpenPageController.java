package org.doit.onbank.account.controller;

import org.doit.onbank.account.dto.OpenAccountForm;
import org.doit.onbank.account.dto.OpenAccountReceipt;
import org.doit.onbank.account.entity.Account;
import org.doit.onbank.account.service.AccountOpenService;
import org.doit.onbank.member.security.LoginMember;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

// 계좌 개설 화면 : 입출금 계좌(ON 통장) 개설 → 결과
// /mybank/** 는 SecurityConfig 에서 로그인한 고객(ROLE_USER)만 접근 가능
@Controller
@RequiredArgsConstructor
@RequestMapping("/mybank/accounts/open")
public class AccountOpenPageController {

	static final String CHECKING_PRODUCT_NAME = "ON 통장";

	private final AccountOpenService accountOpenService;

	@GetMapping
	public String openForm(Model model) {
		model.addAttribute("form", new OpenAccountForm());
		model.addAttribute("productName", CHECKING_PRODUCT_NAME);
		return "account/open";
	}

	@PostMapping
	public String open(@AuthenticationPrincipal LoginMember me,
			@Valid @ModelAttribute("form") OpenAccountForm form, BindingResult bindingResult,
			Model model, RedirectAttributes redirectAttributes) {
		model.addAttribute("productName", CHECKING_PRODUCT_NAME);
		if (bindingResult.hasErrors()) {
			clearPasswords(form);
			return "account/open";
		}

		try {
			Account account = accountOpenService.openChecking(me.getMemberId(), form.getPassword());
			// PRG : 새로고침해도 계좌가 또 만들어지지 않도록 결과 화면으로 리다이렉트
			redirectAttributes.addFlashAttribute("receipt", OpenAccountReceipt.from(account, CHECKING_PRODUCT_NAME));
			return "redirect:/mybank/accounts/open/result";
		} catch (IllegalArgumentException | IllegalStateException e) {
			model.addAttribute("errorMessage", e.getMessage());
			clearPasswords(form);
			return "account/open";
		}
	}

	// 개설 결과 : 리다이렉트 직후 한 번만 보인다. 결과가 없으면(새로고침, 주소 직접 입력) 개설 화면으로
	@GetMapping("/result")
	public String result(Model model) {
		if (!model.containsAttribute("receipt")) {
			return "redirect:/mybank/accounts/open";
		}
		return "account/open-result";
	}

	// 비밀번호는 다시 입력받는다
	private void clearPasswords(OpenAccountForm form) {
		form.setPassword(null);
		form.setPasswordConfirm(null);
	}
}
