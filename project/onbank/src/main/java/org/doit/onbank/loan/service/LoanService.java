
package org.doit.onbank.loan.service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

import org.doit.onbank.loan.dto.ScheduleResponse;
import org.doit.onbank.loan.entity.LoanApplication;
import org.doit.onbank.loan.entity.LoanApplicationStatus;
import org.doit.onbank.loan.entity.LoanRepaymentSchedule;
import org.doit.onbank.loan.repository.LoanApplicationRepository;
import org.doit.onbank.loan.repository.LoanRepaymentScheduleRepository;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class LoanService {

    private final LoanApplicationRepository applications;
    private final LoanRepaymentScheduleRepository schedules;

    private static final RoundingMode ROUND =
            RoundingMode.HALF_UP;

    // =====================================
    // 1. 상환 일정 생성
    // =====================================

    @Transactional
    @PreAuthorize("hasRole('MANAGER')")
    public void generateSchedule(Long applicationId) {

        LoanApplication application = applications
                .findByIdForUpdate(applicationId)
                .orElseThrow(() ->
                    new NoSuchElementException(
                        "대출 신청이 존재하지 않습니다."
                    )
                );

        if (application.getStatus()
                != LoanApplicationStatus.APPROVED) {
            throw new IllegalStateException(
                "승인된 대출만 상환 일정을 생성할 수 있습니다."
            );
        }

        if (schedules.existsByLoanApplication_Id(applicationId)) {
            throw new IllegalStateException(
                "상환 일정이 이미 생성되었습니다."
            );
        }

        if (application.getStartedAt() == null) {
            throw new IllegalStateException(
                "대출 시작일이 등록되지 않았습니다."
            );
        }

        List<LoanRepaymentSchedule> result =
                calculate(application);

        schedules.saveAll(result);
    }

    // =====================================
    // 2. 상환 일정 조회
    // =====================================

    @Transactional(readOnly = true)
    @PreAuthorize("isAuthenticated()")
    public List<ScheduleResponse> getSchedule(
            Long applicationId,
            String username,
            boolean isAdmin) {

        LoanApplication application = applications
                .findById(applicationId)
                .orElseThrow(() ->
                    new NoSuchElementException(
                        "대출 신청이 존재하지 않습니다."
                    )
                );

        if (!isAdmin
                && (username == null
                    || !username.equals(
                        application.getApplicantUsername()))) {

            throw new AccessDeniedException(
                "본인의 대출 상환 일정만 조회할 수 있습니다."
            );
        }

        return schedules
                .findByLoanApplication_IdOrderByInstallmentNo(
                        applicationId
                )
                .stream()
                .map(s -> new ScheduleResponse(
                        s.getInstallmentNo(),
                        s.getDueDate(),
                        s.getPrincipalAmount(),
                        s.getInterestAmount(),
                        s.getTotalAmount(),
                        s.getStatus()
                ))
                .toList();
    }

    // =====================================
    // 3. 상환 일정 계산
    // =====================================

    private List<LoanRepaymentSchedule> calculate(
            LoanApplication application) {

        BigDecimal principal =
                application.getApprovedAmount();

        Integer monthsValue =
                application.getRequestedMonths();

        BigDecimal annualRate =
                application.getApprovedInterestRate();

        String method =
                application.getRepaymentMethod();

        if (principal == null
                || principal.signum() <= 0
                || principal.scale() > 0) {
            throw new IllegalArgumentException(
                "승인 금액이 올바르지 않습니다."
            );
        }

        if (monthsValue == null
                || monthsValue < 1
                || monthsValue > 600) {
            throw new IllegalArgumentException(
                "대출 기간이 올바르지 않습니다."
            );
        }

        if (annualRate == null
                || annualRate.signum() < 0
                || annualRate.compareTo(
                    new BigDecimal("100")) > 0) {
            throw new IllegalArgumentException(
                "승인 금리가 올바르지 않습니다."
            );
        }

        if (method == null
                || !List.of("EQUAL", "PRINCIPAL", "BULLET")
                        .contains(method)) {
            throw new IllegalArgumentException(
                "상환 방식이 올바르지 않습니다."
            );
        }

        int months = monthsValue;

        BigDecimal monthlyRate = annualRate.divide(
                new BigDecimal("1200"),
                18,
                ROUND
        );

        BigDecimal fixedPayment = BigDecimal.ZERO;

        // 원리금균등상환의 매월 납입액
        if ("EQUAL".equals(method)) {

            if (monthlyRate.signum() == 0) {

                fixedPayment = principal.divide(
                        BigDecimal.valueOf(months),
                        18,
                        ROUND
                );

            } else {

                BigDecimal factor = BigDecimal.ONE
                        .add(monthlyRate)
                        .pow(
                            months,
                            MathContext.DECIMAL128
                        );

                fixedPayment = principal
                        .multiply(monthlyRate)
                        .multiply(factor)
                        .divide(
                            factor.subtract(BigDecimal.ONE),
                            18,
                            ROUND
                        );
            }
        }

        List<LoanRepaymentSchedule> result =
                new ArrayList<>(months);

        BigDecimal balance = principal;

        LocalDateTime now = LocalDateTime.now();

        for (int i = 1; i <= months; i++) {

            // 해당 회차 이자
            BigDecimal interest = balance
                    .multiply(monthlyRate)
                    .setScale(0, ROUND);

            BigDecimal paidPrincipal;

            // 상환방식별 원금 계산
            switch (method) {

                case "EQUAL" -> {

                    paidPrincipal = fixedPayment
                            .subtract(interest)
                            .setScale(0, ROUND);
                }

                case "PRINCIPAL" -> {

                    paidPrincipal = principal.divide(
                            BigDecimal.valueOf(months),
                            0,
                            ROUND
                    );
                }

                case "BULLET" -> {

                    paidPrincipal = (i == months)
                            ? balance
                            : BigDecimal.ZERO;
                }

                default -> throw new IllegalStateException(
                    "지원하지 않는 상환 방식입니다."
                );
            }

            // 음수 방지
            if (paidPrincipal.signum() < 0) {
                paidPrincipal = BigDecimal.ZERO;
            }

            // 남은 원금 초과 방지
            if (paidPrincipal.compareTo(balance) > 0) {
                paidPrincipal = balance;
            }

            // 마지막 회차 잔액 정산
            if (i == months) {
                paidPrincipal = balance;
            }

            balance = balance.subtract(paidPrincipal);

            BigDecimal total =
                    paidPrincipal.add(interest);

            LoanRepaymentSchedule schedule =
                    new LoanRepaymentSchedule();

            schedule.setLoanApplication(application);

            schedule.setInstallmentNo((short) i);

            schedule.setDueDate(
                    application.getStartedAt()
                            .toLocalDate()
                            .plusMonths(i)
            );

            schedule.setPrincipalAmount(
                    paidPrincipal.longValueExact()
            );

            schedule.setInterestAmount(
                    interest.longValueExact()
            );

            schedule.setTotalAmount(
                    total.longValueExact()
            );

            schedule.setStatus("SCHEDULED");
            schedule.setCreatedAt(now);
            schedule.setUpdatedAt(now);

            result.add(schedule);
        }

        return result;
    }
}
