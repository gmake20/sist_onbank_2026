package org.doit.onbank.admin.aop;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.doit.onbank.account.dto.TransferReceipt;
import org.doit.onbank.account.entity.TransactionHistory;
import org.doit.onbank.admin.service.FdsDetectionService;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

// FDS-001 이상거래 탐지 연결 (FDS-탐지규칙.md 1장)
// 팀원2 즉시이체 transfer()·ATM 출금 withdraw() 가 정상으로 끝난 뒤 FdsDetectionService 를 부른다
// 팀원2 파일은 고치지 않는다. 예약이체는 transferPreAuthorized() 로 실행돼서 여기 안 걸린다 (제외 조건과 같음)
@Slf4j
@Aspect
@Component
// 트랜잭션(@Transactional, 기본 LOWEST_PRECEDENCE) 바로 바깥에 둔다 → 팀원2 트랜잭션이 커밋된 뒤에 실행된다
// 안쪽에 들어가면 FDS 오류가 이체 트랜잭션을 rollback-only 로 만들어 이체까지 취소된다
// HIGHEST_PRECEDENCE 는 안 됨 : ExposeInvocationInterceptor(HIGHEST+1)보다 먼저 돌아 args() 바인딩이 실패한다
@Order(Ordered.LOWEST_PRECEDENCE - 1)
@RequiredArgsConstructor
public class FdsTransactionAspect {

    private final FdsDetectionService fdsDetectionService;

    // 즉시이체 (TRANSFER_OUT)
    @AfterReturning(
            pointcut = "execution(* org.doit.onbank.account.service.AccountTransferService.transfer(..)) && args(memberId, ..)",
            returning = "receipt")
    public void afterTransfer(Long memberId, TransferReceipt receipt) {
        warnIfInsideTransaction("이체");
        try {
            fdsDetectionService.onTransfer(memberId, receipt);
        } catch (RuntimeException e) {
            // 이체는 이미 커밋됨. FDS 실패로 고객 화면에 오류를 보이지 않는다
            log.error("FDS 판정 실패(이체) memberId={} sqno={}", memberId, receipt.transactionSqno(), e);
        }
    }

    // ATM 출금 (WITHDRAWAL)
    @AfterReturning(
            pointcut = "execution(* org.doit.onbank.account.service.AccountService.withdraw(..)) && args(memberId, ..)",
            returning = "history")
    public void afterWithdrawal(Long memberId, TransactionHistory history) {
        warnIfInsideTransaction("ATM 출금");
        try {
            fdsDetectionService.onWithdrawal(memberId, history);
        } catch (RuntimeException e) {
            log.error("FDS 판정 실패(ATM 출금) memberId={} historyId={}", memberId, history.getTransactionHistoryId(), e);
        }
    }

    // 커밋 뒤에 도는지 앱 실행 때 확인용. 트랜잭션 안이면 순서가 틀린 것
    private void warnIfInsideTransaction(String type) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            log.warn("FDS Aspect가 {} 트랜잭션 안에서 실행됨 — @Order 확인 필요", type);
        }
    }
}
