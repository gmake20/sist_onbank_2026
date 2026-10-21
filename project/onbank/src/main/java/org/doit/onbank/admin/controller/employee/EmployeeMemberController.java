package org.doit.onbank.admin.controller.employee;

import lombok.RequiredArgsConstructor;
import org.doit.onbank.admin.service.AdminMemberService;
import org.doit.onbank.member.entity.MemberStatus;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

// 직원 회원 조회. 관리자 회원 목록(AdminMemberController)과 같은 검색, 잠금 해제·이용 제한은 없음 (조회 전용)
@Controller
@RequestMapping("/employee/members")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('STAFF','MANAGER')")
public class EmployeeMemberController {

    private final AdminMemberService adminMemberService;

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

        return "backoffice/employee/member/members";
    }
}
