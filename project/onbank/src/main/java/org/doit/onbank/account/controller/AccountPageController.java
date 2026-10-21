package org.doit.onbank.account.controller;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
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
import org.doit.onbank.account.repository.AssetReportRepository;
import org.doit.onbank.account.repository.LoanSummary;
import org.doit.onbank.account.repository.SavingsLastPaid;
import org.doit.onbank.account.service.AccountFavoriteService;
import org.doit.onbank.account.service.AccountService;
import org.doit.onbank.member.security.LoginMember;
import org.doit.onbank.savings.entity.SavingsAccount;
import org.doit.onbank.savings.entity.SavingsStatus;
import org.doit.onbank.savings.entity.SavingsType;
import org.doit.onbank.savings.repository.SavingsAccountRepository;
import org.springframework.dao.DataAccessException;
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
	private final AccountFavoriteService accountFavoriteService;
	private final SavingsAccountRepository savingsAccountRepository;	// 팀3 예적금 (조회만)
	private final AssetReportRepository assetReportRepository;		// 팀3 대출 조회 (자산 리포트와 같은 SQL)

	// 거래 유형의 한글 이름 (DB에는 영문 값 저장). 계좌 상태 이름은 AccountStatus.label 을 쓴다
	@ModelAttribute("typeNames")
	public Map<String, String> typeNames() {
		return Map.of(
				TransactionType.DEPOSIT, "입금",
				TransactionType.WITHDRAWAL, "출금",
				TransactionType.TRANSFER_OUT, "이체출금",
				TransactionType.TRANSFER_IN, "이체입금");
	}

	// ---------- 계좌 목록 ----------

	// 전계좌조회 : 입출금 / 예금 / 적금 / 대출 영역으로 나눠서 보여 준다
	// 입출금은 팀2 account, 예금·적금은 팀3 savings_account, 대출은 팀3 loan_account 에서 읽는다 (10-21)
	@GetMapping
	public String list(@AuthenticationPrincipal LoginMember me, Model model) {
		List<Account> accounts = accountService.getMyAccounts(me.getMemberId());
		// account_type 이 비어 있는 계좌(테스트 계좌 등)는 입출금으로 본다
		List<Account> checking = accounts.stream()
				.filter(account -> account.getAccountType() == null
						|| account.getAccountType() == AccountType.CHECKING)
				.toList();

		// 팀3 예적금 (savings_account) : 진행 중인 것만, 예금·적금으로 나눈다
		List<SavingsAccount> mySavings = savingsAccountRepository.findByMemberIdOrderByIdDesc(me.getMemberId());
		List<SavingsAccount> depositProducts = new ArrayList<>();	// 예금
		List<SavingsAccount> savingsProducts = new ArrayList<>();	// 적금
		long depositSum = 0;
		long savingsSum = 0;

		for (SavingsAccount item : mySavings) {
			if (item.getStatus() != SavingsStatus.ACTIVE) {		// 진행 중이 아니면 (만기·중도 해지)
				continue;										// 건너뛰고 다음 것으로
			}
			if (item.getProduct().getType() == SavingsType.FIXED_DEPOSIT) {	// 예금이면
				depositProducts.add(item);
				depositSum += item.getPrincipal();				// 원금을 더한다
			} else {											// 아니면 적금
				savingsProducts.add(item);
				savingsSum += item.getPrincipal();				// 지금까지 넣은 금액을 더한다
			}
		}

		// 예적금 표시용 : 연결계좌 번호 (가입·납입 때 출금되고 만기에 입금되는 내 입출금 계좌), 최종거래일
		Map<Long, String> accountNumbers = new HashMap<>();
		for (Account account : accounts) {
			accountNumbers.put(account.getAccountId(), account.getAccountNumber());
		}
		Map<Long, String> lastPaidOn = new HashMap<>();
		try {
			for (SavingsLastPaid row : assetReportRepository.findSavingsLastPaidOn(me.getMemberId())) {
				lastPaidOn.put(row.getSavingsAccountId(), row.getLastPaidOn());
			}
		} catch (DataAccessException e) {
			// 납입 기록을 못 읽으면 최종거래일만 가입일로 보여 준다
		}

		// 팀3 대출 (loan_account) : 완납(CLOSED)을 뺀 대출. 팀3 테이블이 바뀌어 조회가 실패해도 화면은 연다
		List<LoanSummary> loans;
		try {
			loans = assetReportRepository.findActiveLoans(me.getMemberId());
		} catch (DataAccessException e) {
			loans = List.of();
			model.addAttribute("loanError", "대출 정보를 불러오지 못했습니다.");
		}
		long loanSum = loans.stream()
				.mapToLong(loan -> loan.getRemainingBalance() == null ? 0L : loan.getRemainingBalance())
				.sum();

		model.addAttribute("checkingAccounts", checking);
		model.addAttribute("depositProducts", depositProducts);	// 예금 목록
		model.addAttribute("savingsProducts", savingsProducts);	// 적금 목록
		model.addAttribute("loans", loans);						// 대출 목록
		model.addAttribute("accountNumbers", accountNumbers);	// 계좌 ID → 계좌번호 (예적금 연결계좌)
		model.addAttribute("lastPaidOn", lastPaidOn);			// 예적금 ID → 최종거래일
		model.addAttribute("checkingTotal", totalBalance(checking));
		model.addAttribute("depositTotal", depositSum);
		model.addAttribute("savingsTotal", savingsSum);
		model.addAttribute("loanTotal", loanSum);				// 대출 잔액 합계
		model.addAttribute("now", LocalDateTime.now());
		// 예금명 옆 ☆/★ : 계좌번호 → 즐겨찾기 ID
		model.addAttribute("favoriteIds", accountFavoriteService.getMyFavoriteIdsByNumber(me.getMemberId()));
		return "account/list";
	}

	// 해지 계좌는 합계에서 제외
	private long totalBalance(List<Account> accounts) {
		return accounts.stream()
				.filter(account -> account.getAccountStatus() != AccountStatus.CLOSED)
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
