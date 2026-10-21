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

// AdminFdsService.assign (FDS-004 내가 맡기) 단위 테스트 : DB 대신 가짜(Mock) Repository
// 확인하는 것 : 같은 계좌 미확인 건 묶음 배정, 처리 이력·활동 로그 건수, 이미 맡은 건·없는 번호 거절, 연락 시작·연락 기록, 담당자 본인 검사
// 확인하지 못하는 것 : FOR UPDATE 잠금이 실제로 동시 요청을 줄 세우는지 → 실제 DB 필요 (localhost 테스트 스키마)
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

    private FdsDetection detection(Long id) {
        FdsDetection d = FdsDetection.builder().accountId(ACCOUNT_ID).transactionHistoryId(1000L + id)
                .ruleCode("IN_THEN_OUT").riskScore(65).detail("[]").build();
        ReflectionTestUtils.setField(d, "detectionId", id);
        return d;
    }
}
