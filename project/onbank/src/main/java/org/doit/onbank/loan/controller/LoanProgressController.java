package org.doit.onbank.loan.controller;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

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

        List<LoanApplication> applications =
                applicationService.findMyApplications(me.getMemberId());

        Map<LoanApplicationStatus, Long> statusCounts =
                new EnumMap<>(LoanApplicationStatus.class);

        Arrays.stream(LoanApplicationStatus.values())
                .forEach(status -> statusCounts.put(status, 0L));

        for (LoanApplication application : applications) {
            statusCounts.merge(
                    application.getStatus(),
                    1L,
                    Long::sum
            );
        }

        model.addAttribute("applications", applications);
        model.addAttribute("totalCount", applications.size());
        model.addAttribute("statusCounts", statusCounts);

        return "loan/loan-progress";
    }
}