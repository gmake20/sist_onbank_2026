package org.doit.onbank.account.controller;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

import org.doit.onbank.account.entity.Account;
import org.doit.onbank.account.entity.ScheduledTransfer;
import org.doit.onbank.account.entity.ScheduledTransferStatus;
import org.doit.onbank.account.service.AccountService;
import org.doit.onbank.account.service.ScheduledTransferRunner;
import org.doit.onbank.account.service.ScheduledTransferService;
import org.doit.onbank.member.security.LoginMember;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import lombok.RequiredArgsConstructor;

// 예약이체 조회·취소·[지금 실행] 화면 (등록은 이체 화면 TransferPageController 에서 한다)
// /mybank/** 는 SecurityConfig 에서 로그인한 고객(ROLE_USER)만 접근 가능
@Controller
@RequiredArgsConstructor
@RequestMapping("/mybank/transfer/scheduled")
public class ScheduledTransferPageController {

	private final ScheduledTransferService scheduledTransferService;
	private final ScheduledTransferRunner scheduledTransferRunner;
	private final AccountService accountService;

	// 목록 : 상태와 관계없이 내가 등록한 예약 전부 (최근 등록 순)
	@GetMapping
	public String list(@AuthenticationPrincipal LoginMember me, Model model) {
		model.addAttribute("transfers", scheduledTransferService.getMine(me.getMemberId()));
		// 출금계좌는 ID로 저장돼 있으므로 화면에 보여 줄 계좌번호로 바꾼다
		Map<Long, String> accountNumbers = accountService.getMyAccounts(me.getMemberId()).stream()
				.collect(Collectors.toMap(Account::getAccountId, Account::getAccountNumber));
		model.addAttribute("accountNumbers", accountNumbers);
		model.addAttribute("now", LocalDateTime.now());
		return "account/scheduled-transfers";
	}

	// 취소 : 처리 결과는 PRG(리다이렉트 + flash)로 목록에 보여 준다
	@PostMapping("/{id}/cancel")
	public String cancel(@AuthenticationPrincipal LoginMember me, @PathVariable("id") Long id,
			RedirectAttributes redirectAttributes) {
		try {
			scheduledTransferService.cancel(me.getMemberId(), id);
			redirectAttributes.addFlashAttribute("successMessage", "예약이체를 취소했습니다.");
		} catch (NoSuchElementException | IllegalStateException e) {
			redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
		}
		return "redirect:/mybank/transfer/scheduled";
	}

	// [지금 실행] (시연용) : 예약 일시를 기다리지 않고 바로 실행. 실행 결과(성공·실패 사유)를 목록 위에 보여 준다
	@PostMapping("/{id}/run")
	public String runNow(@AuthenticationPrincipal LoginMember me, @PathVariable("id") Long id,
			RedirectAttributes redirectAttributes) {
		try {
			ScheduledTransfer result = scheduledTransferRunner.runNow(me.getMemberId(), id);
			if (result.getStatus() == ScheduledTransferStatus.COMPLETED) {
				redirectAttributes.addFlashAttribute("successMessage",
						String.format("예약이체를 실행했습니다. %,d원을 이체했습니다.", result.getTransferAmount()));
			} else {
				redirectAttributes.addFlashAttribute("errorMessage", "예약이체를 실행하지 못했습니다. " + result.getLastResult());
			}
		} catch (NoSuchElementException | IllegalStateException e) {
			redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
		}
		return "redirect:/mybank/transfer/scheduled";
	}
}
