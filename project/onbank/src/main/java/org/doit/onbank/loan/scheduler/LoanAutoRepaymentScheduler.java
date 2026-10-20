package org.doit.onbank.loan.scheduler;

import java.time.LocalDate;
import java.time.ZoneId;

import org.doit.onbank.loan.repository.LoanAutoRepaymentRepository;
import org.doit.onbank.loan.service.LoanAutoRepaymentExecutionService;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class LoanAutoRepaymentScheduler {

    private final LoanAutoRepaymentRepository autoRepository;
    private final LoanAutoRepaymentExecutionService executionService;

    // 매일 오전 9시
    @Scheduled(cron = "0 0 9 * * *", zone = "Asia/Seoul")
    public void processAutoRepayments() {

        LocalDate today =
                LocalDate.now(ZoneId.of("Asia/Seoul"));

        var settings = autoRepository.findByEnabledTrue();

        log.info("대출 자동상환 점검 시작 - {}", today);

        for (var setting : settings) {
            try {
                executionService.execute(
                        setting.getId(),
                        today
                );

            } catch (Exception e) {
                log.error(
                    "대출 자동상환 실패. 설정 ID={}",
                    setting.getId(),
                    e
                );
            }
        }
    }
}
