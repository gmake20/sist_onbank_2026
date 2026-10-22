package org.doit.onbank.admin.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

// S-17 실시간 모니터링 화면 (MON-002). 역할은 사이드바·화면목록과 같게
// 표는 비워서 내려 주고 admin-monitoring.js 가 /admin/api/monitoring 으로 채운다
@Controller
@RequestMapping("/admin/monitoring")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','OPERATOR')")
public class AdminMonitoringController {

    @GetMapping
    public String monitoring() {
        return "backoffice/admin/monitoring";
    }
}
