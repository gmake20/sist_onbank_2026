package org.doit.onbank.savings.controller;

import lombok.RequiredArgsConstructor;
import org.doit.onbank.member.security.LoginMember;
import org.doit.onbank.savings.service.SavingsService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
public class SavingsManualPaymentController {
	private final SavingsService savingsService;

	@PostMapping("/savings/{id}/manual-pay")
	public String manualPay(@PathVariable Long id,
			@AuthenticationPrincipal LoginMember member,
			RedirectAttributes attributes) {
		
		if (member == null) return "redirect:/login";
		
		try {
			// SavingsService.pay()가 소유권, 회차 순서, 납입 가능일과 잔액을 검증합니다.
			savingsService.pay(id, member.getMemberId());
			attributes.addFlashAttribute("message", "적금 수동 납입이 완료되었습니다.");
			
		} catch (IllegalArgumentException | IllegalStateException | ArithmeticException ex) {
			attributes.addFlashAttribute("error", ex.getMessage());
		}
		
		attributes.addAttribute("accountId", id);
		
		return "redirect:/savings/mine";
	}
}
