package org.doit.onbank.admin.controller.employee;

import lombok.RequiredArgsConstructor;
import org.doit.onbank.account.repository.AccountRepository;
import org.doit.onbank.account.service.AccountNumberGenerator;
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

// 직원 거래 조회. 관리자 거래 관리(AdminTransactionController)와 같은 검색, 단 계좌번호를 정확히 넣어야 조회한다 ("검색 후 조회", D-02)
// 검색 쿼리는 계좌번호 일부 일치(LIKE)라서, 있는 계좌번호인지 먼저 확인해 일부만 넣은 검색을 막는다
@Controller
@RequestMapping("/employee/transactions")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('STAFF','MANAGER')")
public class EmployeeTransactionController {

    private static final int MAX_MONTHS = 3;    // 명세서 2.7 기간 최대 3개월 (관리자 화면과 같음)

    private final AdminTransactionService adminTransactionService;
    private final AccountRepository accountRepository;

    // 기간을 안 고르면 오늘 ~ 오늘
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

        String searchError = searchError(from, to, accountNumber, min, max);
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

        return "backoffice/employee/transaction/transactions";
    }

    // 이체내역 탭. 기간 기본값·검사는 전체 거래와 같다
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

        String searchError = searchError(from, to, accountNumber, min, max);
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

        return "backoffice/employee/transaction/transfers";
    }

    // 두 탭 공통 검색 조건 검사. 관리자 쪽 검사 + 계좌번호 필수·정확히 일치. 문제가 없으면 null
    // 하이픈 없이 13자리로 넣어도 팀원2 normalize 가 1002-482-915307 모양으로 맞춘다
    private String searchError(LocalDate from, LocalDate to, String accountNumber, Long min, Long max) {
        if (accountNumber == null || accountNumber.isBlank()) {
            return "계좌번호를 입력하고 검색해 주세요.";
        }
        if (!accountRepository.existsByAccountNumber(AccountNumberGenerator.normalize(accountNumber))) {
            return "계좌번호를 정확히 입력해 주세요. 일치하는 계좌가 없습니다.";
        }
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
