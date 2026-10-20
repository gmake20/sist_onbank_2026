
package org.doit.onbank.savings.service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.doit.onbank.savings.entity.SavingsAccount;
import org.doit.onbank.savings.entity.SavingsPayment;
import org.doit.onbank.savings.entity.SavingsType;
import org.doit.onbank.savings.repository.SavingsPaymentRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SavingsScheduleViewService {

    private final SavingsPaymentRepository paymentRepository;

    // 회차별 화면 데이터
    public record ScheduleRow(
            int installmentNo,
            LocalDate dueDate,
            long amount,
            LocalDate paidOn,
            String status
    ) {}

    // 전체 납입 현황
    public record ScheduleSummary(
            int totalCount,
            int paidCount,
            int overdueCount,
            int pendingCount,
            Integer nextInstallmentNo,
            List<ScheduleRow> rows
    ) {}

    @Transactional(readOnly = true)
    public ScheduleSummary buildSummary(SavingsAccount account) {

        if (account == null) {
            return new ScheduleSummary(
                    0, 0, 0, 0, null, List.of());
        }

        // 정기예금은 월별 납입 스케줄 없음
        if (account.getProduct().getType()
                != SavingsType.INSTALLMENT_SAVINGS) {

            return new ScheduleSummary(
                    0, 0, 0, 0, null, List.of());
        }

        Long accountId = account.getId();

        List<SavingsPayment> payments =
                paymentRepository.findByAccount_Id(accountId);

        Map<Integer, SavingsPayment> paymentMap =
                new HashMap<>();

        for (SavingsPayment payment : payments) {
            paymentMap.put(
                    payment.getInstallmentNo(),
                    payment
            );
        }

        int totalMonths =
                account.getProduct().getTermMonths();

        LocalDate openedOn = account.getOpenedOn();

        LocalDate today =
                LocalDate.now(ZoneId.of("Asia/Seoul"));

        List<ScheduleRow> rows = new ArrayList<>();

        int paidCount = 0;
        int overdueCount = 0;
        int pendingCount = 0;

        Integer nextInstallmentNo = null;

        for (int i = 1; i <= totalMonths; i++) {

            // 가입일을 기준으로 매월 납입 예정일 생성
            YearMonth targetMonth =
                    YearMonth.from(openedOn).plusMonths(i - 1);

            int day = Math.min(
                    openedOn.getDayOfMonth(),
                    targetMonth.lengthOfMonth()
            );

            LocalDate dueDate =
                    targetMonth.atDay(day);

            SavingsPayment payment = paymentMap.get(i);

            String status;
            LocalDate paidOn = null;

            if (payment != null) {

                paidOn = payment.getPaidOn();

                if (paidOn.isAfter(dueDate)) {
                    status = "LATE_PAID";
                } else {
                    status = "PAID";
                }

                paidCount++;

            } else if (dueDate.isBefore(today)) {

                status = "OVERDUE";
                overdueCount++;

            } else {

                status = "PENDING";
                pendingCount++;
            }

            // 가장 오래된 미납 회차
            if (payment == null && nextInstallmentNo == null) {
                nextInstallmentNo = i;
            }

            rows.add(new ScheduleRow(
                    i,
                    dueDate,
                    account.getMonthlyAmount(),
                    paidOn,
                    status
            ));
        }

        return new ScheduleSummary(
                totalMonths,
                paidCount,
                overdueCount,
                pendingCount,
                nextInstallmentNo,
                rows
        );
    }
}
