package org.doit.onbank.savings.controller;

import lombok.RequiredArgsConstructor;
import org.doit.onbank.member.security.LoginMember;
import org.doit.onbank.savings.service.SavingsAutoTransferService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
@RequestMapping("/savings")
public class SavingsAutoTransferController {
    private final SavingsAutoTransferService auto;

    @PostMapping("/{id}/auto-transfer")
    public String save(@PathVariable Long id, @RequestParam int transferDay,
                       @AuthenticationPrincipal LoginMember member, RedirectAttributes flash) {
        if (member == null) return "redirect:/login";
        try {
            auto.configure(id, member.getMemberId(), transferDay);
            flash.addFlashAttribute("message", "정기이체 설정이 저장되었습니다.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            flash.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/savings/" + id;
    }

    @PostMapping("/{id}/auto-transfer/cancel")
    public String cancel(@PathVariable Long id, @AuthenticationPrincipal LoginMember member,
                         RedirectAttributes flash) {
        if (member == null) return "redirect:/login";
        try {
            auto.cancel(id, member.getMemberId());
            flash.addFlashAttribute("message", "정기이체가 해지되었습니다.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            flash.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/savings/" + id;
    }
}
