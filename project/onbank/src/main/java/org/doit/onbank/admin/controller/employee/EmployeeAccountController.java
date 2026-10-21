package org.doit.onbank.admin.controller.employee;

import lombok.RequiredArgsConstructor;
import org.doit.onbank.account.entity.AccountStatus;
import org.doit.onbank.account.entity.AccountType;
import org.doit.onbank.admin.service.AdminAccountService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

// 직원 계좌 조회. 관리자 계좌 목록(AdminAccountController)과 같은 검색, 거래정지·해제·상태 이력은 없음 (조회 전용)
@Controller
@RequestMapping("/employee/accounts")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('STAFF','MANAGER')")
public class EmployeeAccountController {

    private final AdminAccountService adminAccountService;

    // select에서 "전체"(빈 값)를 고르면 enum은 null로 들어온다
    @GetMapping
    public String list(@RequestParam(name = "accountNumber", required = false) String accountNumber,
                       @RequestParam(name = "memberLoginId", required = false) String memberLoginId,
                       @RequestParam(name = "status", required = false) AccountStatus status,
                       @RequestParam(name = "type", required = false) AccountType type,
                       @RequestParam(name = "page", defaultValue = "0") int page,
                       Model model) {
        model.addAttribute("accounts", adminAccountService.search(accountNumber, memberLoginId, status, type, page));
        model.addAttribute("accountNumber", accountNumber);
        model.addAttribute("memberLoginId", memberLoginId);
        model.addAttribute("status", status);
        model.addAttribute("type", type);
        model.addAttribute("statuses", AccountStatus.values());
        model.addAttribute("types", AccountType.values());

        return "backoffice/employee/account/accounts";
    }
}
