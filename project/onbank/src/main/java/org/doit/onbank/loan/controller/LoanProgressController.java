package org.doit.onbank.loan.controller;

import java.util.List;

import org.doit.onbank.loan.entity.LoanApplication;
import org.doit.onbank.loan.entity.LoanApplicationStatus;
import org.doit.onbank.loan.service.LoanApplicationService;
import org.doit.onbank.member.security.LoginMember;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
@RequestMapping("/loan")
public class LoanProgressController {

    private final LoanApplicationService applicationService;

    @GetMapping("/progress")
    public String progress(
            @AuthenticationPrincipal LoginMember me,
            Model model
    ) {

        if (me == null) {
            return "redirect:/login";
        }

        Long memberId = me.getMemberId();

        List<LoanApplication> applications =
                applicationService.findMyApplications(memberId);

        long totalCount = applications.size();

        long pendingCount = applications.stream()
                .filter(a -> a.getStatus() ==
                        LoanApplicationStatus.PENDING)
                .count();

        long reviewingCount = applications.stream()
                .filter(a -> a.getStatus() ==
                        LoanApplicationStatus.REVIEWING)
                .count();

        long approvedCount = applications.stream()
                .filter(a -> a.getStatus() ==
                        LoanApplicationStatus.APPROVED)
                .count();

        model.addAttribute("applications", applications);
        model.addAttribute("totalCount", totalCount);
        model.addAttribute("pendingCount", pendingCount);
        model.addAttribute("reviewingCount", reviewingCount);
        model.addAttribute("approvedCount", approvedCount);

        return "loan/loan-progress";
    }
}