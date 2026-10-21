package org.doit.onbank.admin.controller;

import lombok.RequiredArgsConstructor;
import org.doit.onbank.admin.service.AdminTransactionService;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;

// S-09 거래 관리 — 전체 거래 탭 (TRX-001)
// 역할은 사이드바·화면목록 5장과 같게. 직원(STAFF·MANAGER) "검색 후 조회"는 /employee 쪽에서 따로 (D-02)
@Controller
@RequestMapping("/admin/transactions")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN','OPERATOR')")
public class AdminTransactionController {

    private static final int MAX_MONTHS = 3;    // 명세서 2.7 기간 최대 3개월 (NFR-ADM-PRF-001)

    private final AdminTransactionService adminTransactionService;

    // 기간을 안 고르면 오늘 ~ 오늘. 대시보드 "오늘 거래" 카드는 from·to·excludeIn 을 넘긴다
    @GetMapping
    public String list(@RequestParam(name = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                       @RequestParam(name = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                       @RequestParam(name = "type", required = false) String type,
                       @RequestParam(name = "excludeIn", defaultValue = "false") boolean excludeIn,
                       @RequestParam(name = "accountNumber", required = false) String accountNumber,
                       @RequestParam(name = "min", required = false) Long min,
                       @RequestParam(name = "max", required = false) Long max,
                       @RequestParam(name = "page", defaultValue = "0") int page,
                       Model model) {
        if (to == null) {
            to = LocalDate.now();
        }
        if (from == null) {
            from = to;
        }

        String searchError = searchError(from, to, min, max);
        if (searchError != null) {
            model.addAttribute("searchError", searchError);
            model.addAttribute("transactions", Page.empty());
            model.addAttribute("totalAmount", 0L);
        } else {
            model.addAttribute("transactions",
                    adminTransactionService.search(from, to, type, excludeIn, accountNumber, min, max, page));
            model.addAttribute("totalAmount",
                    adminTransactionService.sumAmount(from, to, type, excludeIn, accountNumber, min, max));
        }
        model.addAttribute("from", from);
        model.addAttribute("to", to);
        model.addAttribute("type", type);
        model.addAttribute("excludeIn", excludeIn);
        model.addAttribute("accountNumber", accountNumber);
        model.addAttribute("min", min);
        model.addAttribute("max", max);
        model.addAttribute("typeLabels", AdminTransactionService.TYPE_LABELS);

        return "backoffice/admin/transaction/transactions";
    }

    // S-09 이체내역 탭 (TRX-002). 기간 기본값·검사는 전체 거래와 같다
    @GetMapping("/transfers")
    public String transfers(@RequestParam(name = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                            @RequestParam(name = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                            @RequestParam(name = "accountNumber", required = false) String accountNumber,
                            @RequestParam(name = "min", required = false) Long min,
                            @RequestParam(name = "max", required = false) Long max,
                            @RequestParam(name = "page", defaultValue = "0") int page,
                            Model model) {
        if (to == null) {
            to = LocalDate.now();
        }
        if (from == null) {
            from = to;
        }

        String searchError = searchError(from, to, min, max);
        if (searchError != null) {
            model.addAttribute("searchError", searchError);
            model.addAttribute("transfers", Page.empty());
        } else {
            model.addAttribute("transfers",
                    adminTransactionService.searchTransfers(from, to, accountNumber, min, max, page));
        }
        model.addAttribute("from", from);
        model.addAttribute("to", to);
        model.addAttribute("accountNumber", accountNumber);
        model.addAttribute("min", min);
        model.addAttribute("max", max);
        model.addAttribute("typeLabels", AdminTransactionService.TRANSFER_TYPE_LABELS);

        return "backoffice/admin/transaction/transfers";
    }

    // 두 탭 공통 검색 조건 검사. 문제가 없으면 null (명세서 2.7)
    private String searchError(LocalDate from, LocalDate to, Long min, Long max) {
        if (from.isAfter(to)) {
            return "시작일이 종료일보다 늦습니다.";
        }
        if (from.isBefore(to.minusMonths(MAX_MONTHS))) {
            return "기간은 최대 3개월까지 조회할 수 있습니다.";
        }
        if (min != null && max != null && min > max) {
            return "최소 금액이 최대 금액보다 큽니다.";
        }
        return null;
    }
}
