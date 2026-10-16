package org.doit.onbank.loan.controller.admin;

import org.doit.onbank.loan.dto.LoanReviewApproveRequest;
import org.doit.onbank.loan.service.LoanReviewService;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/loan-applications")
@PreAuthorize("hasRole('ADMIN')")
@Validated
public class LoanReviewController {

    private final LoanReviewService reviewService;

    // 심사 시작
    @PostMapping("/{id}/review")
    public ResponseEntity<String> startReview(
            @PathVariable("id") Long id
    ) {
        reviewService.startReview(id);

        return ResponseEntity.ok("심사가 시작되었습니다.");
    }

    // 대출 승인
    @PostMapping("/{id}/approve")
    public ResponseEntity<String> approve(
            @PathVariable("id") Long id,
            @Valid @RequestBody LoanReviewApproveRequest request,
            @AuthenticationPrincipal(expression = "employeeId")
            Long reviewerId
    ) {

        reviewService.approve(
                id,
                request.approvedAmount(),
                request.approvedInterestRate(),
                request.repaymentMethod(),
                request.startDate(),
                reviewerId
        );

        return ResponseEntity.ok("대출 심사가 승인되었습니다.");
    }
}