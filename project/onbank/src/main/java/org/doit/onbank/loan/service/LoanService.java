
package org.doit.onbank.loan.service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;

import org.doit.onbank.loan.dto.LoanApplyRequest;
import org.doit.onbank.loan.dto.LoanApproveRequest;
import org.doit.onbank.loan.dto.LoanResponse;
import org.doit.onbank.loan.dto.ScheduleResponse;
import org.doit.onbank.loan.entity.Loan;
import org.doit.onbank.loan.entity.LoanRepaymentSchedule;
import org.doit.onbank.loan.repository.LoanRepaymentScheduleRepository;
import org.doit.onbank.loan.repository.LoanRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class LoanService {
    private final LoanRepository loans;
    private final LoanRepaymentScheduleRepository schedules;
    private static final RoundingMode ROUND = RoundingMode.HALF_UP;

    @Transactional
    public LoanResponse apply(long memberId, LoanApplyRequest req) {
        if (!Set.of("EQUAL", "PRINCIPAL", "BULLET").contains(req.repaymentMethod()))
            throw new IllegalArgumentException("지원하지 않는 상환 방식입니다.");
        if (!Set.of("CREDIT", "MORTGAGE", "JEONSE", "AUTO", "OTHER").contains(req.loanType()))
            throw new IllegalArgumentException("지원하지 않는 대출 상품 구분입니다.");
        Loan loan = new Loan();
        loan.setMemberId(memberId);
        loan.setLoanType(req.loanType());
        loan.setRequestedAmount(req.requestedAmount());
        loan.setAnnualInterestRate(req.annualInterestRate());
        loan.setTermMonths(req.termMonths().shortValue());
        loan.setRepaymentMethod(req.repaymentMethod());
        loan.setRepaymentAccountId(req.repaymentAccountId()); // validate account ownership in actual banking integration
        loan.setEmploymentStatus(req.employmentStatus());
        loan.setAnnualIncome(req.annualIncome());
        loan.setStatus("PENDING");
        loan.setAppliedAt(LocalDateTime.now());
        loan.setCreatedAt(LocalDateTime.now());
        loan.setUpdatedAt(LocalDateTime.now());
        return new LoanResponse(loans.save(loan).getLoanId(), loan.getStatus(), "신청이 접수되었습니다.");
    }

    @Transactional
    public LoanResponse approve(long loanId, long reviewerId, LoanApproveRequest req) {
        Loan loan = loans.findByIdForUpdate(loanId).orElseThrow(() -> new NoSuchElementException("대출 신청이 없습니다."));
        if (!"PENDING".equals(loan.getStatus())) throw new IllegalStateException("대기 중인 신청만 승인할 수 있습니다.");
        if (req.approvedAmount() > loan.getRequestedAmount())
            throw new IllegalArgumentException("승인 금액은 신청 금액을 초과할 수 없습니다.");
        if (schedules.existsByLoan_LoanId(loanId)) throw new IllegalStateException("이미 상환 스케줄이 있습니다.");
        loan.setApprovedAmount(req.approvedAmount());
        loan.setReviewedBy(reviewerId);
        loan.setReviewedAt(LocalDateTime.now());
        loan.setStartedAt(req.startedAt());
        loan.setMaturityDate(req.startedAt().plusMonths(loan.getTermMonths()));
        loan.setStatus("APPROVED");
        loan.setUpdatedAt(LocalDateTime.now());
        schedules.saveAll(calculate(loan));
        return new LoanResponse(loanId, loan.getStatus(), "승인 및 상환 일정 생성 완료");
    }

    @Transactional(readOnly = true)
    public List<ScheduleResponse> getSchedule(long loanId, long memberId, boolean isAdmin) {
        Loan loan = loans.findById(loanId).orElseThrow(() -> new NoSuchElementException("대출이 없습니다."));
        if (!isAdmin && !Objects.equals(loan.getMemberId(), memberId))
            throw new org.springframework.security.access.AccessDeniedException("본인 대출만 조회할 수 있습니다.");
        return schedules.findByLoan_LoanIdOrderByInstallmentNo(loanId).stream()
            .map(s -> new ScheduleResponse(s.getInstallmentNo(), s.getDueDate(),
                  s.getPrincipalAmount(), s.getInterestAmount(), s.getTotalAmount(), s.getStatus()))
            .toList();
    }

    private List<LoanRepaymentSchedule> calculate(Loan loan) {
        long principal = loan.getApprovedAmount();
        int months = loan.getTermMonths();
        BigDecimal p = BigDecimal.valueOf(principal);
        BigDecimal r = loan.getAnnualInterestRate().divide(new BigDecimal("1200"), 18, ROUND);
        BigDecimal fixedPayment;
        if (r.signum() == 0) fixedPayment = p.divide(BigDecimal.valueOf(months), 18, ROUND);
        else {
            // 월 상환액 = 원금 * 월이율 * (1+월이율)^n / ((1+월이율)^n - 1)
            BigDecimal factor = BigDecimal.ONE.add(r).pow(months, MathContext.DECIMAL128);
            fixedPayment = p.multiply(r).multiply(factor).divide(factor.subtract(BigDecimal.ONE), 18, ROUND);
        }
        List<LoanRepaymentSchedule> out = new ArrayList<>(months);
        long balance = principal;
        for (int i = 1; i <= months; i++) {
            long interest = BigDecimal.valueOf(balance).multiply(r).setScale(0, ROUND).longValueExact();
            long paidPrincipal = switch (loan.getRepaymentMethod()) {
                case "EQUAL" -> fixedPayment.subtract(BigDecimal.valueOf(interest)).setScale(0, ROUND).longValueExact();
                case "PRINCIPAL" -> p.divide(BigDecimal.valueOf(months), 0, ROUND).longValueExact();
                case "BULLET" -> i == months ? balance : 0L;
                default -> throw new IllegalArgumentException("상환 방식 오류");
            };
            paidPrincipal = Math.min(balance, Math.max(0, paidPrincipal));
            if (i == months) paidPrincipal = balance; // 원 단위 반올림 잔액 보정
            balance -= paidPrincipal;
            LoanRepaymentSchedule s = new LoanRepaymentSchedule();
            s.setLoan(loan); s.setInstallmentNo((short)i);
            s.setDueDate(loan.getStartedAt().plusMonths(i));
            s.setPrincipalAmount(paidPrincipal);
            s.setInterestAmount(interest);
            s.setTotalAmount(Math.addExact(paidPrincipal, interest));
            s.setStatus("SCHEDULED");
            s.setCreatedAt(LocalDateTime.now());s.setUpdatedAt(LocalDateTime.now());
            out.add(s);
        }
        return out;
    }
}