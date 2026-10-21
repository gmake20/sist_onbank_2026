
package org.doit.onbank.loan.controller;

import java.time.LocalDate;
import java.util.List;

import org.doit.onbank.loan.entity.LoanAccount;
import org.doit.onbank.loan.entity.LoanAutoRepayment;
import org.doit.onbank.loan.entity.LoanRepaymentSchedule;
import org.doit.onbank.loan.repository.LoanAccountRepository;
import org.doit.onbank.loan.repository.LoanAutoRepaymentRepository;
import org.doit.onbank.loan.repository.LoanRepaymentScheduleRepository;
import org.doit.onbank.loan.service.LoanAutoRepaymentService;
import org.doit.onbank.loan.service.LoanRepaymentService;
import org.doit.onbank.member.security.LoginMember;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
@RequestMapping("/loan/repayment")
public class LoanRepaymentPageController {

    private final LoanAccountRepository loanAccounts;
    private final LoanRepaymentScheduleRepository schedules;
    private final LoanRepaymentService repaymentService;
    
    private final LoanAutoRepaymentService autoRepaymentService;
    
    private final LoanAutoRepaymentRepository autoRepaymentRepository;

    // =====================================
    // 1. 대출금 상환 페이지
    // =====================================

    @GetMapping
    @Transactional(readOnly = true)
    public String repaymentPage(
            @AuthenticationPrincipal LoginMember member,
            @RequestParam(required = false) Long accountId,
            Model model) {

        if (member == null) {
            return "redirect:/login";
        }

        // 로그인 회원의 대출 계좌 조회
        List<LoanAccount> loans = loanAccounts
                .findByLoanApplication_Member_MemberIdOrderByExecutedAtDesc(
                        member.getMemberId());

        model.addAttribute("loans", loans);

        // 선택된 대출 계좌
        LoanAccount selected = null;

        if (accountId == null) {
            if (!loans.isEmpty()) {
                selected = loans.get(0);
            }
        } else {
            selected = loans.stream()
                    .filter(l ->
                        accountId.equals(l.getLoanAccountId()))
                    .findFirst()
                    .orElseThrow(() ->
                        new AccessDeniedException(
                            "조회 권한이 없는 대출 계좌입니다."));
        }

        model.addAttribute("selectedLoan", selected);
        
        boolean autoEnabled = selected != null
                && autoRepaymentRepository
                    .findByLoanAccountId(selected.getLoanAccountId())
                    .map(LoanAutoRepayment::isEnabled)
                    .orElse(false);

        model.addAttribute("autoEnabled", autoEnabled);

        // 선택된 대출 계좌의 상환 일정 조회
        List<LoanRepaymentSchedule> repaymentSchedules =
                selected == null
                    ? List.of()
                    : schedules
                        .findByLoanAccount_LoanAccountIdOrderByInstallmentNoAsc(
                            selected.getLoanAccountId());

        model.addAttribute("schedules", repaymentSchedules);
        model.addAttribute("today", LocalDate.now());

        return "loan/loan-repayment";
    }

    // =====================================
    // 2. 대출금 상환 실행
    // =====================================

    @PostMapping("/pay")
    public String repay(
            @RequestParam Long loanAccountId,
            @RequestParam Long scheduleId,
            @AuthenticationPrincipal LoginMember member,
            RedirectAttributes redirectAttributes) {

        if (member == null) {
            return "redirect:/login";
        }

        try {
            // 실제 출금 및 상환 처리
            repaymentService.repay(
                    loanAccountId,
                    scheduleId,
                    member.getMemberId());

            redirectAttributes.addFlashAttribute(
                    "message",
                    "대출금 상환이 완료되었습니다.");

        } catch (IllegalArgumentException
                 | IllegalStateException e) {

            redirectAttributes.addFlashAttribute(
                    "error",
                    e.getMessage());
        }

        // 처리한 대출 계좌의 화면으로 돌아가기
        redirectAttributes.addAttribute(
                "accountId", loanAccountId);

        return "redirect:/loan/repayment";
    }
    
 // 자동상환 등록
    @PostMapping("/auto/register")
    public String registerAutoRepayment(
            @RequestParam Long loanAccountId,
            @AuthenticationPrincipal LoginMember member,
            RedirectAttributes redirectAttributes) {

        if (member == null) {
            return "redirect:/login";
        }

        try {
            autoRepaymentService.register(
                    loanAccountId, member.getMemberId());

            redirectAttributes.addFlashAttribute(
                    "message", "대출 자동상환이 등록되었습니다.");

        } catch (IllegalArgumentException | IllegalStateException e) {
            redirectAttributes.addFlashAttribute(
                    "error", e.getMessage());
        }

        redirectAttributes.addAttribute("accountId", loanAccountId);
        return "redirect:/loan/repayment";
    }

    // 자동상환 해지
    @PostMapping("/auto/cancel")
    public String cancelAutoRepayment(
            @RequestParam Long loanAccountId,
            @AuthenticationPrincipal LoginMember member,
            RedirectAttributes redirectAttributes) {

        if (member == null) {
            return "redirect:/login";
        }

        try {
            autoRepaymentService.cancel(
                    loanAccountId, member.getMemberId());

            redirectAttributes.addFlashAttribute(
                    "message", "대출 자동상환이 해지되었습니다.");

        } catch (IllegalArgumentException | IllegalStateException e) {
            redirectAttributes.addFlashAttribute(
                    "error", e.getMessage());
        }

        redirectAttributes.addAttribute("accountId", loanAccountId);
        return "redirect:/loan/repayment";
    }
}
