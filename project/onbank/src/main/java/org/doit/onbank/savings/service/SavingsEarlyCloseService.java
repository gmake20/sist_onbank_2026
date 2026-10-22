package org.doit.onbank.savings.service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.doit.onbank.savings.entity.SavingsAccount;
import org.doit.onbank.savings.entity.SavingsEarlyCloseSettlement;
import org.doit.onbank.savings.entity.SavingsPayment;
import org.doit.onbank.savings.entity.SavingsStatus;
import org.doit.onbank.savings.repository.SavingsEarlyCloseSettlementRepository;
import org.doit.onbank.savings.repository.SavingsPaymentRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SavingsEarlyCloseService {
	
    @PersistenceContext
    private EntityManager em;
    
    private final SavingsPaymentRepository paymentRepository;
    private final SavingsEarlyCloseCalculator calculator;
    private final SavingsMoneyPort moneyPort;
    private final SavingsEarlyCloseSettlementRepository settlementRepository;
    private final SavingsAutoTransferStopPort autoTransferStopPort;

    @Transactional(readOnly = true)
    public SavingsEarlyCloseCalculator.Quote preview(Long id, Long memberId) {
        
    	SavingsAccount a = requireOwned(id, memberId, false);
    	
        return makeQuote(a, LocalDate.now(ZoneId.of("Asia/Seoul")));
    }

    @Transactional
    public SavingsEarlyCloseCalculator.Quote execute(Long id, Long memberId) {
        
    	SavingsAccount a = requireOwned(id, memberId, true);
        
    	if (a.getStatus() != SavingsStatus.ACTIVE) {
            throw new IllegalStateException("이미 해지되었거나 이용할 수 없는 계좌입니다.");
        }
        
    	if (settlementRepository.existsByAccount_Id(id)) {
            throw new IllegalStateException("이미 처리된 중도해지입니다.");
        }
        SavingsEarlyCloseCalculator.Quote quote =
                makeQuote(a, LocalDate.now(ZoneId.of("Asia/Seoul")));
        // 같은 트랜잭션에서 입금과 거래내역 저장이 이뤄져야 함.
        
        moneyPort.credit(a.getSourceAccountId(), memberId, quote.payout());
        
        a.closeEarly(quote.closeDate());
        
        autoTransferStopPort.stopBySavingsAccountId(id);
        
        settlementRepository.save(new SavingsEarlyCloseSettlement(
                a, quote.principal(), quote.grossInterest(), quote.tax(),
                quote.payout(), quote.annualRate(), quote.closeDate()));
        return quote;
    }

    private SavingsEarlyCloseCalculator.Quote makeQuote(SavingsAccount account, LocalDate date) {
    	
        if (account.getStatus() != SavingsStatus.ACTIVE) {
            throw new IllegalStateException("활성 계좌만 중도해지할 수 있습니다.");
        }
        
        List<SavingsPayment> payments = paymentRepository.findByAccount_Id(account.getId());
        
        return calculator.quote(account, payments,
                account.getProduct().getEarlyTerminationRate(), date);
    }

    private SavingsAccount requireOwned(Long id, Long memberId, boolean lock) {
    	
        if (id == null || memberId == null) throw new AccessDeniedException("로그인이 필요합니다.");
        
        SavingsAccount a = lock
                ? em.find(SavingsAccount.class, id, LockModeType.PESSIMISTIC_WRITE)
                : em.find(SavingsAccount.class, id);
        
        if (a == null || !memberId.equals(a.getMemberId())) {
            throw new AccessDeniedException("본인 예적금 계좌만 조회할 수 있습니다.");
        }
        
        return a;
    }
}
