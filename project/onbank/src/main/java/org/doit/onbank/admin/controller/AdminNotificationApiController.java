package org.doit.onbank.admin.controller;

import lombok.RequiredArgsConstructor;
import org.doit.onbank.admin.dto.NotificationPoll;
import org.doit.onbank.admin.security.AdminPrincipal;
import org.doit.onbank.admin.service.AdminNotificationService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// MON-001 관리자 공통 레이아웃 JS가 3초마다 부르는 알림 폴링 API (D-71)
// 예) GET /admin/api/notifications            → 처음 열 때 (기준 번호 + 미확인 개수)
//     GET /admin/api/notifications?after=15   → 15번 다음 새 알림
// GET이라 CSRF 토큰 없음. 로그인 안 됐으면 SecurityConfig entryPoint 가 401
@RestController
@RequestMapping("/admin/api/notifications")
@RequiredArgsConstructor
public class AdminNotificationApiController {

    private final AdminNotificationService adminNotificationService;

    @GetMapping
    public NotificationPoll poll(@AuthenticationPrincipal AdminPrincipal me,
                                 @RequestParam(required = false) Long after) {
        return adminNotificationService.poll(me.getAdminId(), after);
    }
}
