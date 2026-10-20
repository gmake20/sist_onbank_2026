package org.doit.onbank.savings.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.doit.onbank.savings.repository.SavingsAutoTransferRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component @RequiredArgsConstructor @Slf4j
public class SavingsAutoTransferScheduler {
    private final SavingsAutoTransferRepository settings;
    private final SavingsAutoTransferService service;

    // 매일 오전 09:00 KST. 당일 실패 시 다음 날 재시도(지정일 이후).
    @Scheduled(cron = "0 0 9 * * *", zone = "Asia/Seoul")
    public void execute() {
        for (Long accountId : settings.findEnabledAccountIds()) {
            try {
                service.runAccount(accountId);
            } catch (Exception ex) {
                log.warn("적금 자동납입 실패 accountId={}: {}", accountId, ex.toString());
                try { service.noteFailure(accountId, ex.getMessage()); }
                catch (Exception loggingEx) { log.error("자동납입 실패 기록 저장 실패 accountId={}", accountId, loggingEx); }
            }
        }
    }
}
