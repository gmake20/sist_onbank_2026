
package org.doit.onbank.loan.controller.employee;

import org.doit.onbank.loan.dto.LoanReviewApproveRequest;
import org.doit.onbank.loan.security.LoanReviewerIdentity;
import org.doit.onbank.loan.service.LoanReviewService;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/employee/api/loan-applications")
@PreAuthorize("hasAnyRole('STAFF', 'MANAGER')")
@Validated
public class LoanReviewController {

    private final LoanReviewService reviewService;
    private final LoanReviewerIdentity reviewerIdentity;

    // 1. 대출 심사 시작
    // STAFF, MANAGER
    @PostMapping("/{id}/review")
    public ResponseEntity<String> startReview(
            @PathVariable("id") Long id,
            Authentication authentication) {

        reviewerIdentity.requireEmployeeId(authentication);

        reviewService.startReview(id);

        return ResponseEntity.ok(
                "대출 심사가 시작되었습니다."
        );
    }

    // 2. 대출 승인
    // MANAGER 전용
    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<String> approve(
            @PathVariable("id") Long id,
            @Valid @RequestBody LoanReviewApproveRequest request,
            Authentication authentication) {

        // 로그인한 직원 ID
        Long employeeId =
                reviewerIdentity.requireEmployeeId(authentication);

        // 대출 승인 처리
        reviewService.approve(
                id,
                employeeId,
                request.approvedAmount(),
                request.approvedInterestRate(),
                request.startDate()
        );

        return ResponseEntity.ok(
                "대출 심사가 승인되었습니다."
        );
    }
}
