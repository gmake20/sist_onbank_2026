package org.doit.onbank.admin.service;

import org.doit.onbank.account.entity.TransactionType;
import org.doit.onbank.admin.dto.FdsFacts;
import org.doit.onbank.admin.dto.FdsRuleHit;
import org.doit.onbank.admin.entity.FdsRule;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// 규칙 9개 경계값 (FDS-탐지규칙.md 2장). DB 없음
// 설정값은 FdsRuleSettings 의 @Value 기본값을 그대로 쓴다 → 기본값이 문서와 같은지도 같이 확인
@SpringJUnitConfig({FdsRuleSettings.class, FdsRuleEvaluator.class})
class FdsRuleEvaluatorTests {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 22, 14, 0);

    @Autowired
    private FdsRuleEvaluator evaluator;

    @Autowired
    private FdsRuleSettings settings;

    @Test
    void settingsUseDocumentDefaults() {
        assertThat(settings.getDetectScore()).isEqualTo(60);
        assertThat(settings.getCautionScore()).isEqualTo(30);
    }

    @Test
    void ordinaryTransferHitsNothing() {
        assertThat(evaluator.evaluate(new Facts().build())).isEmpty();
    }

    @Test
    void otpReissuedWithin24Hours() {
        FdsRuleHit hit = evaluator.otpReissued(new Facts().otpAt(NOW.minusHours(23).minusMinutes(59)).build());

        assertThat(hit.score()).isEqualTo(25);
        assertThat(hit.basis()).isEqualTo("등록 23시간 후");
        assertThat(evaluator.otpReissued(new Facts().otpAt(NOW.minusHours(24)).build())).isNull();
    }

    // 이체만 보는 규칙
    @Test
    void otpReissuedIgnoredForWithdrawal() {
        assertThat(evaluator.otpReissued(new Facts().withdrawal().otpAt(NOW.minusHours(1)).build())).isNull();
    }

    @Test
    void elderlyFrom65() {
        LocalDate turned65 = NOW.toLocalDate().minusYears(65);

        assertThat(evaluator.elderly(new Facts().birth(turned65).build()).basis()).isEqualTo("만 65세");
        assertThat(evaluator.elderly(new Facts().birth(turned65.plusDays(1)).build())).isNull();
    }

    @Test
    void highOnceFromTenMillion() {
        assertThat(evaluator.highOnce(new Facts().amount(10_000_000).build()).basis()).isEqualTo("1,000만");
        assertThat(evaluator.highOnce(new Facts().amount(9_999_999).build())).isNull();
    }

    @Test
    void highDailyFromThirtyMillion() {
        assertThat(evaluator.highDaily(new Facts().todayTotal(30_000_000).build()).basis()).isEqualTo("오늘 3,000만");
        assertThat(evaluator.highDaily(new Facts().todayTotal(29_999_999).build())).isNull();
    }

    @Test
    void aboveUsualFromDouble() {
        assertThat(evaluator.aboveUsual(new Facts().usual(1_000_000L).amount(2_000_000).build()).basis())
                .isEqualTo("평소최대 100만");
        assertThat(evaluator.aboveUsual(new Facts().usual(1_000_000L).amount(1_999_999).build())).isNull();
    }

    // 90일 기록이 없으면 건너뜀
    @Test
    void aboveUsualSkippedWithoutHistory() {
        assertThat(evaluator.aboveUsual(new Facts().usual(null).amount(50_000_000).build())).isNull();
    }

    @Test
    void rapidCountFromThree() {
        assertThat(evaluator.rapidCount(new Facts().count(3).build()).basis()).isEqualTo("10분 3회");
        assertThat(evaluator.rapidCount(new Facts().count(2).build())).isNull();
    }

    @Test
    void newRecipientOnlyForTransfer() {
        assertThat(evaluator.newRecipient(new Facts().first(true).build()).score()).isEqualTo(10);
        assertThat(evaluator.newRecipient(new Facts().first(false).build())).isNull();
        assertThat(evaluator.newRecipient(new Facts().withdrawal().first(true).build())).isNull();
    }

    @Test
    void inThenOutFromTenMillion() {
        assertThat(evaluator.inThenOut(new Facts().deposit(10_000_000L).build()).basis()).isEqualTo("입금 1,000만");
        assertThat(evaluator.inThenOut(new Facts().deposit(9_999_999L).build())).isNull();
    }

    @Test
    void dormantWakeFromOneYear() {
        assertThat(evaluator.dormantWake(new Facts().previous(NOW.minusDays(365)).build()).basis()).isEqualTo("365일 만");
        assertThat(evaluator.dormantWake(new Facts().previous(NOW.minusDays(364)).build())).isNull();
    }

    // 문서 2장 점수 설계: 고령 + 1회 고액 + 처음 계좌 = 40점 (주의, 탐지 아님)
    // 평소 최대 1,000만이라 1,200만은 2배 미만 → ABOVE_USUAL 은 안 걸림
    @Test
    void elderlyHighAmountNewRecipientIsCautionOnly() {
        List<FdsRuleHit> hits = evaluator.evaluate(new Facts()
                .birth(LocalDate.of(1954, 1, 1)).amount(12_000_000).todayTotal(12_000_000)
                .usual(10_000_000L).first(true).build());

        assertThat(hits).extracting(FdsRuleHit::rule)
                .containsExactly(FdsRule.ELDERLY, FdsRule.HIGH_ONCE, FdsRule.NEW_RECIPIENT);
        assertThat(hits.stream().mapToInt(FdsRuleHit::score).sum()).isEqualTo(40);
    }

    // 9개가 다 걸려도 근거는 15자 이내 (detail VARCHAR(500), 4장)
    @Test
    void allRulesHitAndBasisFitsFifteenChars() {
        List<FdsRuleHit> hits = evaluator.evaluate(new Facts()
                .otpAt(NOW.minusHours(1)).birth(LocalDate.of(1940, 1, 1))
                .amount(9_999_999_999L).todayTotal(9_999_999_999L).usual(1_000_000L).count(99)
                .first(true).deposit(9_999_999_999L).previous(NOW.minusDays(3650)).build());

        assertThat(hits).hasSize(9);
        assertThat(hits).allSatisfy(hit -> assertThat(hit.basis().length()).isLessThanOrEqualTo(15));
    }

    // 기본값 = 아무 규칙에도 안 걸리는 평범한 즉시이체. 테스트마다 바꿀 칸만 바꾼다
    private static class Facts {
        private String type = TransactionType.TRANSFER_OUT;
        private long amount = 100_000;
        private LocalDateTime otpAt = null;
        private LocalDate birth = LocalDate.of(1990, 1, 1);
        private long todayTotal = 100_000;
        private Long usual = 1_000_000L;
        private long count = 1;
        private Boolean first = false;
        private Long deposit = null;
        private LocalDateTime previous = NOW.minusDays(1);

        Facts withdrawal() { type = TransactionType.WITHDRAWAL; return this; }
        Facts amount(long v) { amount = v; return this; }
        Facts otpAt(LocalDateTime v) { otpAt = v; return this; }
        Facts birth(LocalDate v) { birth = v; return this; }
        Facts todayTotal(long v) { todayTotal = v; return this; }
        Facts usual(Long v) { usual = v; return this; }
        Facts count(long v) { count = v; return this; }
        Facts first(Boolean v) { first = v; return this; }
        Facts deposit(Long v) { deposit = v; return this; }
        Facts previous(LocalDateTime v) { previous = v; return this; }

        FdsFacts build() {
            return new FdsFacts(type, 1L, 10L, 100L, amount, NOW,
                    otpAt, birth, todayTotal, usual, count, first, deposit, previous);
        }
    }
}
