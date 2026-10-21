package org.doit.onbank.admin.service;

import lombok.RequiredArgsConstructor;
import org.doit.onbank.admin.dto.FdsListRow;
import org.doit.onbank.admin.entity.FdsRule;
import org.doit.onbank.admin.entity.FdsStatus;
import org.doit.onbank.admin.repository.FdsDetectionSearchRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// FDS-002 탐지 목록 (S-15). 조회만
// 기간 검사는 컨트롤러에서 (AdminTransactionService 와 같음)
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminFdsService {

    private static final int PAGE_SIZE = 20;

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

    private final FdsDetectionSearchRepository fdsDetectionSearchRepository;

    // assignedAdminId : "내 담당만"이면 로그인한 관리자 번호, 아니면 null
    public Page<FdsListRow> search(LocalDate from, LocalDate to, String status, String accountNumber,
                                   Long assignedAdminId, int page) {
        return fdsDetectionSearchRepository.search(from.atStartOfDay(), to.plusDays(1).atStartOfDay(),
                statusNames(status), withoutHyphen(accountNumber), assignedAdminId,
                PageRequest.of(Math.max(page, 0), PAGE_SIZE));
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
