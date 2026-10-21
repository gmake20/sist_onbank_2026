package org.doit.onbank.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.doit.onbank.account.entity.AccountStatus;
import org.doit.onbank.admin.dto.AccountListRow;
import org.doit.onbank.admin.dto.FdsContactForm;
import org.doit.onbank.admin.entity.Admin;
import org.doit.onbank.admin.entity.AdminRole;
import org.doit.onbank.admin.entity.ContactMethod;
import org.doit.onbank.admin.entity.ContactResult;
import org.doit.onbank.admin.entity.FdsDetection;
import org.doit.onbank.admin.entity.FdsHandlingHistory;
import org.doit.onbank.admin.entity.FdsStatus;
import org.doit.onbank.admin.repository.FdsDetectionRepository;
import org.doit.onbank.admin.repository.FdsDetectionSearchRepository;
import org.doit.onbank.admin.repository.FdsHandlingHistoryRepository;
import org.doit.onbank.admin.security.AdminPrincipal;
import org.doit.onbank.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

// AdminFdsService 단위 테스트 (FDS-004 배정, FDS-005·006 연락 시작·연락 기록·종결) : DB 대신 가짜(Mock) Repository
// 확인하는 것 : 같은 계좌 건 묶음 처리, 처리 이력·활동 로그 건수, 이미 맡은 건·없는 번호 거절, 담당자 본인 검사,
//              종결 결과 제한(마지막 연락), 사기 의심 조치 거래정지·이미 정지면 건너뜀
// 확인하지 못하는 것 : FOR UPDATE 잠금이 실제로 동시 요청을 줄 세우는지, 메서드 이름 쿼리가 맞는지 → 실제 DB 필요 (localhost 테스트 스키마)
@ExtendWith(MockitoExtension.class)
class AdminFdsServiceTests {

    private static final Long ADMIN_ID = 1L;
    private static final Long ACCOUNT_ID = 7L;
    private static final String IP = "127.0.0.1";

    @Mock
    private FdsDetectionSearchRepository fdsDetectionSearchRepository;

    @Mock
    private FdsRuleSettings fdsRuleSettings;

    @Mock
    private FdsDetectionRepository fdsDetectionRepository;

    @Mock
    private FdsHandlingHistoryRepository fdsHandlingHistoryRepository;

    @Mock
    private AdminLogService adminLogService;

    @Mock
    private AdminAccountService adminAccountService;

    @InjectMocks
    private AdminFdsService service;

    private AdminPrincipal me;

    @BeforeEach
    void setUp() {
        Admin admin = Admin.builder().loginId("oper01").password("x").name("김운영").role(AdminRole.OPERATOR).build();
        ReflectionTestUtils.setField(admin, "adminId", ADMIN_ID);
        me = new AdminPrincipal(admin);
    }

    // 18번을 눌렀는데 같은 계좌에 16번도 미확인 → 둘 다 내 담당, 이력·로그 2줄씩
    @Test
    void assignTakesUncheckedDetectionsOfSameAccount() {
        FdsDetection d16 = detection(16L);
        FdsDetection d18 = detection(18L);
        given(fdsDetectionRepository.findById(18L)).willReturn(Optional.of(d18));
        given(fdsDetectionRepository.findByAccountIdAndStatusForUpdate(ACCOUNT_ID, FdsStatus.UNCHECKED))
                .willReturn(List.of(d16, d18));

        int count = service.assign(me, 18L, IP);

        assertThat(count).isEqualTo(2);
        assertThat(List.of(d16, d18)).allSatisfy(d -> {
            assertThat(d.getStatus()).isEqualTo(FdsStatus.ASSIGNED);
            assertThat(d.getAssignedAdminId()).isEqualTo(ADMIN_ID);
            assertThat(d.getAssignedAt()).isNotNull();
        });

        ArgumentCaptor<FdsHandlingHistory> history = ArgumentCaptor.forClass(FdsHandlingHistory.class);
        verify(fdsHandlingHistoryRepository, times(2)).save(history.capture());
        assertThat(history.getAllValues()).extracting(FdsHandlingHistory::getDetectionId).containsExactly(16L, 18L);
        assertThat(history.getAllValues()).extracting(FdsHandlingHistory::getStage)
                .containsOnly(FdsStatus.ASSIGNED);
        assertThat(history.getAllValues()).extracting(FdsHandlingHistory::getMemo)
                .containsExactly("같은 계좌 #18 배정 때 함께", null);

        verify(adminLogService).record(eq(me), eq("FDS_ASSIGN"), eq("FDS_DETECTION"), eq("16"),
                eq("UNCHECKED"), eq("ASSIGNED"), isNull(), eq(IP));
        verify(adminLogService).record(eq(me), eq("FDS_ASSIGN"), eq("FDS_DETECTION"), eq("18"),
                eq("UNCHECKED"), eq("ASSIGNED"), isNull(), eq(IP));
    }

    // 다른 관리자가 먼저 맡음 → 잠금 조회에 18번이 안 나온다 → 거절, 아무것도 안 바뀜
    @Test
    void assignRejectsDetectionAlreadyTaken() {
        FdsDetection d16 = detection(16L);
        given(fdsDetectionRepository.findById(18L)).willReturn(Optional.of(detection(18L)));
        given(fdsDetectionRepository.findByAccountIdAndStatusForUpdate(ACCOUNT_ID, FdsStatus.UNCHECKED))
                .willReturn(List.of(d16));

        assertThatThrownBy(() -> service.assign(me, 18L, IP))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("이미 다른 담당자가 맡았거나");

        assertThat(d16.getStatus()).isEqualTo(FdsStatus.UNCHECKED);
        verify(fdsHandlingHistoryRepository, never()).save(any());
        verify(adminLogService, never()).record(any(), anyString(), anyString(), anyString(),
                any(), any(), any(), any());
    }

    @Test
    void assignRejectsUnknownDetection() {
        given(fdsDetectionRepository.findById(99999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.assign(me, 99999L, IP))
                .isInstanceOf(BusinessException.class)
                .hasMessage("없는 탐지 번호입니다.");

        verify(fdsDetectionRepository, never()).findByAccountIdAndStatusForUpdate(any(), any());
    }

    // 엔티티 메서드 : 미확인이 아니면 거절 (잠금 안에서 다시 보는 마지막 확인)
    @Test
    void entityAssignRejectsWhenNotUnchecked() {
        FdsDetection d = detection(18L);
        d.assign(2L, LocalDateTime.now());

        assertThatThrownBy(() -> d.assign(ADMIN_ID, LocalDateTime.now()))
                .isInstanceOf(IllegalStateException.class);
        assertThat(d.getAssignedAdminId()).isEqualTo(2L);
    }

    // 내 담당 18·16번 → 둘 다 고객 연락 중, 이력·로그 2줄씩
    @Test
    void startContactMovesMyAssignedDetections() {
        FdsDetection d16 = mine(16L);
        FdsDetection d18 = mine(18L);
        given(fdsDetectionRepository.findById(18L)).willReturn(Optional.of(d18));
        given(fdsDetectionRepository.findMineByAccountIdAndStatusForUpdate(ACCOUNT_ID, FdsStatus.ASSIGNED, ADMIN_ID))
                .willReturn(List.of(d16, d18));

        int count = service.startContact(me, 18L, IP);

        assertThat(count).isEqualTo(2);
        assertThat(List.of(d16, d18)).extracting(FdsDetection::getStatus).containsOnly(FdsStatus.CONTACTING);
        verify(fdsHandlingHistoryRepository, times(2)).save(any());
        verify(adminLogService, times(2)).record(eq(me), eq("FDS_STAGE_CHANGE"), eq("FDS_DETECTION"), anyString(),
                eq("ASSIGNED"), eq("CONTACTING"), isNull(), eq(IP));
    }

    // 다른 관리자 담당 건 → 거절, 잠금 조회까지 가지 않음 (EX-ADM-FDS-002)
    @Test
    void startContactRejectsOtherAdminsDetection() {
        FdsDetection others = detection(18L);
        others.assign(2L, LocalDateTime.now());
        given(fdsDetectionRepository.findById(18L)).willReturn(Optional.of(others));

        assertThatThrownBy(() -> service.startContact(me, 18L, IP))
                .isInstanceOf(BusinessException.class)
                .hasMessage("담당자만 처리할 수 있습니다.");

        assertThat(others.getStatus()).isEqualTo(FdsStatus.ASSIGNED);
        verify(fdsDetectionRepository, never()).findMineByAccountIdAndStatusForUpdate(any(), any(), any());
    }

    // 연락 기록 → 건마다 이력 1줄 (연락 칸 채움), 단계는 그대로, 활동 로그 없음
    @Test
    void recordContactAddsHistoryToEachDetection() {
        FdsDetection d16 = contacting(16L);
        FdsDetection d18 = contacting(18L);
        given(fdsDetectionRepository.findById(18L)).willReturn(Optional.of(d18));
        given(fdsDetectionRepository.findMineByAccountIdAndStatusForUpdate(ACCOUNT_ID, FdsStatus.CONTACTING, ADMIN_ID))
                .willReturn(List.of(d16, d18));
        LocalDateTime at = LocalDateTime.of(2026, 10, 22, 13, 50);

        int count = service.recordContact(me, 18L, contactForm(at, "  고객이 이체한 적 없다고 함  "));

        assertThat(count).isEqualTo(2);
        ArgumentCaptor<FdsHandlingHistory> history = ArgumentCaptor.forClass(FdsHandlingHistory.class);
        verify(fdsHandlingHistoryRepository, times(2)).save(history.capture());
        assertThat(history.getAllValues()).allSatisfy(h -> {
            assertThat(h.getStage()).isEqualTo(FdsStatus.CONTACTING);
            assertThat(h.getContactAt()).isEqualTo(at);
            assertThat(h.getContactMethod()).isEqualTo("PHONE");
            assertThat(h.getContactResult()).isEqualTo(ContactResult.NOT_CUSTOMER);
            assertThat(h.getMemo()).isEqualTo("고객이 이체한 적 없다고 함");
        });
        assertThat(List.of(d16, d18)).extracting(FdsDetection::getStatus).containsOnly(FdsStatus.CONTACTING);
        verify(adminLogService, never()).record(any(), anyString(), anyString(), anyString(), any(), any(), any(), any());
    }

    // 아직 담당 배정 단계 → 연락 기록 거절 (순서 건너뛰기, EX-ADM-FDS-003)
    @Test
    void recordContactRejectsWhenNotContacting() {
        FdsDetection d18 = mine(18L);
        given(fdsDetectionRepository.findById(18L)).willReturn(Optional.of(d18));
        given(fdsDetectionRepository.findMineByAccountIdAndStatusForUpdate(ACCOUNT_ID, FdsStatus.CONTACTING, ADMIN_ID))
                .willReturn(List.of());

        assertThatThrownBy(() -> service.recordContact(me, 18L, contactForm(LocalDateTime.now(), "통화")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("고객 연락 중 단계에서만");

        verify(fdsHandlingHistoryRepository, never()).save(any());
    }

    // 마지막 연락 = 본인 거래 확인 → 정상 확인으로 2건 함께 종결, 거래정지 안 함
    @Test
    void closeNormalWhenLastContactConfirmed() {
        FdsDetection d16 = contacting(16L);
        FdsDetection d18 = contacting(18L);
        givenTargets(d16, d18);
        givenLastContact(16L, 1L, LocalDateTime.of(2026, 10, 22, 14, 0), ContactResult.CONFIRMED_BY_CUSTOMER);
        givenLastContact(18L, 2L, LocalDateTime.of(2026, 10, 22, 14, 0), ContactResult.CONFIRMED_BY_CUSTOMER);

        int count = service.close(me, 18L, FdsStatus.NORMAL_CONFIRMED, "  ", IP);

        assertThat(count).isEqualTo(2);
        assertThat(List.of(d16, d18)).allSatisfy(d -> {
            assertThat(d.getStatus()).isEqualTo(FdsStatus.NORMAL_CONFIRMED);
            assertThat(d.getClosedAt()).isNotNull();
        });
        ArgumentCaptor<FdsHandlingHistory> history = ArgumentCaptor.forClass(FdsHandlingHistory.class);
        verify(fdsHandlingHistoryRepository, times(2)).save(history.capture());
        assertThat(history.getAllValues()).extracting(FdsHandlingHistory::getMemo)
                .containsExactly("같은 계좌 #18 종결 때 함께", "종결");
        verify(adminLogService, times(2)).record(eq(me), eq("FDS_STAGE_CHANGE"), eq("FDS_DETECTION"), anyString(),
                eq("CONTACTING"), eq("NORMAL_CONFIRMED"), isNull(), eq(IP));
        verify(adminAccountService, never()).freeze(any(), any(), any(), any());
    }

    // 사기 의심 조치 + 정상 계좌 → 거래정지 (사유 = 탐지 번호 + 메모), 이력 메모에 결과 한 줄
    @Test
    void closeFraudFreezesActiveAccount() {
        FdsDetection d16 = contacting(16L);
        FdsDetection d18 = contacting(18L);
        givenTargets(d16, d18);
        givenLastContact(16L, 1L, LocalDateTime.of(2026, 10, 22, 14, 0), ContactResult.NOT_CUSTOMER);
        givenLastContact(18L, 2L, LocalDateTime.of(2026, 10, 22, 14, 0), ContactResult.NOT_CUSTOMER);
        given(adminAccountService.account(ACCOUNT_ID)).willReturn(accountRow(AccountStatus.ACTIVE));

        service.close(me, 18L, FdsStatus.FRAUD_ACTION, " 고객이 이체한 적 없다고 함 ", IP);

        assertThat(List.of(d16, d18)).extracting(FdsDetection::getStatus).containsOnly(FdsStatus.FRAUD_ACTION);
        verify(adminAccountService).freeze(me, ACCOUNT_ID, "FDS 탐지 #18 외 1건 사기 의심 조치 : 고객이 이체한 적 없다고 함", IP);
        ArgumentCaptor<FdsHandlingHistory> history = ArgumentCaptor.forClass(FdsHandlingHistory.class);
        verify(fdsHandlingHistoryRepository, times(2)).save(history.capture());
        assertThat(history.getAllValues()).extracting(FdsHandlingHistory::getMemo)
                .containsExactly("같은 계좌 #18 종결 때 함께", "고객이 이체한 적 없다고 함 / 계좌 거래정지");
    }

    // 계좌가 이미 거래정지 → 거래정지 호출 없이 종결, 이력에 그때 상태
    @Test
    void closeFraudSkipsFreezeWhenAlreadyFrozen() {
        FdsDetection d18 = contacting(18L);
        givenTargets(d18);
        givenLastContact(18L, 1L, LocalDateTime.of(2026, 10, 22, 14, 0), ContactResult.NO_ANSWER);
        given(adminAccountService.account(ACCOUNT_ID)).willReturn(accountRow(AccountStatus.FROZEN));

        service.close(me, 18L, FdsStatus.FRAUD_ACTION, null, IP);

        assertThat(d18.getStatus()).isEqualTo(FdsStatus.FRAUD_ACTION);
        verify(adminAccountService, never()).freeze(any(), any(), any(), any());
        ArgumentCaptor<FdsHandlingHistory> history = ArgumentCaptor.forClass(FdsHandlingHistory.class);
        verify(fdsHandlingHistoryRepository).save(history.capture());
        assertThat(history.getValue().getMemo()).isEqualTo("종결 / 계좌가 이미 거래정지 상태라 거래정지하지 않음");
    }

    // 사건 단위 마지막 연락 : 18번은 14시 본인 확인, 16번은 15시 본인 아님 → 15시가 마지막 → 정상 확인 거절
    @Test
    void closeRejectsNormalWhenLatestContactIsNotCustomer() {
        FdsDetection d16 = contacting(16L);
        FdsDetection d18 = contacting(18L);
        givenTargets(d16, d18);
        givenLastContact(16L, 1L, LocalDateTime.of(2026, 10, 22, 15, 0), ContactResult.NOT_CUSTOMER);
        givenLastContact(18L, 2L, LocalDateTime.of(2026, 10, 22, 14, 0), ContactResult.CONFIRMED_BY_CUSTOMER);

        assertThatThrownBy(() -> service.close(me, 18L, FdsStatus.NORMAL_CONFIRMED, null, IP))
                .isInstanceOf(BusinessException.class)
                .hasMessage("마지막 연락 결과가 '본인 아님'이라 정상 거래 확인으로 종결할 수 없습니다.");

        assertThat(List.of(d16, d18)).extracting(FdsDetection::getStatus).containsOnly(FdsStatus.CONTACTING);
        verify(fdsHandlingHistoryRepository, never()).save(any());
    }

    // 연락 기록 없음 → 거절 (EX-ADM-FDS-004)
    @Test
    void closeRejectsWithoutContactRecord() {
        FdsDetection d18 = contacting(18L);
        givenTargets(d18);
        given(fdsHandlingHistoryRepository.findFirstByDetectionIdAndContactResultIsNotNullOrderByContactAtDescHistoryIdDesc(18L))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> service.close(me, 18L, FdsStatus.FRAUD_ACTION, null, IP))
                .isInstanceOf(BusinessException.class)
                .hasMessage("연락 결과를 먼저 기록하세요.");

        assertThat(d18.getStatus()).isEqualTo(FdsStatus.CONTACTING);
        verify(adminAccountService, never()).freeze(any(), any(), any(), any());
    }

    // 결과를 안 고름(null) → 조회 전에 거절
    @Test
    void closeRejectsMissingResult() {
        assertThatThrownBy(() -> service.close(me, 18L, null, null, IP))
                .isInstanceOf(BusinessException.class)
                .hasMessage("종결 결과를 고르세요.");

        verify(fdsDetectionRepository, never()).findById(any());
    }

    // 엔티티 메서드 : 고객 연락 중이 아니면 거절
    @Test
    void entityCloseRejectsWhenNotContacting() {
        FdsDetection d = mine(18L);

        assertThatThrownBy(() -> d.close(FdsStatus.NORMAL_CONFIRMED, LocalDateTime.now()))
                .isInstanceOf(IllegalStateException.class);
        assertThat(d.getStatus()).isEqualTo(FdsStatus.ASSIGNED);
        assertThat(d.getClosedAt()).isNull();
    }

    // 종결 칸 표시값 : 건수 + 마지막 연락. 본인 확인일 때만 정상 확인 가능
    @Test
    void closeInfoReportsCountAndLastContact() {
        FdsDetection d16 = contacting(16L);
        FdsDetection d18 = contacting(18L);
        given(fdsDetectionRepository.findById(18L)).willReturn(Optional.of(d18));
        given(fdsDetectionRepository.findByAccountIdAndStatusAndAssignedAdminIdOrderByDetectionId(
                ACCOUNT_ID, FdsStatus.CONTACTING, ADMIN_ID)).willReturn(List.of(d16, d18));
        givenLastContact(16L, 1L, LocalDateTime.of(2026, 10, 22, 14, 0), ContactResult.NO_ANSWER);
        given(fdsHandlingHistoryRepository.findFirstByDetectionIdAndContactResultIsNotNullOrderByContactAtDescHistoryIdDesc(18L))
                .willReturn(Optional.empty());

        AdminFdsService.CloseInfo info = service.closeInfo(me, 18L);

        assertThat(info.count()).isEqualTo(2);
        assertThat(info.lastContact()).isEqualTo(ContactResult.NO_ANSWER);
        assertThat(info.normalAllowed()).isFalse();
    }

    // 내 담당 (ASSIGNED)
    private FdsDetection mine(Long id) {
        FdsDetection d = detection(id);
        d.assign(ADMIN_ID, LocalDateTime.now());
        return d;
    }

    // 내 담당 + 고객 연락 중 (CONTACTING)
    private FdsDetection contacting(Long id) {
        FdsDetection d = mine(id);
        d.startContact();
        return d;
    }

    private FdsContactForm contactForm(LocalDateTime at, String memo) {
        FdsContactForm form = new FdsContactForm();
        form.setContactAt(at);
        form.setContactMethod(ContactMethod.PHONE);
        form.setContactResult(ContactResult.NOT_CUSTOMER);
        form.setMemo(memo);
        return form;
    }

    // 종결 잠금 조회 : 누른 건 = 마지막 인자
    private void givenTargets(FdsDetection... targets) {
        FdsDetection clicked = targets[targets.length - 1];
        given(fdsDetectionRepository.findById(clicked.getDetectionId())).willReturn(Optional.of(clicked));
        given(fdsDetectionRepository.findMineByAccountIdAndStatusForUpdate(ACCOUNT_ID, FdsStatus.CONTACTING, ADMIN_ID))
                .willReturn(List.of(targets));
    }

    private void givenLastContact(Long detectionId, Long historyId, LocalDateTime at, ContactResult result) {
        FdsHandlingHistory h = FdsHandlingHistory.builder().detectionId(detectionId).adminId(ADMIN_ID)
                .stage(FdsStatus.CONTACTING).memo("통화").contactAt(at).contactMethod("PHONE").contactResult(result).build();
        ReflectionTestUtils.setField(h, "historyId", historyId);
        given(fdsHandlingHistoryRepository.findFirstByDetectionIdAndContactResultIsNotNullOrderByContactAtDescHistoryIdDesc(detectionId))
                .willReturn(Optional.of(h));
    }

    private AccountListRow accountRow(AccountStatus status) {
        return new AccountListRow(ACCOUNT_ID, "1002482915307", "user01", "홍길동", null, status, 0L, null);
    }

    private FdsDetection detection(Long id) {
        FdsDetection d = FdsDetection.builder().accountId(ACCOUNT_ID).transactionHistoryId(1000L + id)
                .ruleCode("IN_THEN_OUT").riskScore(65).detail("[]").build();
        ReflectionTestUtils.setField(d, "detectionId", id);
        return d;
    }
}
