
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

    // 1. 심사 시작
    @Transactional
    public void startReview(Long applicationId) {

        LoanApplication application = applications
                .findByIdForUpdate(applicationId)
                .orElseThrow(() ->
                        new NoSuchElementException(
                                "대출 신청이 존재하지 않습니다."));

        application.startReview();
        application.setUpdatedAt(LocalDateTime.now());
    }

    // 2. 대출 승인
    @Transactional
    public Long approve(
            Long applicationId,
            Long reviewerId,
            BigDecimal approvedAmount,
            BigDecimal approvedRate,
            LocalDateTime startedAt) {

        if (reviewerId == null || reviewerId <= 0) {
            throw new IllegalArgumentException(
                    "심사 담당자 정보가 없습니다.");
        }

        LoanApplication application = applications
                .findByIdForUpdate(applicationId)
                .orElseThrow(() ->
                        new NoSuchElementException(
                                "대출 신청이 존재하지 않습니다."));

        if (application.getStatus()
                != LoanApplicationStatus.REVIEWING) {
            throw new IllegalStateException(
                    "심사 중인 신청만 승인할 수 있습니다.");
        }

        if (schedules.existsByLoanApplication_Id(applicationId)) {
            throw new IllegalStateException(
                    "상환 일정이 이미 존재합니다.");
        }

        application.approve(
                approvedAmount,
                approvedRate,
                reviewerId,
                startedAt
        );

        List<LoanRepaymentSchedule> result =
                calculate(application);

        schedules.saveAll(result);

        return application.getId();
    }

    // 3. 본인 상환 일정 조회
    @Transactional(readOnly = true)
    public List<ScheduleResponse> getSchedule(
            Long applicationId,
            String username,
            boolean isAdmin) {

        LoanApplication application = applications
                .findById(applicationId)
                .orElseThrow(() ->
                        new NoSuchElementException(
                                "대출 신청이 존재하지 않습니다."));

        if (!isAdmin
                && (username == null
                    || !username.equals(
                        application.getApplicantUsername()))) {
            throw new AccessDeniedException(
                    "본인의 대출만 조회할 수 있습니다.");
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

    // 4. 상환 일정 계산
    private List<LoanRepaymentSchedule> calculate(
            LoanApplication application) {

        BigDecimal principal = application.getApprovedAmount();
        int months = application.getRequestedMonths();

        if (principal == null || principal.signum() <= 0
                || months < 1 || months > 600) {
            throw new IllegalArgumentException(
                    "대출 금액 또는 기간이 올바르지 않습니다.");
        }

        BigDecimal monthlyRate =
                application.getApprovedInterestRate()
                        .divide(new BigDecimal("1200"), 18, ROUND);

        String method = application.getRepaymentMethod();

        BigDecimal fixedPayment = BigDecimal.ZERO;

        if ("EQUAL".equals(method)) {

            if (monthlyRate.signum() == 0) {
                fixedPayment = principal.divide(
                        BigDecimal.valueOf(months), 18, ROUND);
            } else {
                BigDecimal factor = BigDecimal.ONE
                        .add(monthlyRate)
                        .pow(months, MathContext.DECIMAL128);

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

            BigDecimal interest = balance
                    .multiply(monthlyRate)
                    .setScale(0, ROUND);

            BigDecimal paidPrincipal;

            switch (method) {

                case "EQUAL" -> paidPrincipal = fixedPayment
                        .subtract(interest)
                        .setScale(0, ROUND);

                case "PRINCIPAL" -> paidPrincipal = principal
                        .divide(
                                BigDecimal.valueOf(months),
                                0,
                                ROUND
                        );

                case "BULLET" -> paidPrincipal =
                        i == months
                            ? balance
                            : BigDecimal.ZERO;

                default -> throw new IllegalArgumentException(
                        "상환 방식이 올바르지 않습니다.");
            }

            if (paidPrincipal.signum() < 0) {
                paidPrincipal = BigDecimal.ZERO;
            }

            if (paidPrincipal.compareTo(balance) > 0) {
                paidPrincipal = balance;
            }

            // 마지막 회차 원금 잔액 보정
            if (i == months) {
                paidPrincipal = balance;
            }

            balance = balance.subtract(paidPrincipal);

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
                    paidPrincipal.add(interest)
                            .longValueExact()
            );

            schedule.setStatus("SCHEDULED");
            schedule.setCreatedAt(now);
            schedule.setUpdatedAt(now);

            result.add(schedule);
        }

        return result;
    }
}
