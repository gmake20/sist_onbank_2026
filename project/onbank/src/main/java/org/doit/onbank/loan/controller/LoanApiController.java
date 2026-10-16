
package org.doit.onbank.loan.controller;

import org.doit.onbank.loan.dto.LoanApplicationForm;
import org.doit.onbank.loan.service.LoanApplicationService;
import org.doit.onbank.loan.service.LoanService;
import org.doit.onbank.member.security.LoginMember;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/loans")
public class LoanApiController {

    private final LoanService loanService;
    private final LoanApplicationService applicationService;

    // 1. 대출 신청
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("isAuthenticated()")
    public Long apply(
            @Valid @RequestBody LoanApplicationForm request,
            @AuthenticationPrincipal LoginMember me) {

        return applicationService.apply(
                request,
                me.getMemberId()
        );
    }
}
