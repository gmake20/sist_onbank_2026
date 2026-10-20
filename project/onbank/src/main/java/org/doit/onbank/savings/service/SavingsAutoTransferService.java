package org.doit.onbank.savings.service;

import lombok.RequiredArgsConstructor;
import org.doit.onbank.savings.entity.*;
import org.doit.onbank.savings.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Optional;

@Service @RequiredArgsConstructor
public class SavingsAutoTransferService {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private final SavingsAutoTransferRepository settings;
    private final SavingsAccountRepository accounts;
    private final SavingsService savings;

    @Transactional(readOnly = true)
    public Optional<SavingsAutoTransfer> get(Long id, Long memberId) {
        requireOwnedSavings(id, memberId);
        return settings.findByAccount_Id(id);
    }

    @Transactional
    public void configure(Long id, Long memberId, int day) {
    	
        SavingsAccount a = requireOwnedSavings(id, memberId);
        if (a.getStatus() != SavingsStatus.ACTIVE || a.getProduct().getType() != SavingsType.INSTALLMENT_SAVINGS)
            throw new IllegalStateException("이용 중인 정기적금만 자동납입을 설정할 수 있습니다.");
        
        SavingsAutoTransfer setting = settings.findByAccount_Id(id)
            .orElseGet(() -> new SavingsAutoTransfer(a, day));
        
        setting.configure(day);
        settings.save(setting);
    }

    @Transactional
    public void cancel(Long id, Long memberId) {
        requireOwnedSavings(id, memberId);
        settings.findByAccount_Id(id).ifPresent(SavingsAutoTransfer::disable);
    }

    private SavingsAccount requireOwnedSavings(Long id, Long memberId) {
        if (memberId == null) throw new IllegalArgumentException("로그인이 필요합니다.");
        
        return accounts.findByIdAndMemberId(id, memberId)
            .orElseThrow(() -> new IllegalArgumentException("본인 계좌가 아닙니다."));
    }

    // 한 계좌씩 별도 트랜잭션. 중복 스케줄러 실행 시 설정 행 잠금으로 직렬화.
    @Transactional
    public void runAccount(Long accountId) {
        
    	SavingsAutoTransfer setting = settings.lockByAccountId(accountId).orElse(null);
        
    	if (setting == null || !setting.isEnabled()) return;
        
    	LocalDate now = LocalDate.now(SEOUL);
        SavingsAccount a = setting.getAccount();
        
        if (a.getStatus() != SavingsStatus.ACTIVE || a.getProduct().getType() != SavingsType.INSTALLMENT_SAVINGS || !now.isBefore(a.getMaturityOn())) {
            setting.disable();
            return;
        }
        if (now.getDayOfMonth() < setting.getTransferDay()) return;
        
        if (YearMonth.from(now).toString().equals(setting.getLastSuccessMonth())) return;
        // 예외는 상위 스케줄러로 전파: 출금/납입 트랜잭션을 모두 롤백.
        savings.pay(a.getId(), a.getMemberId());
        setting.markSuccess(now);
    }

    @Transactional
    public void noteFailure(Long accountId, String message) {
        settings.lockByAccountId(accountId).ifPresent(s -> s.markError(message));
    }
}
