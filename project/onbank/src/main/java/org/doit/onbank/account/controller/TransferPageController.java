package org.doit.onbank.account.controller;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import org.doit.onbank.account.dto.RecipientInfo;
import org.doit.onbank.account.dto.TransferForm;
import org.doit.onbank.account.dto.TransferLimitInfo;
import org.doit.onbank.account.dto.TransferReceipt;
import org.doit.onbank.account.entity.Account;
import org.doit.onbank.account.service.AccountService;
import org.doit.onbank.account.service.AccountTransferService;
import org.doit.onbank.member.security.LoginMember;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

// 이체 화면 : 정보입력 → 확인(받는 분 이름) → 결과 영수증
// /mybank/** 는 SecurityConfig 에서 로그인한 고객(ROLE_USER)만 접근 가능
@Controller
@RequiredArgsConstructor
@RequestMapping("/mybank/transfer")
public class TransferPageController {

	private final AccountService accountService;
	private final AccountTransferService accountTransferService;

	// ---------- 1. 정보입력 ----------

	@GetMapping
	public String form(@AuthenticationPrincipal LoginMember me,
			@RequestParam(name = "from", required = false) Long fromAccountId, Model model) {
		TransferForm form = new TransferForm();
		form.setFromAccountId(fromAccountId);	// 계좌 화면에서 "이체"를 눌러 들어오면 그 계좌를 미리 선택
		model.addAttribute("form", form);
		return showForm(me, model);
	}

	// ---------- 2. 이체 실행 ----------

	@PostMapping
	public String transfer(@AuthenticationPrincipal LoginMember me,
			@Valid @ModelAttribute("form") TransferForm form, BindingResult bindingResult,
			Model model, RedirectAttributes redirectAttributes) {
		if (bindingResult.hasErrors()) {
			form.setPassword(null);
			return showForm(me, model);
		}

		try {
			TransferReceipt receipt = accountTransferService.transfer(me.getMemberId(), form.getFromAccountId(),
					form.getPassword(), form.getToAccountNumber(), form.getAmount(),
					form.getSenderMemo(), form.getReceiverMemo());
			redirectAttributes.addFlashAttribute("receipt", receipt);
			return "redirect:/mybank/transfer/result";	// PRG : 새로고침해도 다시 이체되지 않음
		} catch (IllegalArgumentException | IllegalStateException | NoSuchElementException e) {
			model.addAttribute("errorMessage", e.getMessage());
			form.setPassword(null);	// 비밀번호는 다시 입력받는다
			return showForm(me, model);
		}
	}

	// ---------- 3. 결과 ----------

	@GetMapping("/result")
	public String result(Model model) {
		if (!model.containsAttribute("receipt")) {	// 새로고침 등으로 영수증이 없으면 입력 화면으로
			return "redirect:/mybank/transfer";
		}
		return "account/transfer-result";
	}

	// ---------- 화면에서 fetch 로 부르는 조회 (JSON) ----------

	// 입금계좌 확인 : 받는 분 이름(가림)
	@GetMapping("/recipient")
	@ResponseBody
	public ResponseEntity<Map<String, Object>> recipient(@RequestParam("accountNumber") String accountNumber) {
		try {
			RecipientInfo info = accountTransferService.findRecipient(accountNumber);
			return ResponseEntity.ok(Map.of("accountNumber", info.accountNumber(), "name", info.maskedName()));
		} catch (IllegalArgumentException e) {
			return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
		}
	}

	// 이체한도 조회 : 선택한 출금계좌 기준
	@GetMapping("/limit")
	@ResponseBody
	public ResponseEntity<Map<String, Object>> limit(@AuthenticationPrincipal LoginMember me,
			@RequestParam(name = "accountId", required = false) Long accountId) {
		try {
			TransferLimitInfo info = accountTransferService.getLimit(me.getMemberId(), accountId);
			return ResponseEntity.ok(Map.of(
					"dailyLimit", info.dailyLimit(),
					"onceLimit", info.onceLimit(),
					"todayUsed", info.todayUsed(),
					"remaining", info.remaining()));
		} catch (NoSuchElementException e) {
			return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
		}
	}

	// 입력 화면 그리기 : 출금 가능한 내 계좌만 선택 목록에 넣는다
	private String showForm(LoginMember me, Model model) {
		List<Account> accounts = accountService.getMyAccounts(me.getMemberId()).stream()
				.filter(Account::canWithdraw)
				.toList();
		model.addAttribute("accounts", accounts);
		model.addAttribute("today", LocalDate.now());
		return "account/transfer";
	}
}
