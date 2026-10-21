package org.doit.onbank.savings.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.doit.onbank.savings.entity.SavingsAccount;
import org.doit.onbank.savings.entity.SavingsPayment;
import org.doit.onbank.savings.entity.SavingsType;
import org.springframework.stereotype.Component;

@Component
public class SavingsEarlyCloseCalculator {
	
	public record Quote(long principal, long grossInterest, long tax,
			long netInterest, long payout, BigDecimal annualRate,
			LocalDate closeDate) {}

	public Quote quote(SavingsAccount account, List<SavingsPayment> payments,
			BigDecimal earlyAnnualRate, LocalDate closeDate) {

		if (account == null || closeDate == null ||
				closeDate.isBefore(account.getOpenedOn()) ||
				!closeDate.isBefore(account.getMaturityOn())) {

			throw new IllegalStateException("중도해지 가능한 날짜가 아닙니다.");
		}

		if (earlyAnnualRate == null || earlyAnnualRate.signum() < 0 ||
				earlyAnnualRate.compareTo(new BigDecimal("100")) > 0) {
			throw new IllegalArgumentException("중도해지이율이 올바르지 않습니다.");
		}
		
		BigDecimal interest = BigDecimal.ZERO;

		if (account.getProduct().getType() == SavingsType.FIXED_DEPOSIT) {
			interest = periodInterest(account.getPrincipal(), earlyAnnualRate,
					account.getOpenedOn(), closeDate);
		} else if (account.getProduct().getType() == SavingsType.INSTALLMENT_SAVINGS) {

			long sumPaid = 0;
			
			for (SavingsPayment payment : payments) {
				if (payment.getPaidOn().isAfter(closeDate)) {
					throw new IllegalStateException("해지일 이후 납입 기록이 존재합니다.");
				}
				sumPaid = Math.addExact(sumPaid, payment.getAmount());
				interest = interest.add(periodInterest(payment.getAmount(),
						earlyAnnualRate, payment.getPaidOn(), closeDate));
			}
			if (sumPaid != account.getPrincipal()) {
				throw new IllegalStateException("적금 원금과 납입 내역이 일치하지 않습니다. 첫 회차 기록을 확인하세요.");
			}
		} else {
			throw new IllegalArgumentException("지원하지 않는 예적금 상품입니다.");
		}

		long gross = interest.setScale(0, RoundingMode.DOWN).longValueExact();
		
		long tax = BigDecimal.valueOf(gross).multiply(new BigDecimal("0.154"))
				.setScale(0, RoundingMode.DOWN).longValueExact();

		long net = Math.subtractExact(gross, tax);
		
		return new Quote(account.getPrincipal(), gross, tax, net,
				Math.addExact(account.getPrincipal(), net), earlyAnnualRate, closeDate);
	}

	private BigDecimal periodInterest(long amount, BigDecimal annualRate,
			LocalDate start, LocalDate end) {
		
		long days = ChronoUnit.DAYS.between(start, end);

		return BigDecimal.valueOf(amount).multiply(annualRate)
				.multiply(BigDecimal.valueOf(days))
				.divide(new BigDecimal("36500"), 12, RoundingMode.HALF_UP);
	}
}
