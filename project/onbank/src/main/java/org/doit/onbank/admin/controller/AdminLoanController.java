package org.doit.onbank.admin.controller;

import lombok.RequiredArgsConstructor;
import org.doit.onbank.admin.service.AdminLoanService;
import org.doit.onbank.loan.entity.LoanApplicationStatus;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.Map;

// S-12 대출 목록 (LON-001). 조회만 — ADMIN은 심사하지 않는다 (D-04)
@Controller
@RequestMapping("/admin/loans")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN','OPERATOR')")
public class AdminLoanController {

    // 기간을 안 고르면 오늘 기준 최근 30일 (활동 로그와 같음)
    private static final int DEFAULT_DAYS = 30;

    private final AdminLoanService adminLoanService;

    // select에서 "전체"(빈 값)를 고르면 enum은 null로 들어온다
    @GetMapping
    public String list(@RequestParam(name = "status", required = false) LoanApplicationStatus status,
                       @RequestParam(name = "memberLoginId", required = false) String memberLoginId,
                       @RequestParam(name = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                       @RequestParam(name = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                       @RequestParam(name = "page", defaultValue = "0") int page,
                       Model model) {
        if (to == null) {
            to = LocalDate.now();
        }
        if (from == null) {
            from = to.minusDays(DEFAULT_DAYS);
        }

        Map<LoanApplicationStatus, Long> counts = adminLoanService.countByStatus(memberLoginId, from, to);
        long approved = counts.get(LoanApplicationStatus.APPROVED);
        long rejected = counts.get(LoanApplicationStatus.REJECTED);

        model.addAttribute("loans", adminLoanService.search(status, memberLoginId, from, to, page));
        model.addAttribute("totalCount", counts.values().stream().mapToLong(Long::longValue).sum());
        model.addAttribute("approvedCount", approved);
        model.addAttribute("rejectedCount", rejected);
        // 승인율 = 승인 / (승인 + 거절). 판정 난 건이 없으면 null → 화면에 '-'
        model.addAttribute("approvalRate", approved + rejected == 0 ? null : approved * 100 / (approved + rejected));
        model.addAttribute("status", status);
        model.addAttribute("memberLoginId", memberLoginId);
        model.addAttribute("from", from);
        model.addAttribute("to", to);
        model.addAttribute("statuses", LoanApplicationStatus.values());
        model.addAttribute("statusLabels", AdminLoanService.STATUS_LABELS);

        return "backoffice/admin/loan/loan-list";
    }
}
