package org.doit.onbank.admin.service;

import lombok.RequiredArgsConstructor;
import org.doit.onbank.admin.dto.FdsAiResult;
import org.doit.onbank.admin.dto.MonitoringDetectionRow;
import org.doit.onbank.admin.dto.MonitoringPoll;
import org.doit.onbank.admin.dto.MonitoringRow;
import org.doit.onbank.admin.repository.MonitoringSearchRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

// MON-002 실시간 거래 현황 (S-17). 3초마다 최근 50건을 통째로 (D-84)
// 탐지(60점 이상)는 DB 에서, 주의(30~59점)는 서버 메모리(FdsCautionStore)에서 붙인다
// AI 보조 판정(D-72)도 서버 메모리(FdsAiService)에서 거래내역 번호로 붙인다
@Service
@RequiredArgsConstructor
public class AdminMonitoringService {

    private final MonitoringSearchRepository monitoringSearchRepository;
    private final FdsCautionStore fdsCautionStore;
    private final FdsAiService fdsAiService;

    @Transactional(readOnly = true)
    public MonitoringPoll poll() {
        List<MonitoringRow> rows = monitoringSearchRepository.findLatest();
        List<MonitoringDetectionRow> detectionRows = monitoringSearchRepository.findRecentDetections();
        List<Long> historyIds = rows.stream().map(MonitoringRow::getTransactionHistoryId).toList();
        Map<Long, Integer> cautions = fdsCautionStore.findScores(historyIds);
        // 표 50건 + 오른쪽 탐지 10건의 거래 번호를 한 번에 찾는다 (탐지는 50건 밖으로 밀려났을 수 있음)
        Map<Long, FdsAiResult> ai = fdsAiService.findResults(Stream.concat(historyIds.stream(),
                detectionRows.stream().map(MonitoringDetectionRow::getTransactionHistoryId)).toList());

        List<MonitoringPoll.Item> items = rows.stream()
                .map(row -> MonitoringPoll.Item.of(row, cautions.get(row.getTransactionHistoryId()),
                        ai.get(row.getTransactionHistoryId())))
                .toList();

        List<MonitoringPoll.Detection> detections = detectionRows.stream()
                .map(row -> MonitoringPoll.Detection.of(row,
                        AdminFdsService.RULE_LABELS.getOrDefault(row.getRuleCode(), row.getRuleCode()),
                        AdminFdsService.STATUS_LABELS.getOrDefault(row.getStatus(), row.getStatus()),
                        ai.get(row.getTransactionHistoryId())))
                .toList();

        return new MonitoringPoll(monitoringSearchRepository.countUnchecked(), items, detections);
    }
}
