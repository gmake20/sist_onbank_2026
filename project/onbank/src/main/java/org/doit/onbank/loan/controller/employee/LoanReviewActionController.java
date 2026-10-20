
package org.doit.onbank.loan.controller.employee;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.doit.onbank.loan.security.LoanReviewerIdentity;
import org.doit.onbank.loan.service.LoanReviewService;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
@RequestMapping("/employee/loans/review")
@PreAuthorize("hasAnyRole('STAFF', 'MANAGER')")
public class LoanReviewActionController {

    private final LoanReviewService reviewService;
    private final LoanReviewerIdentity reviewerIdentity;

    // 1. 대출 심사 시작
    // STAFF, MANAGER
    @PostMapping("/{id}/start")
    public String start(
            @PathVariable("id") Long id,
            Authentication auth) {

        reviewerIdentity.requireEmployeeId(auth);

        reviewService.startReview(id);

        return redirect(id);
    }

    // 2. 추가 서류 요청
    // STAFF, MANAGER
    @PostMapping("/{id}/request-info")
    public String requestInfo(
            @PathVariable("id") Long id,
            @RequestParam("reason") String reason,
            Authentication auth) {

        Long employeeId =
                reviewerIdentity.requireEmployeeId(auth);

        reviewService.requestAdditionalInfo(
                id,
                reason,
                employeeId
        );

        return redirect(id);
    }

    // 3. 서류보완 후 심사 재개
    // STAFF, MANAGER
    @PostMapping("/{id}/resume")
    public String resume(
            @PathVariable("id") Long id,
            Authentication auth) {

        reviewerIdentity.requireEmployeeId(auth);

        reviewService.resumeReview(id);

        return redirect(id);
    }

    // 4. 대출 거절
    // MANAGER 전용
    @PostMapping("/{id}/reject")
    @PreAuthorize("hasRole('MANAGER')")
    public String reject(
            @PathVariable("id") Long id,
            @RequestParam("reason") String reason,
            Authentication auth) {

        Long employeeId =
                reviewerIdentity.requireEmployeeId(auth);

        reviewService.reject(
                id,
                reason,
                employeeId
        );

        return redirect(id);
    }

    // 5. 대출 승인
    // MANAGER 전용
    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('MANAGER')")
    public String approve(
            @PathVariable("id") Long id,

            @RequestParam("approvedAmount")
            BigDecimal amount,

            @RequestParam("approvedInterestRate")
            BigDecimal rate,

            @RequestParam("startDate")
            @DateTimeFormat(
                pattern = "yyyy-MM-dd'T'HH:mm"
            )
            LocalDateTime startDate,

            Authentication auth) {

        Long employeeId =
                reviewerIdentity.requireEmployeeId(auth);

        reviewService.approve(
                id,
                employeeId,
                amount,
                rate,
                startDate
        );

        return redirect(id);
    }

    private String redirect(Long id) {
    	return "redirect:/employee/loans/review/" + id;
    }
}
