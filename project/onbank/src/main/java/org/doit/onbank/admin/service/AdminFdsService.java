package org.doit.onbank.admin.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.doit.onbank.admin.dto.FdsContactForm;
import org.doit.onbank.admin.dto.FdsDetailRow;
import org.doit.onbank.admin.dto.FdsDetailView;
import org.doit.onbank.admin.dto.FdsListRow;
import org.doit.onbank.admin.entity.ContactMethod;
import org.doit.onbank.admin.entity.ContactResult;
import org.doit.onbank.admin.entity.FdsDetection;
import org.doit.onbank.admin.entity.FdsHandlingHistory;
import org.doit.onbank.admin.entity.FdsRule;
import org.doit.onbank.admin.entity.FdsStatus;
import org.doit.onbank.admin.repository.FdsDetectionRepository;
import org.doit.onbank.admin.repository.FdsDetectionSearchRepository;
import org.doit.onbank.admin.repository.FdsHandlingHistoryRepository;
import org.doit.onbank.admin.security.AdminPrincipal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.doit.onbank.common.exception.BusinessException;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// FDS-002 탐지 목록 (S-15) · 상세 (S-16) 조회, FDS-004 담당자 배정
// 기간 검사는 컨트롤러에서 (AdminTransactionService 와 같음)
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminFdsService {

    private static final int PAGE_SIZE = 20;

    // 상세 점수 막대 색 단계 수 (화면 --det-s1 ~ s5)
    private static final int MAX_SHADE = 5;

    // detail JSON 읽기용. 설정이 필요 없어 빈(bean) 대신 기본값 하나를 둔다
    private static final JsonMapper JSON = JsonMapper.builder().build();

    // 처리 상태 select 의 묶음 값
    public static final String FILTER_OPEN = "OPEN";
    public static final String FILTER_CLOSED = "CLOSED";

    // 처리 중 = 종결(NORMAL_CONFIRMED·FRAUD_ACTION) 전 단계 (FdsDetectionService 41줄과 같음, D-66)
    private static final List<FdsStatus> OPEN =
            List.of(FdsStatus.UNCHECKED, FdsStatus.ASSIGNED, FdsStatus.CONTACTING);
    private static final List<FdsStatus> CLOSED =
            List.of(FdsStatus.NORMAL_CONFIRMED, FdsStatus.FRAUD_ACTION);

    // 처리 상태 배지·select 의 한글 이름. 키는 FdsStatus 이름, 넣은 순서대로 select 에 나온다
    public static final Map<String, String> STATUS_LABELS = statusLabels();

    // 대표 규칙 한글 이름. 키는 FdsRule 이름 (rule_code)
    public static final Map<String, String> RULE_LABELS = Arrays.stream(FdsRule.values())
            .collect(Collectors.toUnmodifiableMap(Enum::name, FdsRule::getLabel));

    // 탐지 대상 거래 종류 (D-09). 대출 상환·적금 납입 WITHDRAWAL 은 판정에서 빠진다 (D-78)
    public static final Map<String, String> TX_TYPE_LABELS =
            Map.of("TRANSFER_OUT", "이체", "WITHDRAWAL", "ATM 출금");

    // 처리 이력의 연락 방법·판단 결과 한글 이름. 키는 enum 이름 (fds_handling_history 에 저장된 값)
    public static final Map<String, String> CONTACT_METHOD_LABELS = Arrays.stream(ContactMethod.values())
            .collect(Collectors.toUnmodifiableMap(Enum::name, ContactMethod::getLabel));
    public static final Map<String, String> CONTACT_RESULT_LABELS = Arrays.stream(ContactResult.values())
            .collect(Collectors.toUnmodifiableMap(Enum::name, ContactResult::getLabel));

    private final FdsDetectionSearchRepository fdsDetectionSearchRepository;
    private final FdsRuleSettings fdsRuleSettings;
    private final FdsDetectionRepository fdsDetectionRepository;
    private final FdsHandlingHistoryRepository fdsHandlingHistoryRepository;
    private final AdminLogService adminLogService;

    // assignedAdminId : "내 담당만"이면 로그인한 관리자 번호, 아니면 null
    public Page<FdsListRow> search(LocalDate from, LocalDate to, String status, String accountNumber,
                                   Long assignedAdminId, int page) {
        return fdsDetectionSearchRepository.search(from.atStartOfDay(), to.plusDays(1).atStartOfDay(),
                statusNames(status), withoutHyphen(accountNumber), assignedAdminId,
                PageRequest.of(Math.max(page, 0), PAGE_SIZE));
    }

    // FDS-004 담당자 배정 ("내가 맡기"). 같은 계좌의 미확인 건을 함께 맡는다 (D-66 추가 거래 알림이 담당자에게 가도록)
    // 한 트랜잭션 : 탐지 상태 변경 + 처리 이력 + 활동 로그 → 하나라도 실패하면 전부 롤백
    // 돌려주는 값 = 맡은 건수 (누른 건 포함). 화면 안내에 쓴다
    @Transactional
    public int assign(AdminPrincipal me, Long detectionId, String requestIp) {
        FdsDetection clicked = fdsDetectionRepository.findById(detectionId)
                .orElseThrow(() -> new BusinessException("없는 탐지 번호입니다."));

        // 계좌 단위로 번호 순 잠금 (FdsDetectionRepository 주석). 누른 건이 없으면 = 이미 누가 맡았거나 처리가 진행됨
        List<FdsDetection> targets = fdsDetectionRepository
                .findByAccountIdAndStatusForUpdate(clicked.getAccountId(), FdsStatus.UNCHECKED);
        if (targets.stream().noneMatch(d -> d.getDetectionId().equals(detectionId))) {
            throw new BusinessException("이미 다른 담당자가 맡았거나 처리가 진행된 탐지입니다. 화면을 새로 고쳐 확인하세요.");
        }

        LocalDateTime now = LocalDateTime.now();
        for (FdsDetection d : targets) {
            try {
                d.assign(me.getAdminId(), now);
            } catch (IllegalStateException e) {     // 잠금 안에서 상태를 다시 본다. 정상 흐름에선 안 생김
                throw new BusinessException("이미 처리된 요청입니다. 화면을 새로 고쳐 확인하세요.");
            }
            fdsHandlingHistoryRepository.save(FdsHandlingHistory.builder()
                    .detectionId(d.getDetectionId())
                    .adminId(me.getAdminId())
                    .stage(FdsStatus.ASSIGNED)
                    .memo(d.getDetectionId().equals(detectionId) ? null : "같은 계좌 #" + detectionId + " 배정 때 함께")
                    .build());
            adminLogService.record(me, "FDS_ASSIGN", "FDS_DETECTION", String.valueOf(d.getDetectionId()),
                    FdsStatus.UNCHECKED.name(), FdsStatus.ASSIGNED.name(), null, requestIp);
        }
        log.info("FDS 배정 admin={} clicked={} count={}", me.getAdminId(), detectionId, targets.size());
        return targets.size();
    }

    // FDS-005 고객 연락 시작 : 담당 배정 → 고객 연락 중. 같은 계좌의 내 담당 건을 함께 (사건 단위)
    // 단계 변경이라 처리 이력 + 활동 로그(FDS_STAGE_CHANGE, LOG-활동로그.md 34줄)
    @Transactional
    public int startContact(AdminPrincipal me, Long detectionId, String requestIp) {
        List<FdsDetection> targets = lockMine(me, detectionId, FdsStatus.ASSIGNED,
                "담당 배정 단계에서만 고객 연락을 시작할 수 있습니다. 화면을 새로 고쳐 확인하세요.");
        for (FdsDetection d : targets) {
            try {
                d.startContact();
            } catch (IllegalStateException e) {     // 잠금 안에서 다시 보는 확인. 정상 흐름에선 안 생김
                throw new BusinessException("이미 처리된 요청입니다. 화면을 새로 고쳐 확인하세요.");
            }
            fdsHandlingHistoryRepository.save(FdsHandlingHistory.builder()
                    .detectionId(d.getDetectionId())
                    .adminId(me.getAdminId())
                    .stage(FdsStatus.CONTACTING)
                    .memo(d.getDetectionId().equals(detectionId) ? "연락 시작" : "같은 계좌 #" + detectionId + " 연락 시작 때 함께")
                    .build());
            adminLogService.record(me, "FDS_STAGE_CHANGE", "FDS_DETECTION", String.valueOf(d.getDetectionId()),
                    FdsStatus.ASSIGNED.name(), FdsStatus.CONTACTING.name(), null, requestIp);
        }
        log.info("FDS 연락 시작 admin={} clicked={} count={}", me.getAdminId(), detectionId, targets.size());
        return targets.size();
    }

    // FDS-006 연락 결과 기록 : 단계는 그대로(고객 연락 중), 같은 계좌의 내 담당 건마다 처리 이력 1줄
    // 활동 로그는 남기지 않는다 (누가 언제는 처리 이력에 있음. LOG-활동로그.md 42줄 미정 1 → 2026-10-22 결정)
    @Transactional
    public int recordContact(AdminPrincipal me, Long detectionId, FdsContactForm form) {
        List<FdsDetection> targets = lockMine(me, detectionId, FdsStatus.CONTACTING,
                "고객 연락 중 단계에서만 연락 결과를 기록할 수 있습니다. 화면을 새로 고쳐 확인하세요.");
        for (FdsDetection d : targets) {
            fdsHandlingHistoryRepository.save(FdsHandlingHistory.builder()
                    .detectionId(d.getDetectionId())
                    .adminId(me.getAdminId())
                    .stage(FdsStatus.CONTACTING)
                    .memo(form.getMemo().trim())
                    .contactAt(form.getContactAt())
                    .contactMethod(form.getContactMethod().name())
                    .contactResult(form.getContactResult())
                    .build());
        }
        log.info("FDS 연락 기록 admin={} clicked={} count={} result={}", me.getAdminId(), detectionId,
                targets.size(), form.getContactResult());
        return targets.size();
    }

    // 누른 건 확인 → 담당자 본인인지 (EX-ADM-FDS-002) → 같은 계좌의 내 담당·그 단계 건을 번호 순 잠금
    // 누른 건이 목록에 없으면 = 단계가 다르거나 그 사이 바뀜 (EX-ADM-FDS-003)
    private List<FdsDetection> lockMine(AdminPrincipal me, Long detectionId, FdsStatus stage, String wrongStage) {
        FdsDetection clicked = fdsDetectionRepository.findById(detectionId)
                .orElseThrow(() -> new BusinessException("없는 탐지 번호입니다."));
        if (!me.getAdminId().equals(clicked.getAssignedAdminId())) {
            throw new BusinessException("담당자만 처리할 수 있습니다.");
        }
        List<FdsDetection> targets = fdsDetectionRepository
                .findMineByAccountIdAndStatusForUpdate(clicked.getAccountId(), stage, me.getAdminId());
        if (targets.stream().noneMatch(d -> d.getDetectionId().equals(detectionId))) {
            throw new BusinessException(wrongStage);
        }
        return targets;
    }

    // S-16 상세. 없는 번호면 BusinessException → 컨트롤러가 목록으로 돌려보내며 안내 (AdminAccountService.account 와 같음)
    // 규칙은 점수 큰 순. 같은 점수는 저장된 순서 그대로 (정렬이 안정적)
    public FdsDetailView detail(Long detectionId) {
        FdsDetailRow row = fdsDetectionSearchRepository.findDetail(detectionId)
                .orElseThrow(() -> new BusinessException("없는 탐지 번호입니다."));
        int scale = Math.max(100, row.getRiskScore());

        List<Hit> hits = parse(row.getDetail(), detectionId).stream()
                .sorted(Comparator.comparingInt(Hit::s).reversed())
                .toList();
        List<FdsDetailView.RuleLine> rules = new ArrayList<>();
        for (int i = 0; i < hits.size(); i++) {
            Hit h = hits.get(i);
            rules.add(new FdsDetailView.RuleLine(h.r(), RULE_LABELS.getOrDefault(h.r(), h.r()), h.s(), h.b(),
                    h.s() * 100 / scale, Math.min(i + 1, MAX_SHADE)));
        }
        return new FdsDetailView(row, rules, fdsDetectionSearchRepository.findHistory(detectionId),
                fdsRuleSettings.getCautionScore(), fdsRuleSettings.getDetectScore(), scale);
    }

    // detail 한 칸 = {"r":규칙,"s":점수,"b":근거} (FdsDetectionService.toDetail)
    record Hit(String r, int s, String b) {
    }

    // 비었거나 깨졌으면 빈 목록 + 경고 로그. 화면은 규칙 칸만 "정보 없음"으로 나오고 나머지는 그대로
    private List<Hit> parse(String detail, Long detectionId) {
        if (detail == null || detail.isBlank()) {
            return List.of();
        }
        try {
            return JSON.readValue(detail, new TypeReference<List<Hit>>() {
            });
        } catch (JacksonException e) {
            log.warn("FDS detail 해석 실패 detection={} : {}", detectionId, e.getMessage());
            return List.of();
        }
    }

    // OPEN·CLOSED = 묶음 / 단계 이름 하나 = 그 단계만 / 빈 값·모르는 값 = 전체
    private List<String> statusNames(String status) {
        List<FdsStatus> statuses;
        if (FILTER_OPEN.equals(status)) {
            statuses = OPEN;
        } else if (FILTER_CLOSED.equals(status)) {
            statuses = CLOSED;
        } else if (STATUS_LABELS.containsKey(status)) {
            statuses = List.of(FdsStatus.valueOf(status));
        } else {
            statuses = List.of(FdsStatus.values());
        }
        return statuses.stream().map(Enum::name).toList();
    }

    private static Map<String, String> statusLabels() {
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put("UNCHECKED", "미확인");
        labels.put("ASSIGNED", "담당 배정");
        labels.put("CONTACTING", "고객 연락 중");
        labels.put("NORMAL_CONFIRMED", "정상 거래 확인");
        labels.put("FRAUD_ACTION", "사기 조치");
        return Collections.unmodifiableMap(labels);
    }

    // 1002-482 / 1002482 둘 다 찾도록 하이픈만 뺀다 (AdminTransactionService 와 같음)
    private String withoutHyphen(String value) {
        return (value == null || value.isBlank()) ? null : value.trim().replace("-", "");
    }
}
