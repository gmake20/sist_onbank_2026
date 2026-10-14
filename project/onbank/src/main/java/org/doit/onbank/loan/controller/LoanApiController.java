package org.doit.onbank.loan.controller;

import java.util.List;

import org.doit.onbank.loan.dto.LoanApplyRequest;
import org.doit.onbank.loan.dto.LoanApproveRequest;
import org.doit.onbank.loan.dto.LoanResponse;
import org.doit.onbank.loan.dto.ScheduleResponse;
import org.doit.onbank.loan.security.MemberIdentity;
import org.doit.onbank.loan.service.LoanService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
    private final LoanService service;
    private final MemberIdentity memberIdentity;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("isAuthenticated()")
    public LoanResponse apply(@Valid @RequestBody LoanApplyRequest request) {
        return service.apply(memberIdentity.requireMemberId(), request);
    }

    @PostMapping("/{loanId}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public LoanResponse approve(@PathVariable("loanId") Long loanId, @Valid @RequestBody LoanApproveRequest request) {
        return service.approve(loanId, memberIdentity.requireMemberId(), request);
    }

    @GetMapping("/{loanId}/schedule")
    @PreAuthorize("isAuthenticated()")
    public List<ScheduleResponse> getSchedule(@PathVariable("loanId") Long loanId, Authentication authentication) {
        boolean admin = authentication.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        return service.getSchedule(loanId, memberIdentity.requireMemberId(), admin);
    }
}