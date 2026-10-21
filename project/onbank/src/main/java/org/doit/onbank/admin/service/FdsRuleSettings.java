package org.doit.onbank.admin.service;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

// FDS 판정 규칙, 기준값, 점수
@Component
@Getter
public class FdsRuleSettings {

    // 합계가 detect 이상이면 탐지(저장+알림), caution 이상이면 주의
    @Value("${onbank.fds.detect-score:60}")
    private int detectScore;
    @Value("${onbank.fds.caution-score:30}")
    private int cautionScore;

    // A. OTP 재발급 직후 (첫 등록은 제외)
    @Value("${onbank.fds.otp-reissued.hours:24}")
    private int otpReissuedHours;
    @Value("${onbank.fds.otp-reissued.score:25}")
    private int otpReissuedScore;

    // A. 고령자 (이체일 기준 만 나이)
    @Value("${onbank.fds.elderly.age:65}")
    private int elderlyAge;
    @Value("${onbank.fds.elderly.score:15}")
    private int elderlyScore;

    // B. 1회 고액
    @Value("${onbank.fds.high-once.amount:10000000}")
    private long highOnceAmount;
    @Value("${onbank.fds.high-once.score:15}")
    private int highOnceScore;

    // B. 1일 고액 (회원의 모든 계좌 합산)
    @Value("${onbank.fds.high-daily.amount:30000000}")
    private long highDailyAmount;
    @Value("${onbank.fds.high-daily.score:15}")
    private int highDailyScore;

    // B. 평소보다 큰 금액 (최근 days일 1회 최대 금액의 multiplier배 이상, 기록 없으면 건너뜀)
    @Value("${onbank.fds.above-usual.days:90}")
    private int aboveUsualDays;
    @Value("${onbank.fds.above-usual.multiplier:2}")
    private int aboveUsualMultiplier;
    @Value("${onbank.fds.above-usual.score:15}")
    private int aboveUsualScore;

    // B. 짧은 시간 연속 거래 (minutes분 안 count회 이상)
    @Value("${onbank.fds.rapid-count.minutes:10}")
    private int rapidCountMinutes;
    @Value("${onbank.fds.rapid-count.count:3}")
    private int rapidCountCount;
    @Value("${onbank.fds.rapid-count.score:25}")
    private int rapidCountScore;

    // B. 처음 보내는 계좌 (이체만)
    @Value("${onbank.fds.new-recipient.score:10}")
    private int newRecipientScore;

    // B. 큰 입금 직후 출금 (amount 이상 입금 후 minutes분 안)
    @Value("${onbank.fds.in-then-out.amount:10000000}")
    private long inThenOutAmount;
    @Value("${onbank.fds.in-then-out.minutes:10}")
    private int inThenOutMinutes;
    @Value("${onbank.fds.in-then-out.score:40}")
    private int inThenOutScore;

    // B. 장기 미사용 후 출금 (직전 거래 후 days일 이상)
    @Value("${onbank.fds.dormant-wake.days:365}")
    private int dormantWakeDays;
    @Value("${onbank.fds.dormant-wake.score:20}")
    private int dormantWakeScore;



} // class
