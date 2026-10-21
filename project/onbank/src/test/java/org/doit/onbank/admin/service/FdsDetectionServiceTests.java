package org.doit.onbank.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

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
import org.doit.onbank.admin.entity.FdsRule;
import org.doit.onbank.admin.entity.FdsHandlingHistory;
import org.doit.onbank.admin.entity.FdsStatus;
import org.doit.onbank.admin.repository.AdminNotificationRepository;
import org.doit.onbank.admin.repository.AdminSearchRepository;
import org.doit.onbank.admin.repository.FdsDetectionRepository;
import org.doit.onbank.admin.repository.FdsHandlingHistoryRepository;
import org.doit.onbank.admin.repository.FdsSearchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

// FdsDetectionService 단위 테스트 : DB 대신 가짜(Mock) Repository, 판정기도 가짜 (판정은 FdsRuleEvaluatorTests 에서 확인)
// 확인하는 것 : 판정 안 하는 경우, 60점 경계, 저장 값, 알림 받는 사람·문구 (D-66 포함), 추가 거래 자동 배정, 조회 기간
// 확인하지 못하는 것 : 쿼리가 실제 데이터를 맞게 거르는지, Aspect·트랜잭션 순서 → 앱에서 실제 이체로 확인
@ExtendWith(MockitoExtension.class)
class FdsDetectionServiceTests {

    private static final Long MEMBER_ID = 100L;
    private static final Long OTHER_MEMBER_ID = 200L;
    private static final Long ACCOUNT_ID = 10L;
    private static final Long HISTORY_ID = 1000L;
    private static final Long DETECTION_ID = 500L;
    private static final String TO_ACCOUNT = "TEST-0002";
    private static final String SQNO = "20261022140000-000001";
    private static final LocalDateTime AT = LocalDateTime.of(2026, 10, 22, 14, 0);

    @Mock
    private FdsSearchRepository fdsSearchRepository;

    @Mock
    private FdsRuleEvaluator evaluator;

    @Mock
    private FdsRuleSettings settings;

    @Mock
    private FdsDetectionRepository fdsDetectionRepository;

    @Mock
    private FdsHandlingHistoryRepository fdsHandlingHistoryRepository;

    @Mock
    private AdminNotificationRepository adminNotificationRepository;

    @Mock
    private AdminSearchRepository adminSearchRepository;

    @InjectMocks
    private FdsDetectionService service;

    @Captor
    private ArgumentCaptor<List<AdminNotification>> notifications;

    // 테스트마다 쓰지 않는 준비도 있어서 lenient (AssetReportServiceTests 와 같음)
    @BeforeEach
    void setUp() {
        lenient().when(settings.getDetectScore()).thenReturn(60);
        lenient().when(settings.getCautionScore()).thenReturn(30);
        lenient().when(settings.getAboveUsualDays()).thenReturn(90);
        lenient().when(settings.getRapidCountMinutes()).thenReturn(10);
        lenient().when(settings.getInThenOutMinutes()).thenReturn(10);

        lenient().when(fdsSearchRepository.findMemberIdByAccountNumber(TO_ACCOUNT)).thenReturn(OTHER_MEMBER_ID);
        lenient().when(fdsSearchRepository.findTransferOutId(ACCOUNT_ID, SQNO)).thenReturn(HISTORY_ID);

        // DB가 넣어 주는 탐지 ID를 직접 설정
        lenient().when(fdsDetectionRepository.save(any())).thenAnswer(invocation -> {
            FdsDetection detection = invocation.getArgument(0);
            ReflectionTestUtils.setField(detection, "detectionId", DETECTION_ID);
            return detection;
        });
        lenient().when(fdsDetectionRepository.findFirstByAccountIdAndStatusInOrderByDetectionIdAsc(anyLong(), any()))
                .thenReturn(Optional.empty());

        // 정상 관리자 3명 중 SERVICE 는 알림 대상 아님
        lenient().when(adminSearchRepository.search(null, null, AdminStatus.ACTIVE)).thenReturn(List.of(
                admin(1L, AdminRole.SUPER_ADMIN), admin(2L, AdminRole.OPERATOR), admin(3L, AdminRole.SERVICE)));
    }

    @Test
    void selfTransferIsNotEvaluated() {
        given(fdsSearchRepository.findMemberIdByAccountNumber(TO_ACCOUNT)).willReturn(MEMBER_ID);

        service.onTransfer(MEMBER_ID, receipt());

        verify(evaluator, never()).evaluate(any());
    }

    // 이번 거래 ID 가 없으면 "이번 거래 제외" 조건이 깨지므로 판정하지 않는다
    @Test
    void transferWithoutHistoryIsSkipped() {
        given(fdsSearchRepository.findTransferOutId(ACCOUNT_ID, SQNO)).willReturn(null);

        service.onTransfer(MEMBER_ID, receipt());

        verify(evaluator, never()).evaluate(any());
    }

    @Test
    void below60SavesNothing() {
        given(evaluator.evaluate(any())).willReturn(List.of(
                hit(FdsRule.IN_THEN_OUT, 40, "입금 1,500만"), hit(FdsRule.HIGH_ONCE, 19, "1,200만")));

        service.onTransfer(MEMBER_ID, receipt());

        verify(fdsDetectionRepository, never()).save(any());
        verify(adminNotificationRepository, never()).saveAll(any());
    }

    @Test
    void from60SavesDetectionAndNotifiesAdminsExceptService() {
        given(evaluator.evaluate(any())).willReturn(List.of(
                hit(FdsRule.IN_THEN_OUT, 40, "입금 1,500만"), hit(FdsRule.DORMANT_WAKE, 20, "400일 만")));

        service.onTransfer(MEMBER_ID, receipt());

        ArgumentCaptor<FdsDetection> saved = ArgumentCaptor.forClass(FdsDetection.class);
        verify(fdsDetectionRepository).save(saved.capture());
        assertThat(saved.getValue().getAccountId()).isEqualTo(ACCOUNT_ID);
        assertThat(saved.getValue().getTransactionHistoryId()).isEqualTo(HISTORY_ID);
        assertThat(saved.getValue().getRiskScore()).isEqualTo(60);
        assertThat(saved.getValue().getRuleCode()).isEqualTo("IN_THEN_OUT");
        assertThat(saved.getValue().getDetail()).isEqualTo(
                "[{\"r\":\"IN_THEN_OUT\",\"s\":40,\"b\":\"입금 1,500만\"},{\"r\":\"DORMANT_WAKE\",\"s\":20,\"b\":\"400일 만\"}]");

        verify(adminNotificationRepository).saveAll(notifications.capture());
        assertThat(notifications.getValue()).extracting(AdminNotification::getRecipientId).containsExactly(1L, 2L);
        assertThat(notifications.getValue()).allSatisfy(n -> {
            assertThat(n.getType()).isEqualTo("FDS_DETECTED");
            assertThat(n.getTargetId()).isEqualTo(DETECTION_ID);
            assertThat(n.getMessage()).isEqualTo("[이상거래 탐지] 60점 · 큰 입금 직후 출금 외 1건");
        });
    }

    // 동점이면 돈 이동(B) 신호가 대표 (OTP 는 계정 신호 A)
    @Test
    void tieGoesToMoneySignal() {
        given(evaluator.evaluate(any())).willReturn(List.of(
                hit(FdsRule.OTP_REISSUED, 30, "등록 1시간 후"), hit(FdsRule.RAPID_COUNT, 30, "10분 3회")));

        service.onTransfer(MEMBER_ID, receipt());

        ArgumentCaptor<FdsDetection> saved = ArgumentCaptor.forClass(FdsDetection.class);
        verify(fdsDetectionRepository).save(saved.capture());
        assertThat(saved.getValue().getRuleCode()).isEqualTo("RAPID_COUNT");
    }

    // D-66 : 처리 중 탐지에 담당자가 있으면 그 사람에게만 "추가 거래"
    @Test
    void additionalTransactionGoesToAssigneeOnly() {
        given(evaluator.evaluate(any())).willReturn(List.of(hit(FdsRule.IN_THEN_OUT, 60, "입금 1,500만")));
        FdsDetection open = FdsDetection.builder().accountId(ACCOUNT_ID).build();
        ReflectionTestUtils.setField(open, "assignedAdminId", 2L);
        given(fdsDetectionRepository.findFirstByAccountIdAndStatusInOrderByDetectionIdAsc(eq(ACCOUNT_ID), any()))
                .willReturn(Optional.of(open));
        given(fdsDetectionRepository.countByAccountIdAndStatusIn(eq(ACCOUNT_ID), any())).willReturn(2L);

        service.onTransfer(MEMBER_ID, receipt());

        verify(adminNotificationRepository).saveAll(notifications.capture());
        assertThat(notifications.getValue()).extracting(AdminNotification::getRecipientId).containsExactly(2L);
        assertThat(notifications.getValue().get(0).getMessage())
                .isEqualTo("[추가 거래] 처리 중인 탐지에 추가 거래: 3번째 · 60점 · 큰 입금 직후 출금");
    }

    // D-66 : 처리 중이지만 아직 미배정이면 관리자 전체
    @Test
    void additionalTransactionWithoutAssigneeGoesToAll() {
        given(evaluator.evaluate(any())).willReturn(List.of(hit(FdsRule.IN_THEN_OUT, 60, "입금 1,500만")));
        given(fdsDetectionRepository.findFirstByAccountIdAndStatusInOrderByDetectionIdAsc(eq(ACCOUNT_ID), any()))
                .willReturn(Optional.of(FdsDetection.builder().accountId(ACCOUNT_ID).build()));
        given(fdsDetectionRepository.countByAccountIdAndStatusIn(eq(ACCOUNT_ID), any())).willReturn(1L);

        service.onTransfer(MEMBER_ID, receipt());

        verify(adminNotificationRepository).saveAll(notifications.capture());
        assertThat(notifications.getValue()).extracting(AdminNotification::getRecipientId).containsExactly(1L, 2L);
    }

    // 담당자가 있는 사건의 추가 거래 → 새 건도 같은 담당자에게 자동 배정 + 처리 이력 1줄
    @Test
    void additionalTransactionIsAutoAssignedToSameAssignee() {
        given(evaluator.evaluate(any())).willReturn(List.of(hit(FdsRule.IN_THEN_OUT, 60, "입금 1,500만")));
        FdsDetection open = FdsDetection.builder().accountId(ACCOUNT_ID).build();
        ReflectionTestUtils.setField(open, "detectionId", 400L);
        ReflectionTestUtils.setField(open, "assignedAdminId", 2L);
        given(fdsDetectionRepository.findFirstByAccountIdAndStatusInOrderByDetectionIdAsc(eq(ACCOUNT_ID), any()))
                .willReturn(Optional.of(open));

        service.onTransfer(MEMBER_ID, receipt());

        ArgumentCaptor<FdsDetection> saved = ArgumentCaptor.forClass(FdsDetection.class);
        verify(fdsDetectionRepository).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(FdsStatus.ASSIGNED);
        assertThat(saved.getValue().getAssignedAdminId()).isEqualTo(2L);

        ArgumentCaptor<FdsHandlingHistory> history = ArgumentCaptor.forClass(FdsHandlingHistory.class);
        verify(fdsHandlingHistoryRepository).save(history.capture());
        assertThat(history.getValue().getDetectionId()).isEqualTo(DETECTION_ID);
        assertThat(history.getValue().getAdminId()).isEqualTo(2L);
        assertThat(history.getValue().getStage()).isEqualTo(FdsStatus.ASSIGNED);
        assertThat(history.getValue().getMemo()).isEqualTo("추가 거래 자동 배정 (같은 계좌 #400 담당자)");
    }

    // 미배정 사건의 추가 거래 → 미확인 그대로, 처리 이력 없음 (누가 맡을 때 묶음 배정으로 함께 넘어감)
    @Test
    void additionalTransactionWithoutAssigneeStaysUnchecked() {
        given(evaluator.evaluate(any())).willReturn(List.of(hit(FdsRule.IN_THEN_OUT, 60, "입금 1,500만")));
        given(fdsDetectionRepository.findFirstByAccountIdAndStatusInOrderByDetectionIdAsc(eq(ACCOUNT_ID), any()))
                .willReturn(Optional.of(FdsDetection.builder().accountId(ACCOUNT_ID).build()));

        service.onTransfer(MEMBER_ID, receipt());

        ArgumentCaptor<FdsDetection> saved = ArgumentCaptor.forClass(FdsDetection.class);
        verify(fdsDetectionRepository).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(FdsStatus.UNCHECKED);
        verify(fdsHandlingHistoryRepository, never()).save(any());
    }

    // 조회 기간이 설정값대로 잘리는지 + 처음 보내는 계좌 판정
    @Test
    void transferFactsUseConfiguredWindows() {
        given(fdsSearchRepository.countPastTransfersTo(MEMBER_ID, TO_ACCOUNT, SQNO)).willReturn(0L);
        given(evaluator.evaluate(any())).willReturn(List.of());

        service.onTransfer(MEMBER_ID, receipt());

        verify(fdsSearchRepository).sumOutflow(MEMBER_ID, AT.toLocalDate().atStartOfDay(), AT);
        verify(fdsSearchRepository).maxOutflow(MEMBER_ID, AT.minusDays(90), AT, HISTORY_ID);
        verify(fdsSearchRepository).countOutflow(MEMBER_ID, AT.minusMinutes(10), AT);
        verify(fdsSearchRepository).maxIncomingFromOthers(ACCOUNT_ID, MEMBER_ID, AT.minusMinutes(10), AT);
        verify(fdsSearchRepository).findPreviousTransactionAt(ACCOUNT_ID, HISTORY_ID);

        ArgumentCaptor<FdsFacts> facts = ArgumentCaptor.forClass(FdsFacts.class);
        verify(evaluator).evaluate(facts.capture());
        assertThat(facts.getValue().transactionType()).isEqualTo(TransactionType.TRANSFER_OUT);
        assertThat(facts.getValue().firstRecipient()).isTrue();
        assertThat(facts.getValue().amount()).isEqualTo(12_000_000L);
    }

    // ATM 출금은 반환값의 거래내역을 그대로 쓰고, 이체 전용 조회는 하지 않는다
    @Test
    void withdrawalUsesHistoryValues() {
        given(evaluator.evaluate(any())).willReturn(List.of());
        TransactionHistory history = TransactionHistory.builder()
                .accountId(ACCOUNT_ID).transactionType(TransactionType.WITHDRAWAL)
                .transactionAmount(3_000_000L).transactionAt(AT).build();
        ReflectionTestUtils.setField(history, "transactionHistoryId", HISTORY_ID);

        service.onWithdrawal(MEMBER_ID, history);

        ArgumentCaptor<FdsFacts> facts = ArgumentCaptor.forClass(FdsFacts.class);
        verify(evaluator).evaluate(facts.capture());
        assertThat(facts.getValue().transactionType()).isEqualTo(TransactionType.WITHDRAWAL);
        assertThat(facts.getValue().transactionHistoryId()).isEqualTo(HISTORY_ID);
        assertThat(facts.getValue().firstRecipient()).isNull();
        verify(fdsSearchRepository, never()).findTransferOutId(anyLong(), anyString());
        verify(fdsSearchRepository, never()).countPastTransfersTo(anyLong(), anyString(), anyString());
    }

    private TransferReceipt receipt() {
        return new TransferReceipt(ACCOUNT_ID, "TEST-0001", TO_ACCOUNT, "홍*동",
                12_000_000L, 5_000_000L, "홍길동", "보낸사람", AT, SQNO);
    }

    private FdsRuleHit hit(FdsRule rule, int score, String basis) {
        return new FdsRuleHit(rule, score, basis);
    }

    private Admin admin(Long id, AdminRole role) {
        Admin admin = Admin.builder().loginId("admin" + id).password("x").name("관리자" + id).role(role).build();
        ReflectionTestUtils.setField(admin, "adminId", id);
        return admin;
    }
}
