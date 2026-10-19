
package org.doit.onbank.loan.service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.doit.onbank.loan.entity.LoanAccount;
import org.doit.onbank.loan.entity.LoanApplication;
import org.doit.onbank.loan.entity.LoanRepaymentSchedule;
import org.doit.onbank.loan.repository.LoanRepaymentScheduleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class LoanRepaymentScheduleService {

    private final LoanRepaymentScheduleRepository repository;

    private static final MathContext MC = MathContext.DECIMAL128;
    private static final RoundingMode ROUND = RoundingMode.HALF_UP;

    @Transactional(propagation = Propagation.MANDATORY)
    public void createSchedule(
            LoanAccount loanAccount,
            LoanApplication application) {

        if (loanAccount == null || application == null
                || loanAccount.getLoanAccountId() == null) {
            throw new IllegalArgumentException(
                    "대출 정보가 올바르지 않습니다.");
        }

        if (!loanAccount.getLoanApplication()
                .getId()
                .equals(application.getId())) {
            throw new IllegalStateException(
                    "대출 계좌와 신청 정보가 일치하지 않습니다.");
        }

        Long loanAccountId = loanAccount.getLoanAccountId();

        if (repository.existsByLoanAccount_LoanAccountId(loanAccountId)) {
            throw new IllegalStateException(
                    "이미 상환 스케줄이 생성되었습니다.");
        }

        BigDecimal principal = loanAccount.getPrincipalAmount();
        BigDecimal annualRate = loanAccount.getInterestRate();
        Integer months = application.getRequestedMonths();
        String method = loanAccount.getRepaymentMethod();

        if (principal == null || principal.signum() <= 0
                || principal.scale() > 0) {
            throw new IllegalArgumentException("대출 원금 오류");
        }

        if (annualRate == null || annualRate.signum() < 0
                || annualRate.compareTo(new BigDecimal("100")) > 0) {
            throw new IllegalArgumentException("대출 금리 오류");
        }

        if (months == null || months < 1 || months > 600) {
            throw new IllegalArgumentException("대출 기간 오류");
        }

        if (!List.of("EQUAL", "PRINCIPAL", "BULLET")
                .contains(method)) {
            throw new IllegalArgumentException("상환 방식 오류");
        }

        BigDecimal monthlyRate = annualRate.divide(
                new BigDecimal("1200"), MC);

        BigDecimal remaining = principal;

        BigDecimal equalPayment = BigDecimal.ZERO;
        BigDecimal equalPrincipal = BigDecimal.ZERO;

        if ("EQUAL".equals(method)) {
            if (monthlyRate.signum() == 0) {
                equalPayment = principal.divide(
                        BigDecimal.valueOf(months), 0, ROUND);
            } else {
                BigDecimal factor = BigDecimal.ONE
                        .add(monthlyRate)
                        .pow(months, MC);

                equalPayment = principal
                        .multiply(monthlyRate, MC)
                        .multiply(factor, MC)
                        .divide(
                            factor.subtract(BigDecimal.ONE, MC),
                            0, ROUND);
            }
        }

        if ("PRINCIPAL".equals(method)) {
            equalPrincipal = principal.divide(
                    BigDecimal.valueOf(months), 0, ROUND);
        }

        LocalDate startDate =
                loanAccount.getExecutedAt().toLocalDate();

        List<LoanRepaymentSchedule> schedules =
                new ArrayList<>();

        for (int i = 1; i <= months; i++) {

            BigDecimal interest = remaining
                    .multiply(monthlyRate, MC)
                    .setScale(0, ROUND);

            BigDecimal principalPayment;

            switch (method) {
                case "EQUAL":
                    principalPayment = equalPayment.subtract(interest);
                    break;

                case "PRINCIPAL":
                    principalPayment = equalPrincipal;
                    break;

                case "BULLET":
                    principalPayment = BigDecimal.ZERO;
                    break;

                default:
                    throw new IllegalStateException("잘못된 상환 방식");
            }

            // 마지막 회차: 원금 전액 정산
            if (i == months) {
                principalPayment = remaining;
            }

            // 원금이 음수가 되거나 잔액을 넘지 않도록 보정
            if (principalPayment.signum() < 0) {
                principalPayment = BigDecimal.ZERO;
            }

            if (principalPayment.compareTo(remaining) > 0) {
                principalPayment = remaining;
            }

            remaining = remaining.subtract(principalPayment);

            LoanRepaymentSchedule schedule =
                    LoanRepaymentSchedule.builder()
                        .loanAccount(loanAccount)
                        .installmentNo(i)
                        .dueDate(startDate.plusMonths(i))
                        .principalAmount(principalPayment)
                        .interestAmount(interest)
                        .remainingBalance(remaining)
                        .build();

            schedules.add(schedule);
        }

        repository.saveAll(schedules);
    }
}
