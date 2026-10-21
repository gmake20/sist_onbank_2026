package org.doit.onbank.admin.controller;

import lombok.RequiredArgsConstructor;
import org.doit.onbank.admin.security.AdminPrincipal;
import org.doit.onbank.admin.service.AdminFdsService;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;

// S-15 이상거래 탐지 목록 (FDS-002)
// 역할은 사이드바·화면목록 5장과 같게. 직원(STAFF·MANAGER) 조회는 /employee 쪽에서 따로 (D-02)
@Controller
@RequestMapping("/admin/fds")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN','OPERATOR')")
public class AdminFdsController {

    private static final int DEFAULT_DAYS = 30;     // 기간을 안 고르면 최근 30일 (계좌 상태 이력과 같음)

    private final AdminFdsService adminFdsService;

    // 처음 들어오면 처리 중만. "전체"는 빈 값 대신 ALL 을 보낸다
    // (빈 값이면 defaultValue 가 다시 적용돼 처리 중으로 바뀜. 서비스는 모르는 값을 전체로 본다)
    @GetMapping
    public String list(@AuthenticationPrincipal AdminPrincipal me,
                       @RequestParam(name = "status", defaultValue = AdminFdsService.FILTER_OPEN) String status,
                       @RequestParam(name = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                       @RequestParam(name = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                       @RequestParam(name = "accountNumber", required = false) String accountNumber,
                       @RequestParam(name = "mine", defaultValue = "false") boolean mine,
                       @RequestParam(name = "page", defaultValue = "0") int page,
                       Model model) {
        if (to == null) {
            to = LocalDate.now();
        }
        if (from == null) {
            from = to.minusDays(DEFAULT_DAYS);
        }

        if (from.isAfter(to)) {
            model.addAttribute("searchError", "시작일이 종료일보다 늦습니다.");
            model.addAttribute("detections", Page.empty());
        } else {
            model.addAttribute("detections", adminFdsService.search(from, to, status, accountNumber,
                    mine ? me.getAdminId() : null, page));
        }
        model.addAttribute("status", status);
        model.addAttribute("from", from);
        model.addAttribute("to", to);
        model.addAttribute("accountNumber", accountNumber);
        model.addAttribute("mine", mine);
        model.addAttribute("statusLabels", AdminFdsService.STATUS_LABELS);
        model.addAttribute("ruleLabels", AdminFdsService.RULE_LABELS);
        model.addAttribute("txTypeLabels", AdminFdsService.TX_TYPE_LABELS);

        return "backoffice/admin/fds/detections";
    }
}
