package org.doit.onbank.savings.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.doit.onbank.savings.entity.SavingsAccount;
import org.doit.onbank.savings.entity.SavingsPayment;
import org.doit.onbank.savings.entity.SavingsType;
import org.springframework.stereotype.Service;

@Service
public class SavingsEarlyTerminationCalculator {

    public long calculateInterest(
            SavingsAccount account,
            BigDecimal earlyRate,
            LocalDate closeDate) {

        if (account.getProduct().getType()
                != SavingsType.FIXED_DEPOSIT) {
            throw new IllegalArgumentException(
                "정기예금만 계산할 수 있습니다.");
        }

        if (closeDate.isBefore(account.getOpenedOn())
                || !closeDate.isBefore(account.getMaturityOn())) {
            throw new IllegalArgumentException(
                "중도해지 기간이 아닙니다.");
        }

        if (earlyRate == null
                || earlyRate.signum() < 0) {
            throw new IllegalArgumentException(
                "중도해지이율이 올바르지 않습니다.");
        }

        long days = ChronoUnit.DAYS.between(
                account.getOpenedOn(),
                closeDate);

        BigDecimal interest =
                BigDecimal.valueOf(account.getPrincipal())
                    .multiply(earlyRate)
                    .divide(BigDecimal.valueOf(100),
                            12, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(days))
                    .divide(BigDecimal.valueOf(365),
                            0, RoundingMode.DOWN);

        return interest.longValueExact();
    }
    
    public long calculateInterest(
            SavingsAccount account,
            List<SavingsPayment> payments,
            BigDecimal earlyRate,
            LocalDate closeDate) {

        if (account == null
                || payments == null
                || earlyRate == null
                || closeDate == null
                || earlyRate.signum() < 0
                || closeDate.isBefore(account.getOpenedOn())
                || !closeDate.isBefore(account.getMaturityOn())) {

            throw new IllegalArgumentException(
                    "중도해지 이자 계산 정보가 올바르지 않습니다."
            );
        }

        BigDecimal grossInterest = BigDecimal.ZERO;

        if (account.getProduct().getType()
                == SavingsType.FIXED_DEPOSIT) {

            // 정기예금: 전체 원금의 예치 기간 계산
            long days = ChronoUnit.DAYS.between(
                    account.getOpenedOn(),
                    closeDate
            );

            grossInterest = calculate(
                    account.getPrincipal(),
                    earlyRate,
                    days
            );

        } else if (account.getProduct().getType()
                == SavingsType.INSTALLMENT_SAVINGS) {

            // 정기적금: 실제 납입 건별 이자 합산
            for (SavingsPayment payment : payments) {

                long days = ChronoUnit.DAYS.between(
                        payment.getPaidOn(),
                        closeDate
                );

                if (days < 0) {
                    throw new IllegalStateException(
                            "납입일이 해지일보다 늦습니다."
                    );
                }

                grossInterest = grossInterest.add(
                        calculate(
                                payment.getAmount(),
                                earlyRate,
                                days
                        )
                );
            }

        } else {
            throw new IllegalArgumentException(
                    "지원하지 않는 예적금 상품 유형입니다."
            );
        }

        return grossInterest
                .setScale(0, RoundingMode.DOWN)
                .longValueExact();
    }

    private BigDecimal calculate(
            long principal,
            BigDecimal annualRate,
            long days) {

        return BigDecimal.valueOf(principal)
                .multiply(annualRate)
                .multiply(BigDecimal.valueOf(days))
                .divide(
                        BigDecimal.valueOf(36500),
                        12,
                        RoundingMode.DOWN
                );
    }
}
