package org.doit.onbank.admin.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.doit.onbank.admin.security.AdminPrincipal;
import org.doit.onbank.admin.service.AdminMemberService;
import org.doit.onbank.common.exception.BusinessException;
import org.doit.onbank.member.entity.MemberStatus;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;

// S-05 회원 목록 (MEM-001). 역할은 사이드바 메뉴와 같게
@Controller
@RequestMapping("/admin/members")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN','OPERATOR')")
public class AdminMemberController {

    private final AdminMemberService adminMemberService;

    // 기간을 비우면 조건 없음 (대출 목록과 달리 기본 30일을 두지 않음)
    // select에서 "전체"(빈 값)를 고르면 enum은 null로 들어온다
    @GetMapping
    public String list(@RequestParam(defaultValue = "loginId") String type,
                       @RequestParam(required = false) String keyword,
                       @RequestParam(required = false) MemberStatus status,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                       @RequestParam(defaultValue = "0") int page,
                       Model model) {
        if (from != null && to != null && from.isAfter(to)) {
            model.addAttribute("periodError", "가입일 시작일이 종료일보다 늦습니다.");
            model.addAttribute("members", Page.empty());
        } else {
            model.addAttribute("members", adminMemberService.search(type, keyword, status, from, to, page));
        }
        model.addAttribute("type", type);
        model.addAttribute("keyword", keyword);
        model.addAttribute("status", status);
        model.addAttribute("from", from);
        model.addAttribute("to", to);
        model.addAttribute("statuses", MemberStatus.values());
        model.addAttribute("statusLabels", AdminMemberService.STATUS_LABELS);

        return "backoffice/admin/member/members";
    }

    // 로그인 잠금 해제 (MEM-004) / 이용 제한·해제 (MEM-003). 결과는 목록 위 알림(message / error)으로 보여 준다
    @PostMapping("/{memberId}/unlock")
    public String unlockLogin(@AuthenticationPrincipal AdminPrincipal me, @PathVariable("memberId") Long memberId,
                              @RequestParam("reason") String reason, HttpServletRequest request,
                              RedirectAttributes rttr) {
        try {
            adminMemberService.unlockLogin(me, memberId, reason, request.getRemoteAddr());
            rttr.addFlashAttribute("message", "로그인 잠금을 해제했습니다.");
        } catch (BusinessException e) {
            rttr.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/members";
    }

    @PostMapping("/{memberId}/restrict")
    public String restrict(@AuthenticationPrincipal AdminPrincipal me, @PathVariable("memberId") Long memberId,
                           @RequestParam("reason") String reason, HttpServletRequest request,
                           RedirectAttributes rttr) {
        try {
            adminMemberService.restrict(me, memberId, reason, request.getRemoteAddr());
            rttr.addFlashAttribute("message", "이용 제한했습니다.");
        } catch (BusinessException e) {
            rttr.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/members";
    }

    @PostMapping("/{memberId}/unrestrict")
    public String unrestrict(@AuthenticationPrincipal AdminPrincipal me, @PathVariable("memberId") Long memberId,
                             @RequestParam("reason") String reason, HttpServletRequest request,
                             RedirectAttributes rttr) {
        try {
            adminMemberService.unrestrict(me, memberId, reason, request.getRemoteAddr());
            rttr.addFlashAttribute("message", "이용 제한을 해제했습니다.");
        } catch (BusinessException e) {
            rttr.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/members";
    }
}
