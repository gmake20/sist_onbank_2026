
package org.doit.onbank.loan.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.doit.onbank.employee.entity.Employee;
import org.doit.onbank.employee.entity.EmployeeRole;
import org.doit.onbank.employee.entity.EmployeeStatus;
import org.doit.onbank.employee.repository.EmployeeRepository;
import org.doit.onbank.loan.entity.LoanApplication;
import org.doit.onbank.loan.repository.LoanApplicationRepository;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class LoanReviewService {

    private final LoanApplicationRepository applicationRepository;
    private final EmployeeRepository employeeRepository;

    // 대출 신청 조회 + 비관적 락
    private LoanApplication findForUpdate(Long applicationId) {

        return applicationRepository
                .findByIdForUpdate(applicationId)
                .orElseThrow(() ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "대출 신청 정보가 없습니다."
                    )
                );
    }

    // 직원 조회 및 권한 검증
    private Employee requireEmployee(
            Long employeeId,
            boolean managerRequired) {

        if (employeeId == null) {
            throw new AccessDeniedException(
                    "직원 인증 정보가 없습니다."
            );
        }

        Employee employee = employeeRepository
                .findById(employeeId)
                .orElseThrow(() ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "직원 정보가 없습니다."
                    )
                );

        if (employee.getStatus() != EmployeeStatus.ACTIVE) {
            throw new AccessDeniedException(
                    "활성 상태의 직원만 심사할 수 있습니다."
            );
        }

        if (managerRequired
                && employee.getRole() != EmployeeRole.MANAGER) {
            throw new AccessDeniedException(
                    "대출 승인 및 거절 권한이 없습니다."
            );
        }

        return employee;
    }

    // =====================================
    // 1. 심사 시작 - 직원
    // =====================================
    @Transactional
    @PreAuthorize("hasAnyRole('STAFF', 'MANAGER')")
    public void startReview(Long applicationId) {

        LoanApplication application =
                findForUpdate(applicationId);

        application.startReview();
    }

    // =====================================
    // 2. 추가 서류 요청 - 직원
    // =====================================
    @Transactional
    @PreAuthorize("hasAnyRole('STAFF', 'MANAGER')")
    public void requestAdditionalInfo(
            Long applicationId,
            String reason,
            Long employeeId) {

        Employee employee = requireEmployee(employeeId, false);

        LoanApplication application =
                findForUpdate(applicationId);

        application.requestAdditionalInfo(
                reason,
                employee
        );
    }

    // =====================================
    // 3. 심사 재개 - 직원
    // =====================================
    @Transactional
    @PreAuthorize("hasAnyRole('STAFF', 'MANAGER')")
    public void resumeReview(Long applicationId) {

        LoanApplication application =
                findForUpdate(applicationId);

        application.resumeReview();
    }

    // =====================================
    // 4. 대출 거절 - 관리자급 직원
    // =====================================
    @Transactional
    @PreAuthorize("hasRole('MANAGER')")
    public void reject(
            Long applicationId,
            String reason,
            Long employeeId) {

        Employee employee = requireEmployee(employeeId, true);

        LoanApplication application =
                findForUpdate(applicationId);

        application.reject(
                reason,
                employee
        );
    }

    // =====================================
    // 5. 대출 승인 - 관리자급 직원
    // =====================================
    @Transactional
    @PreAuthorize("hasRole('MANAGER')")
    public Long approve(
            Long applicationId,
            Long employeeId,
            BigDecimal approvedAmount,
            BigDecimal approvedRate,
            LocalDateTime startedAt) {

        Employee employee = requireEmployee(employeeId, true);

        LoanApplication application =
                findForUpdate(applicationId);

        application.approve(
                approvedAmount,
                approvedRate,
                employee,
                startedAt
        );

        // 대출 계좌 생성
        // 입출금 계좌에 입금

        return application.getId();
    }
}
