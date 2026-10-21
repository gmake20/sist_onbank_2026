package org.doit.onbank.savings.controller;

import org.doit.onbank.member.security.LoginMember;
import org.doit.onbank.savings.service.SavingsEarlyCloseService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
@RequestMapping("/savings")
public class SavingsEarlyCloseController {
	
    private final SavingsEarlyCloseService service;

    @GetMapping("/{id}/early-close")
    public String preview(@PathVariable Long id,
            @AuthenticationPrincipal LoginMember member, Model model) {
        if (member == null) return "redirect:/login";
        model.addAttribute("accountId", id);
        model.addAttribute("quote", service.preview(id, member.getMemberId()));
        return "savings/early-close";
    }

    @PostMapping("/{id}/early-close")
    public String execute(@PathVariable Long id,
            @AuthenticationPrincipal LoginMember member,
            RedirectAttributes redirect) {
        if (member == null) return "redirect:/login";
        var quote = service.execute(id, member.getMemberId());
        redirect.addFlashAttribute("message", "중도해지 완료: " + quote.payout() + "원 지급");
        return "redirect:/savings/mine";
    }
}
