
package org.doit.onbank.loan.service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

import org.doit.onbank.loan.dto.ScheduleResponse;
import org.doit.onbank.loan.entity.LoanAccount;
import org.doit.onbank.loan.entity.LoanApplication;
import org.doit.onbank.loan.entity.LoanApplicationStatus;
import org.doit.onbank.loan.entity.LoanRepaymentSchedule;
import org.doit.onbank.loan.repository.LoanApplicationRepository;
import org.doit.onbank.loan.repository.LoanRepaymentScheduleRepository;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class LoanService {

    private final LoanApplicationRepository applications;
    private final LoanRepaymentScheduleRepository schedules;

    private static final RoundingMode ROUND =
            RoundingMode.HALF_UP;

    private static final MathContext MC =
            MathContext.DECIMAL128;

    // =====================================
    // 1. 대출 실행 시 상환 일정 생성
    // =====================================

    @Transactional(propagation = Propagation.MANDATORY)
    public void createSchedule(
            LoanAccount loanAccount,
            LoanApplication application) {

        // 1. 대출 정보 검증
        if (loanAccount == null || application == null) {
            throw new IllegalArgumentException(
                    "대출 계좌와 신청 정보가 필요합니다.");
        }

        if (loanAccount.getLoanAccountId() == null) {
            throw new IllegalStateException(
                    "저장되지 않은 대출 계좌입니다.");
        }

        if (loanAccount.getLoanApplication() == null
                || !application.getId().equals(
                        loanAccount.getLoanApplication().getId())) {
            throw new IllegalStateException(
                    "대출 계좌와 신청 정보가 일치하지 않습니다.");
        }

        // 2. 승인 상태 확인
        if (application.getStatus()
                != LoanApplicationStatus.APPROVED) {
            throw new IllegalStateException(
                    "승인된 대출만 상환 일정을 생성할 수 있습니다.");
        }

        // 3. 중복 생성 방지
        if (schedules.existsByLoanAccount_LoanAccountId(
                loanAccount.getLoanAccountId())) {
            throw new IllegalStateException(
                    "이미 상환 일정이 생성되었습니다.");
        }

        // 4. 상환 일정 계산
        List<LoanRepaymentSchedule> result =
                calculate(loanAccount, application);

        // 5. DB 저장
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
                            "대출 신청이 존재하지 않습니다.")
                );

        // 관리자 여부는 클라이언트 값이 아닌 인증 정보로 확인
        Authentication auth =
                SecurityContextHolder.getContext()
                        .getAuthentication();

        boolean privileged = auth != null
                && auth.getAuthorities().stream()
                    .anyMatch(a ->
                        a.getAuthority().equals("ROLE_ADMIN")
                        || a.getAuthority().equals("ROLE_MANAGER"));

        // 본인 또는 권한 있는 직원만 조회
        if (!privileged
                && (username == null
                    || !username.equals(auth.getName())
                    || !username.equals(
                        application.getApplicantUsername()))) {
            throw new AccessDeniedException(
                    "본인의 상환 일정만 조회할 수 있습니다.");
        }

        return schedules
                .findByLoanAccount_LoanApplication_IdOrderByInstallmentNoAsc(
                        applicationId)
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

    List<LoanRepaymentSchedule> calculate(
            LoanAccount loanAccount,
            LoanApplication application) {

        // 실행된 대출 계좌 기준
        BigDecimal principal =
                loanAccount.getPrincipalAmount();

        BigDecimal annualRate =
                loanAccount.getInterestRate();

        String method =
                loanAccount.getRepaymentMethod();

        Integer monthsValue =
                application.getRequestedMonths();

        // =====================================
        // 4. 유효성 검사
        // =====================================

        if (principal == null
                || principal.signum() <= 0
                || principal.scale() > 0) {
            throw new IllegalArgumentException(
                    "대출 원금이 올바르지 않습니다.");
        }

        if (monthsValue == null
                || monthsValue < 1
                || monthsValue > 600) {
            throw new IllegalArgumentException(
                    "대출 기간이 올바르지 않습니다.");
        }

        if (annualRate == null
                || annualRate.signum() < 0
                || annualRate.compareTo(
                        new BigDecimal("100")) > 0) {
            throw new IllegalArgumentException(
                    "대출 금리가 올바르지 않습니다.");
        }

        if (method == null
                || !List.of(
                        "EQUAL",
                        "PRINCIPAL",
                        "BULLET"
                ).contains(method)) {
            throw new IllegalArgumentException(
                    "상환 방식이 올바르지 않습니다.");
        }

        if (loanAccount.getExecutedAt() == null
                || loanAccount.getMaturityDate() == null) {
            throw new IllegalStateException(
                    "대출 실행일 또는 만기일이 없습니다.");
        }

        int months = monthsValue;

        LocalDate startDate =
                loanAccount.getExecutedAt().toLocalDate();

        LocalDate maturityDate =
                startDate.plusMonths(months);

        if (!loanAccount.getMaturityDate()
                .equals(maturityDate)) {
            throw new IllegalStateException(
                    "대출 기간과 만기일이 일치하지 않습니다.");
        }

        // =====================================
        // 5. 월 이자율 계산
        // 연이율 6% -> 월이율 0.005
        // =====================================

        BigDecimal monthlyRate = annualRate.divide(
                new BigDecimal("1200"),
                MC
        );

        // =====================================
        // 6. 원리금균등 월 납입액 계산
        // =====================================

        BigDecimal fixedPayment = BigDecimal.ZERO;

        if ("EQUAL".equals(method)) {

            if (monthlyRate.signum() == 0) {
                fixedPayment = principal.divide(
                        BigDecimal.valueOf(months),
                        0,
                        ROUND
                );
            } else {

                BigDecimal factor =
                        BigDecimal.ONE.add(monthlyRate)
                                .pow(months, MC);

                fixedPayment = principal
                        .multiply(monthlyRate, MC)
                        .multiply(factor, MC)
                        .divide(
                                factor.subtract(BigDecimal.ONE),
                                0,
                                ROUND
                        );
            }
        }

        // =====================================
        // 7. 원금균등 월 상환 원금
        // =====================================

        BigDecimal equalPrincipal = BigDecimal.ZERO;

        if ("PRINCIPAL".equals(method)) {
            equalPrincipal = principal.divide(
                    BigDecimal.valueOf(months),
                    0,
                    ROUND
            );
        }

        // =====================================
        // 8. 회차별 상환 일정 계산
        // =====================================

        List<LoanRepaymentSchedule> result =
                new ArrayList<>(months);

        BigDecimal balance = principal;

        for (int i = 1; i <= months; i++) {

            // 이번 회차 이자
            BigDecimal interest = balance
                    .multiply(monthlyRate, MC)
                    .setScale(0, ROUND);

            BigDecimal paidPrincipal;

            switch (method) {

                // 원리금균등
                case "EQUAL" -> {
                    paidPrincipal =
                            fixedPayment.subtract(interest);
                }

                // 원금균등
                case "PRINCIPAL" -> {
                    paidPrincipal = equalPrincipal;
                }

                // 만기일시
                case "BULLET" -> {
                    paidPrincipal = (i == months)
                            ? balance
                            : BigDecimal.ZERO;
                }

                default -> throw new IllegalStateException(
                        "지원하지 않는 상환 방식입니다.");
            }

            // 마지막 회차 원금 전액 정산
            if (i == months) {
                paidPrincipal = balance;
            } else {

                if ("EQUAL".equals(method)
                        && paidPrincipal.signum() <= 0
                        && balance.signum() > 0) {
                    throw new IllegalStateException(
                            "월 납입액이 이자를 초과하지 않습니다.");
                }

                if (paidPrincipal.signum() < 0) {
                    paidPrincipal = BigDecimal.ZERO;
                }

                if (paidPrincipal.compareTo(balance) > 0) {
                    paidPrincipal = balance;
                }
            }

            // 잔여 원금 감소
            balance = balance.subtract(paidPrincipal);

            // 납부 예정일
            LocalDate dueDate =
                    startDate.plusMonths(i);

            // 마지막 회차는 만기일
            if (i == months) {
                dueDate = loanAccount.getMaturityDate();
            }

            // 상환 일정 Entity 생성
            LoanRepaymentSchedule schedule =
                    LoanRepaymentSchedule.builder()
                            .loanAccount(loanAccount)
                            .installmentNo(i)
                            .dueDate(dueDate)
                            .principalAmount(paidPrincipal)
                            .interestAmount(interest)
                            .remainingBalance(balance)
                            .build();

            result.add(schedule);
        }

        // 최종 잔액 검증
        if (balance.signum() != 0) {
            throw new IllegalStateException(
                    "상환 일정 계산 후 원금 잔액이 남아 있습니다.");
        }

        return result;
    }
}
