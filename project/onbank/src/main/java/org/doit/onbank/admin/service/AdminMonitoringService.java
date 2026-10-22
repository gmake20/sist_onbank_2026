package org.doit.onbank.admin.service;

import lombok.RequiredArgsConstructor;
import org.doit.onbank.admin.dto.MonitoringPoll;
import org.doit.onbank.admin.dto.MonitoringRow;
import org.doit.onbank.admin.repository.MonitoringSearchRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

// MON-002 실시간 거래 현황 (S-17). 3초마다 최근 50건을 통째로 (D-84)
// 탐지(60점 이상)는 DB 에서, 주의(30~59점)는 서버 메모리(FdsCautionStore)에서 붙인다
@Service
@RequiredArgsConstructor
public class AdminMonitoringService {

    private final MonitoringSearchRepository monitoringSearchRepository;
    private final FdsCautionStore fdsCautionStore;

    @Transactional(readOnly = true)
    public MonitoringPoll poll() {
        List<MonitoringRow> rows = monitoringSearchRepository.findLatest();
        Map<Long, Integer> cautions = fdsCautionStore.findScores(
                rows.stream().map(MonitoringRow::getTransactionHistoryId).toList());
        List<MonitoringPoll.Item> items = rows.stream()
                .map(row -> MonitoringPoll.Item.of(row, cautions.get(row.getTransactionHistoryId())))
                .toList();

        List<MonitoringPoll.Detection> detections = monitoringSearchRepository.findRecentDetections().stream()
                .map(row -> MonitoringPoll.Detection.of(row,
                        AdminFdsService.RULE_LABELS.getOrDefault(row.getRuleCode(), row.getRuleCode()),
                        AdminFdsService.STATUS_LABELS.getOrDefault(row.getStatus(), row.getStatus())))
                .toList();

        return new MonitoringPoll(monitoringSearchRepository.countUnchecked(), items, detections);
    }
}
