package org.doit.onbank.savings.service;

import java.math.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

import org.doit.onbank.savings.entity.*;
import org.springframework.stereotype.Component;

@Component
public class SavingsInterestCalculator {

    private static final BigDecimal DAYS = BigDecimal.valueOf(36500);

    private BigDecimal interest(
            long principal,
            BigDecimal annualPercent,
            LocalDate start,
            LocalDate end) {

        long days = Math.max(
                0,
                ChronoUnit.DAYS.between(start, end)
        );

        return BigDecimal.valueOf(principal)
                .multiply(annualPercent)
                .multiply(BigDecimal.valueOf(days))
                .divide(DAYS, 0, RoundingMode.DOWN);
    }

    /**
     * 계약 금리·실제 납입일 기반 단리,
     * 연 365일, 이자 원 미만 절사 예시
     */
    public long grossInterest(
            SavingsAccount account,
            List<SavingsPayment> payments) {

        LocalDate end = account.getMaturityOn();

        BigDecimal sum = BigDecimal.ZERO;

        for (SavingsPayment p : payments) {

            sum = sum.add(
                    interest(
                            p.getAmount(),
                            account.getContractRate(),
                            p.getPaidOn(),
                            end
                    )
            );
        }

        return sum.longValueExact();
    }

    /**
     * 일반과세 15.4% 가정.
     * 실서비스의 세율·비과세 우대 조건과 구분 필요
     */
    public long estimatedTax(long gross) {

        return BigDecimal.valueOf(gross)
                .multiply(new BigDecimal("0.154"))
                .setScale(0, RoundingMode.DOWN)
                .longValueExact();
    }
}