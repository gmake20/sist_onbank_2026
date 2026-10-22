package org.doit.onbank.admin.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.doit.onbank.account.dto.TransferReceipt;
import org.doit.onbank.account.entity.TransactionHistory;
import org.doit.onbank.account.entity.TransactionType;
import org.doit.onbank.admin.dto.FdsFacts;
import org.doit.onbank.admin.dto.FdsRuleHit;
import org.doit.onbank.admin.entity.Admin;
import org.doit.onbank.admin.entity.AdminNotification;
import org.doit.onbank.admin.entity.AdminRole;
import org.doit.onbank.admin.entity.AdminStatus;
import org.doit.onbank.admin.entity.FdsDetection;
import org.doit.onbank.admin.entity.FdsHandlingHistory;
import org.doit.onbank.admin.entity.FdsRule;
import org.doit.onbank.admin.entity.FdsStatus;
import org.doit.onbank.admin.repository.AdminNotificationRepository;
import org.doit.onbank.admin.repository.AdminSearchRepository;
import org.doit.onbank.admin.repository.FdsDetectionRepository;
import org.doit.onbank.admin.repository.FdsHandlingHistoryRepository;
import org.doit.onbank.admin.repository.FdsSearchRepository;
import org.doit.onbank.common.security.ActorType;
import org.doit.onbank.otp.entity.OtpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

// FDS-001 이상거래 탐지 (FDS-탐지규칙.md 1·2·6장)
// 즉시이체·ATM 출금이 끝난 뒤 FdsTransactionAspect 가 부른다 (팀원2 트랜잭션은 이미 커밋된 뒤)
// 사실 모으기 → 규칙 9개 판정 → 60점 이상이면 탐지 저장 + 알림 / 30~59점 주의는 저장 안 함 (D-37)
@Slf4j
@Service
@RequiredArgsConstructor
public class FdsDetectionService {

    private static final String NOTIFICATION_TYPE = "FDS_DETECTED";

    // 처리 중 = 종결(NORMAL_CONFIRMED·FRAUD_ACTION) 전 단계 (6장, D-66)
    private static final List<FdsStatus> IN_PROGRESS =
            List.of(FdsStatus.UNCHECKED, FdsStatus.ASSIGNED, FdsStatus.CONTACTING);

    private final FdsSearchRepository fdsSearchRepository;
    private final FdsRuleEvaluator evaluator;
    private final FdsRuleSettings settings;
    private final FdsDetectionRepository fdsDetectionRepository;
    private final FdsHandlingHistoryRepository fdsHandlingHistoryRepository;
    private final AdminNotificationRepository adminNotificationRepository;
    private final AdminSearchRepository adminSearchRepository;
    private final FdsCautionStore fdsCautionStore;

    // 즉시이체. 본인 계좌끼리 이체는 판정하지 않는다
    @Transactional
    public void onTransfer(Long memberId, TransferReceipt receipt) {
        Long toMemberId = fdsSearchRepository.findMemberIdByAccountNumber(receipt.toAccountNumber());
        if (memberId.equals(toMemberId)) {
            return;
        }
        Long historyId = fdsSearchRepository.findTransferOutId(receipt.fromAccountId(), receipt.transactionSqno());
        if (historyId == null) {
            // 없으면 "이번 거래 제외" 조건이 전부 빈 값이 돼서 판정이 조용히 틀어진다
            log.warn("FDS 판정 건너뜀: 이체 거래내역 없음 sqno={}", receipt.transactionSqno());
            return;
        }
        boolean first = fdsSearchRepository.countPastTransfersTo(
                memberId, receipt.toAccountNumber(), receipt.transactionSqno()) == 0;
        detect(collectFacts(TransactionType.TRANSFER_OUT, memberId, receipt.fromAccountId(), historyId,
                receipt.amount(), receipt.transferAt(), first));
    }

    // ATM 출금
    @Transactional
    public void onWithdrawal(Long memberId, TransactionHistory history) {
        detect(collectFacts(TransactionType.WITHDRAWAL, memberId, history.getAccountId(),
                history.getTransactionHistoryId(), history.getTransactionAmount(), history.getTransactionAt(), null));
    }

    // 기간은 이번 거래 시각 기준. 이번 거래는 이미 저장돼 있다 (FdsSearchRepository 주석)
    private FdsFacts collectFacts(String type, Long memberId, Long accountId, Long historyId,
                                  long amount, LocalDateTime at, Boolean firstRecipient) {
        return new FdsFacts(type, memberId, accountId, historyId, amount, at,
                fdsSearchRepository.findOtpReactivatedAt(memberId, OtpStatus.ACTIVE, OtpStatus.LOST),
                fdsSearchRepository.findBirthDate(memberId),
                fdsSearchRepository.sumOutflow(memberId, at.toLocalDate().atStartOfDay(), at),
                fdsSearchRepository.maxOutflow(memberId, at.minusDays(settings.getAboveUsualDays()), at, historyId),
                fdsSearchRepository.countOutflow(memberId, at.minusMinutes(settings.getRapidCountMinutes()), at),
                firstRecipient,
                fdsSearchRepository.maxIncomingFromOthers(accountId, memberId,
                        at.minusMinutes(settings.getInThenOutMinutes()), at),
                fdsSearchRepository.findPreviousTransactionAt(accountId, historyId));
    }

    private void detect(FdsFacts facts) {
        List<FdsRuleHit> hits = evaluator.evaluate(facts);
        int total = hits.stream().mapToInt(FdsRuleHit::score).sum();
        if (total < settings.getDetectScore()) {
            if (total >= settings.getCautionScore()) {
                // 주의는 DB에 저장하지 않고 서버 메모리에만 둔다 (D-37, D-84). 모니터링 화면이 노란색으로 표시
                fdsCautionStore.put(facts.transactionHistoryId(), total);
                log.info("FDS 주의 {}점 account={} history={}", total, facts.accountId(), facts.transactionHistoryId());
            }
            return;
        }

        // 새 탐지를 저장하기 전에 본다 (저장하면 자기 자신이 "처리 중"으로 잡힘)
        Optional<FdsDetection> open = fdsDetectionRepository
                .findFirstByAccountIdAndStatusInOrderByDetectionIdAsc(facts.accountId(), IN_PROGRESS);
        long openCount = open.isPresent()
                ? fdsDetectionRepository.countByAccountIdAndStatusIn(facts.accountId(), IN_PROGRESS) : 0;

        FdsRuleHit main = representative(hits);
        FdsDetection saved = fdsDetectionRepository.save(FdsDetection.builder()
                .accountId(facts.accountId())
                .transactionHistoryId(facts.transactionHistoryId())
                .ruleCode(main.rule().name())
                .riskScore(total)
                .detail(toDetail(hits))
                .build());
        log.info("FDS 탐지 {}점 detection={} account={} history={} rule={}", total, saved.getDetectionId(),
                facts.accountId(), facts.transactionHistoryId(), main.rule().name());

        String summary = total + "점 · " + main.rule().getLabel()
                + (hits.size() > 1 ? " 외 " + (hits.size() - 1) + "건" : "");
        if (open.isEmpty()) {
            send(allAdmins(), saved, "[이상거래 탐지] " + summary);
            return;
        }
        // D-66 : 처리 중 탐지가 있는 계좌. 담당자가 있으면 그 사람만, 미배정이면 전체
        Long assigned = open.get().getAssignedAdminId();
        if (assigned != null) {
            // 이미 누가 맡은 사건의 추가 거래 → 새 건도 같은 담당자에게 (다른 관리자가 따로 맡아 사건이 갈라지지 않게)
            // saved 는 이 트랜잭션 안의 엔티티라 assign() 만 하면 끝날 때 UPDATE 된다
            saved.assign(assigned, LocalDateTime.now());
            fdsHandlingHistoryRepository.save(FdsHandlingHistory.builder()
                    .detectionId(saved.getDetectionId())
                    .adminId(assigned)
                    .stage(FdsStatus.ASSIGNED)
                    .memo("추가 거래 자동 배정 (같은 계좌 #" + open.get().getDetectionId() + " 담당자)")
                    .build());
            log.info("FDS 추가 거래 자동 배정 detection={} admin={}", saved.getDetectionId(), assigned);
        }
        send(assigned != null ? List.of(assigned) : allAdmins(), saved,
                "[추가 거래] 처리 중인 탐지에 추가 거래: " + (openCount + 1) + "번째 · " + summary);
    }

    // 점수가 가장 큰 규칙. 동점이면 돈 이동(B) 신호를 먼저 — 목록 한 줄에서 무슨 일인지 보이게
    private FdsRuleHit representative(List<FdsRuleHit> hits) {
        FdsRuleHit main = hits.get(0);
        for (FdsRuleHit hit : hits) {
            boolean higher = hit.score() > main.score();
            boolean sameButMoney = hit.score() == main.score() && isAccountSignal(main.rule()) && !isAccountSignal(hit.rule());
            if (higher || sameButMoney) {
                main = hit;
            }
        }
        return main;
    }

    private boolean isAccountSignal(FdsRule rule) {
        return rule == FdsRule.OTP_REISSUED || rule == FdsRule.ELDERLY;
    }

    // detail = [{"r":규칙,"s":점수,"b":근거}] (4장). 근거는 코드가 만든 값이라 따옴표·역슬래시만 막는다
    private String toDetail(List<FdsRuleHit> hits) {
        return hits.stream()
                .map(h -> "{\"r\":\"" + h.rule().name() + "\",\"s\":" + h.score()
                        + ",\"b\":\"" + h.basis().replace("\\", "\\\\").replace("\"", "\\\"") + "\"}")
                .collect(Collectors.joining(",", "[", "]"));
    }

    // MON-001 수신자 : 정상 상태 관리자 중 SUPER_ADMIN·OPERATOR (창구 직원·SERVICE 제외)
    private List<Long> allAdmins() {
        return adminSearchRepository.search(null, null, AdminStatus.ACTIVE).stream()
                .filter(admin -> admin.getRole() != AdminRole.SERVICE)
                .map(Admin::getAdminId)
                .toList();
    }

    private void send(List<Long> adminIds, FdsDetection detection, String message) {
        adminNotificationRepository.saveAll(adminIds.stream()
                .map(id -> AdminNotification.builder()
                        .recipientType(ActorType.ADMIN)
                        .recipientId(id)
                        .type(NOTIFICATION_TYPE)
                        .targetId(detection.getDetectionId())
                        .message(message)
                        .build())
                .toList());
    }
}
