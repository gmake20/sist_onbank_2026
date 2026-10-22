package org.doit.onbank.admin.service;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.doit.onbank.account.entity.TransactionType;
import org.doit.onbank.admin.dto.FdsAiResult;
import org.doit.onbank.admin.dto.FdsRuleHit;
import org.doit.onbank.admin.entity.FdsRule;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.ResourceAccessException;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// FDS AI 보조 판정 (D-72, FDS-탐지규칙.md 8장) : 규칙 판정이 30점 이상인 거래만 Jev 에게 물어본다
// 숫자 판정(금액·횟수·기간·나이)은 규칙이 이미 했으므로 묻지 않는다 — Jev 문서 jaggedness "Jev is not a calculator"
// 묻는 것 : 1) 이체 메모가 보이스피싱 표현인가(noul, 메모 2개 각각) 2) 걸린 신호가 어떤 수법과 닮았나(choice)
// 결과는 점수에 안 넣고 서버 메모리에만 둔다 (FdsCautionStore 와 같은 방식, 사용자 2026-10-22). 서버를 다시 켜면 비어 있다
// 고객 이체 응답을 기다리게 하지 않도록 이체 트랜잭션 커밋 뒤 전용 스레드 1개에서 부른다 (사용자 2026-10-22)
@Slf4j
@Service
@RequiredArgsConstructor
public class FdsAiService {

    private static final int MAX_SIZE = 100;

    // 화면 표시 기준 (FDS-탐지규칙.md 8장 "메모 표시 기준(안) 0.5"). 수법도 1등 확률 0.5 이상만
    private static final double MEMO_SHOW = 0.5;
    private static final double PATTERN_SHOW = 0.5;
    private static final String NONE = "none";

    // 수법 선택지 (사용자 2026-10-22 "3개 + 해당 없음"). 순서가 답에 영향을 줄 수 있음 (jaggedness 8번) → 시연 전 순서 바꿔 확인
    // 화면 이름은 PATTERN_LABELS
    private static final Map<String, String> PATTERNS = new LinkedHashMap<>();
    static {
        PATTERNS.put("account_takeover",
                "Someone other than the owner has taken over the account and is moving money out,"
                        + " for example right after the OTP device was re-registered or to a recipient never paid before");
        PATTERNS.put("money_mule",
                "The account is used as a pass-through: money from other people comes in and is quickly sent or withdrawn again");
        PATTERNS.put("elderly_scam",
                "An elderly customer is being persuaded by a scammer to send or withdraw an unusually large amount");
        PATTERNS.put(NONE, "The signals do not clearly resemble any of the other patterns");
    }

    public static final Map<String, String> PATTERN_LABELS = Map.of(
            "account_takeover", "계정 탈취",
            "money_mule", "대포통장 경유",
            "elderly_scam", "고령자 대상 사기");

    // 규칙 → 영어 사실 문장. 한국어보다 영어 정확도가 높다 (models 문서 Language support, 사용자 선택 "가")
    // 숫자는 넣지 않는다 : 기준을 넘었는지는 규칙이 이미 판정함
    private static final Map<FdsRule, String> SIGNALS = Map.of(
            FdsRule.OTP_REISSUED, "The OTP device was re-registered shortly before this transaction",
            FdsRule.ELDERLY, "The account holder is 65 or older",
            FdsRule.HIGH_ONCE, "This single transaction is a large amount",
            FdsRule.HIGH_DAILY, "The customer's total outflow today is large",
            FdsRule.ABOVE_USUAL, "The amount is far above this customer's usual transactions",
            FdsRule.RAPID_COUNT, "Several outgoing transactions happened within a few minutes",
            FdsRule.NEW_RECIPIENT, "The money goes to an account this customer has never sent to before",
            FdsRule.IN_THEN_OUT, "A large deposit from another person arrived just before this outflow",
            FdsRule.DORMANT_WAKE, "The account had not been used for a long time before this transaction");

    // 0단계 실측 기준 + 대출 사기 표현 보강 ("저금리 대환 인지세" 0.30 놓침, FDS-탐지규칙.md 8장) → 새 문구로 재측정 필요
    private static final String MEMO_TRUE = "Wording a voice-phishing scammer tells the victim to use: impersonating the prosecutor's office,"
            + " police or Financial Supervisory Service, a 'safe account', investigation deposits,"
            + " or upfront fees, stamp taxes or guarantees required before a low-interest loan";
    private static final String MEMO_FALSE = "An ordinary memo such as a person's name, rent, allowance, a purchase or a deposit for a contract";

    private final JevClient jevClient;

    // 전용 스레드 1개 : 이체가 몰리면 차례로 처리 (시연 규모 기준)
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> new Thread(r, "fds-ai"));

    // 넣은 순서를 기억하고, MAX_SIZE 를 넘으면 가장 오래된 것부터 지운다 (FdsCautionStore 와 같음)
    private final Map<Long, FdsAiResult> results = new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, FdsAiResult> eldest) {
            return size() > MAX_SIZE;
        }
    };

    // FdsDetectionService 가 판정 트랜잭션 안에서 부른다. 커밋된 뒤에 스레드로 넘긴다 (롤백되면 안 부름)
    // 트랜잭션 밖(단위 테스트 등)이면 바로 넘긴다
    public void judgeAfterCommit(Long transactionHistoryId, String transactionType, List<FdsRuleHit> hits,
                                 String senderMemo, String receiverMemo) {
        Runnable task = () -> executor.execute(() -> judge(transactionHistoryId, transactionType, hits, senderMemo, receiverMemo));
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    task.run();
                }
            });
        } else {
            task.run();
        }
    }

    // 화면에 보낼 거래 번호 중 AI 결과가 있는 것만 (없으면 빈 Map)
    public synchronized Map<Long, FdsAiResult> findResults(Collection<Long> transactionHistoryIds) {
        Map<Long, FdsAiResult> found = new HashMap<>();
        for (Long id : transactionHistoryIds) {
            FdsAiResult result = results.get(id);
            if (result != null) {
                found.put(id, result);
            }
        }
        return found;
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }

    // 실패해도 예외를 밖으로 내지 않는다 : 규칙 판정·이체에는 영향 없음
    void judge(Long transactionHistoryId, String transactionType, List<FdsRuleHit> hits,
               String senderMemo, String receiverMemo) {
        LocalDateTime now = LocalDateTime.now();
        if (!jevClient.isEnabled()) {
            save(transactionHistoryId, FdsAiResult.failed("키 없음", now));
            return;
        }
        boolean transfer = TransactionType.TRANSFER_OUT.equals(transactionType);
        long started = System.currentTimeMillis();
        try {
            JevClient.Response response = jevClient.ask(
                    state(transfer, hits, senderMemo, receiverMemo), questions(transfer));
            FdsAiResult result = toResult(response, senderMemo, receiverMemo, now);
            save(transactionHistoryId, result);
            log.info("FDS AI 보조 history={} pattern={} {}% memo={}% {}ms", transactionHistoryId,
                    result.pattern(), result.patternPercent(), result.memoPercent(), System.currentTimeMillis() - started);
        } catch (ResourceAccessException e) {
            save(transactionHistoryId, FdsAiResult.failed("시간 초과", now));
            log.warn("FDS AI 보조 시간 초과·연결 실패 history={} {}ms", transactionHistoryId,
                    System.currentTimeMillis() - started, e);
        } catch (RuntimeException e) {
            save(transactionHistoryId, FdsAiResult.failed("응답 오류", now));
            log.warn("FDS AI 보조 실패 history={}", transactionHistoryId, e);
        }
    }

    // state 는 질문에 필요한 것만 (jaggedness 5번 "Large state full of irrelevant detail"). 메모는 한국어 원문 그대로
    private Map<String, Object> state(boolean transfer, List<FdsRuleHit> hits, String senderMemo, String receiverMemo) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("transaction", transfer ? "Bank transfer to another person's account" : "ATM cash withdrawal");
        state.put("triggered_signals", hits.stream().map(h -> SIGNALS.get(h.rule())).toList());
        if (transfer) {
            state.put("sender_memo", nullToEmpty(senderMemo));
            state.put("receiver_memo", nullToEmpty(receiverMemo));
        }
        return state;
    }

    // ATM 출금은 메모가 없으므로 수법만 묻는다. 질문 이름(키)은 모델에 안 보내지므로 문장을 instructions 에 다 적는다
    private Map<String, JevClient.Question> questions(boolean transfer) {
        Map<String, JevClient.Question> questions = new LinkedHashMap<>();
        questions.put("pattern", JevClient.Question.choice(
                "Which fraud pattern do `triggered_signals` of this `transaction` most resemble?", PATTERNS));
        if (transfer) {
            questions.put("sender_memo", JevClient.Question.noul(
                    "Does `sender_memo` use wording typical of Korean voice-phishing scams?", MEMO_TRUE, MEMO_FALSE));
            questions.put("receiver_memo", JevClient.Question.noul(
                    "Does `receiver_memo` use wording typical of Korean voice-phishing scams?", MEMO_TRUE, MEMO_FALSE));
        }
        return questions;
    }

    // 표시 기준 미만은 null (화면 "특이 사항 없음"). 메모는 둘 중 확률이 큰 쪽
    private FdsAiResult toResult(JevClient.Response response, String senderMemo, String receiverMemo, LocalDateTime now) {
        JevClient.Answer pattern = response.answers().get("pattern");
        String patternLabel = null;
        Integer patternPercent = null;
        if (pattern != null && !NONE.equals(pattern.choice())) {
            double p = pattern.probabilities().getOrDefault(pattern.choice(), 0.0);
            if (p >= PATTERN_SHOW) {
                patternLabel = PATTERN_LABELS.getOrDefault(pattern.choice(), pattern.choice());
                patternPercent = percent(p);
            }
        }

        double senderP = noul(response, "sender_memo");
        double receiverP = noul(response, "receiver_memo");
        String memo = null;
        Integer memoPercent = null;
        if (Math.max(senderP, receiverP) >= MEMO_SHOW) {
            memo = senderP >= receiverP ? senderMemo : receiverMemo;
            memoPercent = percent(Math.max(senderP, receiverP));
        }
        return new FdsAiResult(true, null, patternLabel, patternPercent, memo, memoPercent, response.model(), now);
    }

    private double noul(JevClient.Response response, String key) {
        JevClient.Answer answer = response.answers().get(key);
        return answer == null || answer.noul() == null ? 0.0 : answer.noul();
    }

    private int percent(double p) {
        return (int) Math.round(p * 100);
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private synchronized void save(Long transactionHistoryId, FdsAiResult result) {
        results.put(transactionHistoryId, result);
    }
}
