package org.doit.onbank.admin.service;

import lombok.RequiredArgsConstructor;
import org.doit.onbank.account.entity.AccountStatus;
import org.doit.onbank.account.entity.TransactionType;
import org.doit.onbank.admin.dto.DailyTransactionStat;
import org.doit.onbank.admin.dto.DashboardSummary;
import org.doit.onbank.admin.dto.TrendBar;
import org.doit.onbank.admin.repository.DashboardRepository;
import org.doit.onbank.loan.entity.LoanAccountStatus;
import org.doit.onbank.member.entity.MemberStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// DSH-001 요약 지표, DSH-002 거래량 추이 (S-04)
// 전체 회원은 탈퇴 제외, 대출 잔액은 완납(CLOSED)만 제외
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminDashboardService {

    // 입금 + ATM 출금 + 이체. 이체는 받은 쪽(TRANSFER_IN)까지 세면 1건이 2건이 되므로 보낸 쪽만
    // 요약 카드와 차트가 같이 쓴다 (카드의 오늘 숫자 = 차트의 오늘 막대)
    private static final List<String> COUNTED_TYPES = List.of(
            TransactionType.DEPOSIT, TransactionType.WITHDRAWAL, TransactionType.TRANSFER_OUT);

    private final DashboardRepository dashboardRepository;

    // 오늘 = 오늘 00:00 이상 ~ 내일 00:00 미만. 합계가 null(0건)이면 0
    public DashboardSummary summary() {
        LocalDateTime from = LocalDate.now().atStartOfDay();
        LocalDateTime to = from.plusDays(1);

        Long amount = dashboardRepository.sumTransactionAmount(COUNTED_TYPES, from, to);
        BigDecimal loanBalance = dashboardRepository.sumLoanBalanceExcept(LoanAccountStatus.CLOSED);

        return new DashboardSummary(
                dashboardRepository.countMembersExcept(MemberStatus.WITHDRAWN),
                dashboardRepository.countMembers(MemberStatus.SUSPENDED),
                dashboardRepository.countTransactions(COUNTED_TYPES, from, to),
                amount == null ? 0L : amount,
                dashboardRepository.countAccounts(AccountStatus.FROZEN),
                loanBalance == null ? BigDecimal.ZERO : loanBalance);
    }

    // 7일·30일만 받는다. 그 밖의 값은 7일. 오늘 포함 (7일 = 6일 전 ~ 오늘)
    public static int normalizeRange(int range) {
        return range == 30 ? 30 : 7;
    }

    // 날짜를 하루씩 돌면서 DB 에 없는 날은 0 으로 채운다 → 막대 개수 = 기간 일수
    public List<TrendBar> trend(int range) {
        LocalDate today = LocalDate.now();
        LocalDate first = today.minusDays(normalizeRange(range) - 1);

        Map<LocalDate, DailyTransactionStat> byDay = dashboardRepository
                .dailyTransactions(COUNTED_TYPES, first.atStartOfDay(), today.plusDays(1).atStartOfDay())
                .stream()
                .collect(Collectors.toMap(DailyTransactionStat::getDay, s -> s));

        long max = byDay.values().stream().mapToLong(DailyTransactionStat::getCount).max().orElse(0);

        List<TrendBar> bars = new ArrayList<>();
        for (LocalDate day = first; !day.isAfter(today); day = day.plusDays(1)) {
            DailyTransactionStat stat = byDay.get(day);
            long count = (stat == null) ? 0 : stat.getCount();
            long amount = (stat == null) ? 0 : stat.getAmount();
            int height = (max == 0) ? 0 : (int) Math.round(count * 100.0 / max);
            bars.add(new TrendBar(day, count, amount, height));
        }
        return bars;
    }
}
