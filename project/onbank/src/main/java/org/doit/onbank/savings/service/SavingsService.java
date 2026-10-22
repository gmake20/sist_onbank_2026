package org.doit.onbank.savings.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.doit.onbank.account.entity.AccountType;
import org.doit.onbank.account.service.AccountNumberGenerator;
import org.doit.onbank.savings.entity.SavingsAccount;
import org.doit.onbank.savings.entity.SavingsCashFlow;
import org.doit.onbank.savings.entity.SavingsPayment;
import org.doit.onbank.savings.entity.SavingsProduct;
import org.doit.onbank.savings.entity.SavingsStatus;
import org.doit.onbank.savings.entity.SavingsType;
import org.doit.onbank.savings.repository.SavingsAccountRepository;
import org.doit.onbank.savings.repository.SavingsCashFlowRepository;
import org.doit.onbank.savings.repository.SavingsPaymentRepository;
import org.doit.onbank.savings.repository.SavingsProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SavingsService {

	private final SavingsProductRepository products;
	private final SavingsAccountRepository accounts;
	private final SavingsPaymentRepository payments;
	private final SavingsCashFlowRepository flows;
	private final SavingsMoneyPort money;
	private final SavingsInterestCalculator interest;
	private final AccountNumberGenerator generator;

	private final SavingsEarlyTerminationCalculator earlyCalculator;

	@Transactional(readOnly = true)
	public List<SavingsProduct> products() {

		return products.findByActiveTrueOrderByIdDesc();
	}

	@Transactional(readOnly = true)
	public List<SavingsAccount> mine(Long memberId) {

		return accounts.findByMemberIdOrderByIdDesc(memberId);
	}

	@Transactional(readOnly = true)
	public SavingsAccount detail(Long id, Long memberId) {

		return accounts.findByIdAndMemberId(id, memberId)
				.orElseThrow(() ->
				new IllegalArgumentException(
						"가입 내역이 없습니다."
						)
						);
	}

	@Transactional(readOnly = true)
	public List<SavingsPayment> history(Long id, Long memberId) {

		detail(id, memberId);

		return payments.findByAccount_IdOrderByInstallmentNoAsc(id);
	}

	@Transactional
	public Long enroll(
			Long productId,
			Long memberId,
			Long sourceAccountId,
			long amount) {

		if (memberId == null || sourceAccountId == null) {
			throw new IllegalArgumentException(
					"계좌 정보 오류"
					);
		}

		SavingsProduct product = products.findById(productId)
				.orElseThrow(() ->
				new IllegalArgumentException(
						"상품이 없습니다."
						)
						);

		if (!product.isActive()
				|| amount < product.getMinAmount()
				|| amount > product.getMaxAmount()) {

			throw new IllegalArgumentException(
					"가입 금액 또는 상품 상태를 확인하세요."
					);
		}

		LocalDate today = LocalDate.now();

		money.debit(sourceAccountId, memberId, amount);
		
		String savingsAccountNumber =
		        generator.generate(AccountType.SAVINGS);



		SavingsAccount account = accounts.saveAndFlush(
				new SavingsAccount(
						product,
						memberId,
						sourceAccountId,
						amount,
						savingsAccountNumber,
						today
						)
				);
		
		// DB 저장
		account = accounts.saveAndFlush(account);

		payments.save(
				new SavingsPayment(
						account,
						1,
						amount,
						today
						)
				);

		flows.save(
				new SavingsCashFlow(
						account.getId(),
						"ENROLL-" + account.getId(),
						"ENROLL",
						amount
						)
				);

		return account.getId();
	}

	/**
	 * 월 정액 적금: 다음 회차 1건 납입.
	 * 가입일 기준 해당 회차 월이 되기 전 조기 납입 제한
	 */
	@Transactional
	public void pay(Long savingsId, Long memberId) {

		SavingsAccount account = accounts.lockById(savingsId)
				.orElseThrow(() ->
				new IllegalArgumentException(
						"가입 계좌가 없습니다."
						)
						);

		requireOwner(account, memberId);

		if (account.getProduct().getType() != SavingsType.INSTALLMENT_SAVINGS
				|| account.getStatus() != SavingsStatus.ACTIVE) {

			throw new IllegalStateException(
					"적금 납입이 불가능합니다."
					);
		}

		LocalDate today = LocalDate.now();

		if (!today.isBefore(account.getMaturityOn())) {
			throw new IllegalStateException(
					"만기가 지난 적금은 납입할 수 없습니다."
					);
		}

		List<SavingsPayment> all =
				payments.findByAccount_IdOrderByInstallmentNoAsc(savingsId);

		int next = all.size() + 1;

		if (next > account.getProduct().getTermMonths()) {
			throw new IllegalStateException(
					"모든 회차가 납입되었습니다."
					);
		}

		LocalDate earliest = account.getOpenedOn().plusMonths(next - 1);

		if (today.isBefore(earliest)) {
			throw new IllegalStateException(
					"다음 회차 납입일 전입니다: " + earliest
					);
		}

		long amount = account.getMonthlyAmount();

		money.debit(
				account.getSourceAccountId(),
				memberId,
				amount
				);

		account.addPayment(amount, LocalDateTime.now());

		payments.save(
				new SavingsPayment(
						account,
						next,
						amount,
						today
						)
				);

		flows.save(
				new SavingsCashFlow(
						savingsId,
						"PAY-" + savingsId + "-" + next,
						"PAYMENT",
						amount
						)
				);
	}

	@Transactional
	public long closeAtMaturity(Long savingsId, Long memberId) {

		SavingsAccount account = accounts.lockById(savingsId)
				.orElseThrow(() ->
				new IllegalArgumentException(
						"가입 계좌가 없습니다."
						)
						);

		requireOwner(account, memberId);

		LocalDate today = LocalDate.now();

		if (account.getStatus() != SavingsStatus.ACTIVE
				|| today.isBefore(account.getMaturityOn())) {

			throw new IllegalStateException(
					"만기 이후 정상 계좌만 해지할 수 있습니다."
					);
		}

		List<SavingsPayment> all =
				payments.findByAccount_IdOrderByInstallmentNoAsc(savingsId);

		long gross = interest.grossInterest(account, all);

		long tax = interest.estimatedTax(gross);

		long payout = Math.addExact(
				account.getPrincipal(),
				Math.subtractExact(gross, tax)
				);

		money.credit(
				account.getSourceAccountId(),
				memberId,
				payout
				);

		account.close(today);

		flows.save(
				new SavingsCashFlow(
						savingsId,
						"MATURITY-" + savingsId,
						"MATURITY",
						payout
						)
				);

		return payout;
	}


	@Transactional
	public long closeEarly(Long savingsId, Long memberId) {

		// 1. 예적금 계좌 잠금 조회
		SavingsAccount account = accounts.lockById(savingsId)
				.orElseThrow(() ->
				new IllegalArgumentException(
						"가입 계좌가 없습니다."
						)
						);

		// 2. 본인 계좌 확인
		requireOwner(account, memberId);

		LocalDate today = LocalDate.now();

		// 3. 중도해지 가능 여부
		if (account.getStatus() != SavingsStatus.ACTIVE
				|| !today.isBefore(account.getMaturityOn())
				|| today.isBefore(account.getOpenedOn())) {

			throw new IllegalStateException(
					"중도해지가 불가능한 계좌입니다."
					);
		}

		// 4. 중도해지이율 조회
		BigDecimal earlyRate =
				account.getProduct().getEarlyTerminationRate();

		if (earlyRate == null || earlyRate.signum() < 0) {
			throw new IllegalStateException(
					"중도해지이율이 설정되지 않았습니다."
					);
		}

		// 5. 납입 내역 조회
		List<SavingsPayment> all =
				payments.findByAccount_IdOrderByInstallmentNoAsc(savingsId);

		// 6. 중도해지 세전 이자
		long gross = earlyCalculator.calculateInterest(
				account, all, earlyRate, today
				);

		// 7. 예상 세금
		long tax = interest.estimatedTax(gross);

		// 8. 최종 입금액
		long payout = Math.addExact(
				account.getPrincipal(),
				Math.subtractExact(gross, tax)
				);

		// 9. 입출금계좌에 원리금 입금
		money.credit(
		        account.getSourceAccountId(),
		        memberId,
		        payout,
		        "ONBANK 예적금 중도해지 지급"
		);

		// 10. 예적금 계좌 중도해지
		account.closeEarly(today);

		// 11. 해지 지급 기록
		flows.save(
				new SavingsCashFlow(
						savingsId,
						"EARLY-CLOSE-" + savingsId,
						"EARLY_CLOSE",
						payout
						)
				);

		return payout;
	}


	private void requireOwner(
			SavingsAccount account,
			Long memberId) {

		if (memberId == null
				|| !memberId.equals(account.getMemberId())) {

			throw new IllegalArgumentException(
					"본인의 가입 계좌만 이용할 수 있습니다."
					);
		}
	}
}