package org.doit.onbank.admin.service;

import lombok.RequiredArgsConstructor;
import org.doit.onbank.account.entity.TransactionType;
import org.doit.onbank.admin.dto.FdsFacts;
import org.doit.onbank.admin.dto.FdsRuleHit;
import org.doit.onbank.admin.entity.FdsRule;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Period;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

// FDS 규칙 9개 판정 (FDS-탐지규칙.md 2장). DB 를 보지 않고 FdsFacts 와 설정값만 비교한다
// 기간(10분·90일 등)으로 자르는 건 조회할 때 이미 했다 → 여기서는 기준값과 비교만
// 걸리지 않으면 null. 합산·대표 규칙은 FdsDetectionService 가 한다
@Component
@RequiredArgsConstructor
public class FdsRuleEvaluator {

    private final FdsRuleSettings settings;

    public List<FdsRuleHit> evaluate(FdsFacts facts) {
        List<FdsRuleHit> hits = new ArrayList<>();
        addIfHit(hits, otpReissued(facts));
        addIfHit(hits, elderly(facts));
        addIfHit(hits, highOnce(facts));
        addIfHit(hits, highDaily(facts));
        addIfHit(hits, aboveUsual(facts));
        addIfHit(hits, rapidCount(facts));
        addIfHit(hits, newRecipient(facts));
        addIfHit(hits, inThenOut(facts));
        addIfHit(hits, dormantWake(facts));
        return hits;
    }

    // A. 이체만. 분실 후 재등록한 지 N시간 안
    FdsRuleHit otpReissued(FdsFacts f) {
        if (!isTransfer(f) || f.otpReactivatedAt() == null) {
            return null;
        }
        long hours = Duration.between(f.otpReactivatedAt(), f.transactionAt()).toHours();
        if (hours < 0 || hours >= settings.getOtpReissuedHours()) {
            return null;
        }
        return hit(FdsRule.OTP_REISSUED, settings.getOtpReissuedScore(), "등록 " + hours + "시간 후");
    }

    // A. 거래일 기준 만 나이
    FdsRuleHit elderly(FdsFacts f) {
        if (f.birthDate() == null) {
            return null;
        }
        int age = Period.between(f.birthDate(), f.transactionAt().toLocalDate()).getYears();
        if (age < settings.getElderlyAge()) {
            return null;
        }
        return hit(FdsRule.ELDERLY, settings.getElderlyScore(), "만 " + age + "세");
    }

    FdsRuleHit highOnce(FdsFacts f) {
        if (f.amount() < settings.getHighOnceAmount()) {
            return null;
        }
        return hit(FdsRule.HIGH_ONCE, settings.getHighOnceScore(), manwon(f.amount()));
    }

    // 회원 모든 계좌 오늘 합계 (이번 거래 포함)
    FdsRuleHit highDaily(FdsFacts f) {
        if (f.memberTodayTotal() < settings.getHighDailyAmount()) {
            return null;
        }
        return hit(FdsRule.HIGH_DAILY, settings.getHighDailyScore(), "오늘 " + manwon(f.memberTodayTotal()));
    }

    // 기록이 없으면 건너뜀 (신규 회원은 "평소"가 없다)
    FdsRuleHit aboveUsual(FdsFacts f) {
        Long usual = f.usualMaxAmount();
        if (usual == null || usual <= 0 || f.amount() < usual * settings.getAboveUsualMultiplier()) {
            return null;
        }
        return hit(FdsRule.ABOVE_USUAL, settings.getAboveUsualScore(), "평소최대 " + manwon(usual));
    }

    // 건수는 이번 거래 포함
    FdsRuleHit rapidCount(FdsFacts f) {
        if (f.recentCount() < settings.getRapidCountCount()) {
            return null;
        }
        return hit(FdsRule.RAPID_COUNT, settings.getRapidCountScore(),
                settings.getRapidCountMinutes() + "분 " + f.recentCount() + "회");
    }

    // 이체만. ATM 출금이면 firstRecipient 가 null
    FdsRuleHit newRecipient(FdsFacts f) {
        if (!isTransfer(f) || !Boolean.TRUE.equals(f.firstRecipient())) {
            return null;
        }
        return hit(FdsRule.NEW_RECIPIENT, settings.getNewRecipientScore(), "첫 이체");
    }

    // 다른 회원에게서 받은 이체만 (조회에서 거름)
    FdsRuleHit inThenOut(FdsFacts f) {
        Long deposit = f.recentLargestDeposit();
        if (deposit == null || deposit < settings.getInThenOutAmount()) {
            return null;
        }
        return hit(FdsRule.IN_THEN_OUT, settings.getInThenOutScore(), "입금 " + manwon(deposit));
    }

    // 첫 거래(직전 거래 없음)는 건너뜀
    FdsRuleHit dormantWake(FdsFacts f) {
        if (f.previousTransactionAt() == null) {
            return null;
        }
        long days = ChronoUnit.DAYS.between(f.previousTransactionAt(), f.transactionAt());
        if (days < settings.getDormantWakeDays()) {
            return null;
        }
        return hit(FdsRule.DORMANT_WAKE, settings.getDormantWakeScore(), days + "일 만");
    }

    private boolean isTransfer(FdsFacts f) {
        return TransactionType.TRANSFER_OUT.equals(f.transactionType());
    }

    private FdsRuleHit hit(FdsRule rule, int score, String basis) {
        return new FdsRuleHit(rule, score, basis);
    }

    private void addIfHit(List<FdsRuleHit> hits, FdsRuleHit hit) {
        if (hit != null) {
            hits.add(hit);
        }
    }

    // 근거 표시용. 만 원 아래는 버린다 (12,345,678 → "1,234만")
    private String manwon(long amount) {
        return String.format("%,d만", amount / 10_000);
    }
}
