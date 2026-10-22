package org.doit.onbank.admin.controller;

import lombok.RequiredArgsConstructor;
import org.doit.onbank.admin.dto.MonitoringPoll;
import org.doit.onbank.admin.service.AdminMonitoringService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// MON-002 모니터링 화면 JS가 3초마다 부르는 조회 API (D-84). 최근 50건을 통째로
// 예) GET /admin/api/monitoring
// 알림 API(AdminNotificationApiController)와 따로 둔다 : 거래 조회가 실패해도 알림은 계속 뜨게 (#52)
@RestController
@RequestMapping("/admin/api/monitoring")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN','OPERATOR')")
public class AdminMonitoringApiController {

    private final AdminMonitoringService adminMonitoringService;

    @GetMapping
    public MonitoringPoll poll() {
        return adminMonitoringService.poll();
    }
}
