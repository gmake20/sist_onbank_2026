package org.doit.onbank.loan.controller.admin;

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
@RequestMapping("/admin/loans/review")
@PreAuthorize("hasRole('ADMIN')")
public class LoanReviewActionController {
	
    private final LoanReviewService reviewService;
    private final LoanReviewerIdentity reviewerIdentity;

    @PostMapping("/{id}/start")
    public String start(@PathVariable("id") Long id, Authentication auth) {
        reviewerIdentity.requireEmployeeId(auth);
        reviewService.startReview(id);
        return redirect(id);
    }

    @PostMapping("/{id}/request-info")
    public String requestInfo(@PathVariable("id") Long id,
            @RequestParam("reason") String reason, Authentication auth) {
    	
        Long reviewerId = reviewerIdentity.requireEmployeeId(auth);
        reviewService.requestAdditionalInfo(id, reason, reviewerId);
        return redirect(id);
    }

    @PostMapping("/{id}/resume")
    public String resume(@PathVariable("id") Long id, Authentication auth) {
    	
        reviewerIdentity.requireEmployeeId(auth);
        reviewService.resumeReview(id);
        return redirect(id);
    }

    @PostMapping("/{id}/reject")
    public String reject(@PathVariable("id") Long id,
            @RequestParam("reason") String reason, Authentication auth) {
    	
        Long reviewerId = reviewerIdentity.requireEmployeeId(auth);
        reviewService.reject(id, reason, reviewerId);
        return redirect(id);
    }

    @PostMapping("/{id}/approve")
    public String approve(@PathVariable("id") Long id,
            @RequestParam("approvedAmount") BigDecimal amount,
            @RequestParam("approvedInterestRate") BigDecimal rate,
            @RequestParam("repaymentMethod") String repaymentMethod,
            @RequestParam("startDate") @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm") LocalDateTime startDate,
            Authentication auth) {
    	
        Long reviewerId = reviewerIdentity.requireEmployeeId(auth);
        reviewService.approve(id, amount, rate, repaymentMethod, startDate, reviewerId);
        return redirect(id);
    }

    private String redirect(Long id) {
        return "redirect:/admin/loans/review?id=" + id;
    }
}
