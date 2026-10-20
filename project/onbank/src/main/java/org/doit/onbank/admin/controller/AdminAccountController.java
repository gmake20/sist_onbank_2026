package org.doit.onbank.admin.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.doit.onbank.account.entity.AccountStatus;
import org.doit.onbank.account.entity.AccountType;
import org.doit.onbank.admin.security.AdminPrincipal;
import org.doit.onbank.admin.service.AdminAccountService;
import org.doit.onbank.common.exception.BusinessException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Map;

// S-07 계좌 목록 (ACC-001), M-01 거래정지·해제 (ACC-003)
// 역할은 사이드바·화면목록 5장과 같게 (거래정지·해제·이력도 같은 역할)
@Controller
@RequestMapping("/admin/accounts")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN','OPERATOR')")
public class AdminAccountController {

    // 처리 후 목록으로 돌아갈 때 그대로 붙여 줄 검색 조건 (모달 form 의 hidden 칸)
    private static final List<String> SEARCH_KEYS = List.of("accountNumber", "memberLoginId", "status", "type", "page");

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

        return "backoffice/admin/account/accounts";
    }

    // 거래정지
    @PostMapping("/{accountId}/freeze")
    public String freeze(@AuthenticationPrincipal AdminPrincipal me, @PathVariable("accountId") Long accountId,
                         @RequestParam("reason") String reason, @RequestParam Map<String, String> params,
                         HttpServletRequest request, RedirectAttributes rttr) {
        try {
            adminAccountService.freeze(me, accountId, reason, request.getRemoteAddr());
            rttr.addFlashAttribute("message", "계좌를 거래정지했습니다.");
        } catch (BusinessException e) {
            rttr.addFlashAttribute("error", e.getMessage());
        }
        return backToList(params, rttr);
    }

    // 거래정지 해제
    @PostMapping("/{accountId}/unfreeze")
    public String unfreeze(@AuthenticationPrincipal AdminPrincipal me, @PathVariable("accountId") Long accountId,
                           @RequestParam("reason") String reason, @RequestParam Map<String, String> params,
                           HttpServletRequest request, RedirectAttributes rttr) {
        try {
            adminAccountService.unfreeze(me, accountId, reason, request.getRemoteAddr());
            rttr.addFlashAttribute("message", "거래정지를 해제했습니다.");
        } catch (BusinessException e) {
            rttr.addFlashAttribute("error", e.getMessage());
        }
        return backToList(params, rttr);
    }

    // 보던 검색 조건·페이지 그대로 목록으로 (PRG). 정해 둔 이름만 옮겨서 다른 값은 주소에 안 붙는다
    private String backToList(Map<String, String> params, RedirectAttributes rttr) {
        for (String key : SEARCH_KEYS) {
            String value = params.get(key);
            if (value != null && !value.isBlank()) {
                rttr.addAttribute(key, value);
            }
        }
        return "redirect:/admin/accounts";
    }
}
