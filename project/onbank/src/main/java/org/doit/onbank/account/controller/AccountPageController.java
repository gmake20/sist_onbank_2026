package org.doit.onbank.account.controller;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import org.doit.onbank.account.dto.DepositForm;
import org.doit.onbank.account.dto.TransactionReceipt;
import org.doit.onbank.account.dto.WithdrawForm;
import org.doit.onbank.account.entity.Account;
import org.doit.onbank.account.entity.AccountStatus;
import org.doit.onbank.account.entity.AccountType;
import org.doit.onbank.account.entity.TransactionHistory;
import org.doit.onbank.account.entity.TransactionType;
import org.doit.onbank.account.service.AccountService;
import org.doit.onbank.member.security.LoginMember;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

// 내 계좌 화면 : 계좌 목록, 거래내역, 입금, 출금 (ATM mock)
// /mybank/** 는 SecurityConfig 에서 로그인한 고객(ROLE_USER)만 접근 가능
@Controller
@RequiredArgsConstructor
@RequestMapping("/mybank/accounts")
public class AccountPageController {

	private final AccountService accountService;

	// 화면에 보여 줄 한글 이름 (DB에는 영문 값 저장)
	@ModelAttribute("statusNames")
	public Map<String, String> statusNames() {
		return Map.of(
				AccountStatus.ACTIVE, "정상",
				AccountStatus.PENDING, "개설대기",
				AccountStatus.FROZEN, "거래정지",
				AccountStatus.CLOSED, "해지");
	}

	@ModelAttribute("typeNames")
	public Map<String, String> typeNames() {
		return Map.of(
				TransactionType.DEPOSIT, "입금",
				TransactionType.WITHDRAWAL, "출금",
				TransactionType.TRANSFER_OUT, "이체출금",
				TransactionType.TRANSFER_IN, "이체입금");
	}

	// ---------- 계좌 목록 ----------

	// 전계좌조회 : 입출금 / 예금 / 적금 영역으로 나눠서 보여 준다
	@GetMapping
	public String list(@AuthenticationPrincipal LoginMember me, Model model) {
		List<Account> accounts = accountService.getMyAccounts(me.getMemberId());
		// account_type 이 비어 있는 계좌(테스트 계좌 등)는 입출금으로 본다
		List<Account> checking = accounts.stream()
				.filter(account -> account.getAccountType() == null
						|| AccountType.CHECKING.equals(account.getAccountType()))
				.toList();
		List<Account> deposits = ofType(accounts, AccountType.DEPOSIT);
		List<Account> savings = ofType(accounts, AccountType.SAVINGS);

		model.addAttribute("checkingAccounts", checking);
		model.addAttribute("depositAccounts", deposits);
		model.addAttribute("savingsAccounts", savings);
		model.addAttribute("checkingTotal", totalBalance(checking));
		model.addAttribute("depositTotal", totalBalance(deposits));
		model.addAttribute("savingsTotal", totalBalance(savings));
		model.addAttribute("now", LocalDateTime.now());
		return "account/list";
	}

	private List<Account> ofType(List<Account> accounts, String accountType) {
		return accounts.stream().filter(account -> accountType.equals(account.getAccountType())).toList();
	}

	// 해지 계좌는 합계에서 제외
	private long totalBalance(List<Account> accounts) {
		return accounts.stream()
				.filter(account -> !AccountStatus.CLOSED.equals(account.getAccountStatus()))
				.mapToLong(account -> account.getBalance() == null ? 0L : account.getBalance())
				.sum();
	}

	// ---------- 거래내역 ----------

	@GetMapping("/{accountId}")
	public String detail(@AuthenticationPrincipal LoginMember me, @PathVariable("accountId") Long accountId,
			Model model) {
		model.addAttribute("account", accountService.getMyAccount(me.getMemberId(), accountId));
		model.addAttribute("histories", accountService.getMyHistories(me.getMemberId(), accountId));
		return "account/detail";
	}

	// ---------- 입금 ----------

	@GetMapping("/{accountId}/deposit")
	public String depositForm(@AuthenticationPrincipal LoginMember me, @PathVariable("accountId") Long accountId,
			Model model) {
		model.addAttribute("account", accountService.getMyAccount(me.getMemberId(), accountId));
		model.addAttribute("form", new DepositForm());
		return "account/deposit";
	}

	@PostMapping("/{accountId}/deposit")
	public String deposit(@AuthenticationPrincipal LoginMember me, @PathVariable("accountId") Long accountId,
			@Valid @ModelAttribute("form") DepositForm form, BindingResult bindingResult,
			Model model, RedirectAttributes redirectAttributes) {
		// 본인 계좌인지 먼저 확인 (Service 의 deposit 은 남이 넣어 주는 입금도 받으므로 본인 확인을 하지 않음)
		Account account = accountService.getMyAccount(me.getMemberId(), accountId);
		if (bindingResult.hasErrors()) {
			model.addAttribute("account", account);
			return "account/deposit";
		}

		try {
			TransactionHistory history = accountService.deposit(accountId, form.getAmount(), form.getContent());
			return redirectWithReceipt(accountId, history, redirectAttributes);
		} catch (IllegalArgumentException | IllegalStateException e) {
			model.addAttribute("account", account);
			model.addAttribute("errorMessage", e.getMessage());
			return "account/deposit";
		}
	}

	// ---------- 출금 ----------

	@GetMapping("/{accountId}/withdraw")
	public String withdrawForm(@AuthenticationPrincipal LoginMember me, @PathVariable("accountId") Long accountId,
			Model model) {
		model.addAttribute("account", accountService.getMyAccount(me.getMemberId(), accountId));
		model.addAttribute("form", new WithdrawForm());
		return "account/withdraw";
	}

	@PostMapping("/{accountId}/withdraw")
	public String withdraw(@AuthenticationPrincipal LoginMember me, @PathVariable("accountId") Long accountId,
			@Valid @ModelAttribute("form") WithdrawForm form, BindingResult bindingResult,
			Model model, RedirectAttributes redirectAttributes) {
		if (bindingResult.hasErrors()) {
			model.addAttribute("account", accountService.getMyAccount(me.getMemberId(), accountId));
			return "account/withdraw";
		}

		try {
			TransactionHistory history = accountService.withdraw(me.getMemberId(), accountId,
					form.getAmount(), form.getPassword(), form.getContent());
			return redirectWithReceipt(accountId, history, redirectAttributes);
		} catch (IllegalArgumentException | IllegalStateException e) {
			// 계좌를 다시 읽어서 비밀번호 오류 횟수 등 바뀐 값을 보여 준다
			model.addAttribute("account", accountService.getMyAccount(me.getMemberId(), accountId));
			model.addAttribute("errorMessage", e.getMessage());
			form.setPassword(null);	// 비밀번호는 다시 입력받는다
			return "account/withdraw";
		}
	}

	// 성공하면 거래내역 화면으로 리다이렉트 (PRG : 새로고침해도 다시 입출금되지 않음)
	private String redirectWithReceipt(Long accountId, TransactionHistory history,
			RedirectAttributes redirectAttributes) {
		redirectAttributes.addFlashAttribute("receipt", TransactionReceipt.from(history));
		return "redirect:/mybank/accounts/" + accountId;
	}

	// 없는 계좌, 남의 계좌 → 목록으로
	@ExceptionHandler(NoSuchElementException.class)
	public String handleNotFound(NoSuchElementException e, RedirectAttributes redirectAttributes) {
		redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
		return "redirect:/mybank/accounts";
	}
}
