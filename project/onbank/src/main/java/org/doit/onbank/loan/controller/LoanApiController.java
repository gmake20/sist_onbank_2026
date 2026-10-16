
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

    /*
    // 2. 대출 심사 시작
    @PostMapping("/{applicationId}/review")
    @PreAuthorize("hasRole('ADMIN')")
    public void startReview(
            @PathVariable("applicationId") Long applicationId) {

        loanService.startReview(applicationId);
    }

    // 3. 대출 승인
    @PostMapping("/{applicationId}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public Long approve(
            @PathVariable("applicationId") Long applicationId,
            @Valid @RequestBody LoanApproveRequest request,
            Authentication authentication) {

        Long reviewerId = memberIdentity.requireMemberId();

        // 승인 금리는 관리자 입력값 또는 심사 결과에서 확보해야 함
        BigDecimal approvedRate = request.approvedInterestRate();

        return loanService.approve(
                applicationId,
                reviewerId,
                request.approvedAmount(),
                approvedRate,
                request.startedAt()
        );
    }

    // 4. 상환 일정 조회
    @GetMapping("/{applicationId}/schedule")
    @PreAuthorize("isAuthenticated()")
    public List<ScheduleResponse> getSchedule(
            @PathVariable("applicationId") Long applicationId,
            Authentication authentication) {

        boolean admin = authentication.getAuthorities()
                .stream()
                .anyMatch(a ->
                        "ROLE_ADMIN".equals(a.getAuthority())
                );

        return loanService.getSchedule(
                applicationId,
                authentication.getName(),
                admin
        );
    }
    */
}
