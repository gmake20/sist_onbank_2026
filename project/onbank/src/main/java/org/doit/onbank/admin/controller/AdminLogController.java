package org.doit.onbank.admin.controller;

import lombok.RequiredArgsConstructor;
import org.doit.onbank.admin.service.AdminLogService;
import org.doit.onbank.common.security.ActorType;
import org.doit.onbank.common.security.LoginFailReason;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;

// S-14 활동 로그 (LOG-002). 조회만, SUPER_ADMIN 전용
// 탭 : 작업 이력(/admin/logs, admin_log) / 로그인 이력(/admin/logs/logins, admin_login_log)
@Controller
@RequestMapping("/admin/logs")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminLogController {

    // 기간을 안 고르면 오늘 기준 최근 30일
    private static final int DEFAULT_DAYS = 30;

    private final AdminLogService adminLogService;

    // select에서 "전체"(빈 값)를 고르면 enum은 null, 문자열은 빈 값으로 들어온다 (서비스에서 null로 바꿈)
    @GetMapping
    public String list(@RequestParam(name = "actorType", required = false) ActorType actorType,
                       @RequestParam(name = "actorLoginId", required = false) String actorLoginId,
                       @RequestParam(name = "actionType", required = false) String actionType,
                       @RequestParam(name = "targetType", required = false) String targetType,
                       @RequestParam(name = "targetId", required = false) String targetId,
                       @RequestParam(name = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                       @RequestParam(name = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                       @RequestParam(name = "page", defaultValue = "0") int page,
                       Model model) {
        if (to == null) {
            to = LocalDate.now();
        }
        if (from == null) {
            from = to.minusDays(DEFAULT_DAYS);
        }

        model.addAttribute("logs", adminLogService.search(actorType, actorLoginId, actionType,
                targetType, targetId, from, to, page));
        model.addAttribute("actorType", actorType);
        model.addAttribute("actorLoginId", actorLoginId);
        model.addAttribute("actionType", actionType);
        model.addAttribute("targetType", targetType);
        model.addAttribute("targetId", targetId);
        model.addAttribute("from", from);
        model.addAttribute("to", to);
        model.addAttribute("actorTypes", ActorType.values());
        model.addAttribute("actionTypes", adminLogService.actionTypes());
        model.addAttribute("targetTypes", adminLogService.targetTypes());

        return "backoffice/admin/logs";
    }

    // S-14 로그인 이력 탭 (admin_login_log). 결과 : SUCCESS / FAIL / 빈 값(전체)
    @GetMapping("/logins")
    public String logins(@RequestParam(name = "loginId", required = false) String loginId,
                         @RequestParam(name = "result", required = false) String result,
                         @RequestParam(name = "failReason", required = false) LoginFailReason failReason,
                         @RequestParam(name = "ipAddress", required = false) String ipAddress,
                         @RequestParam(name = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                         @RequestParam(name = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                         @RequestParam(name = "page", defaultValue = "0") int page,
                         Model model) {
        if (to == null) {
            to = LocalDate.now();
        }
        if (from == null) {
            from = to.minusDays(DEFAULT_DAYS);
        }
        Boolean success = "SUCCESS".equals(result) ? Boolean.TRUE : "FAIL".equals(result) ? Boolean.FALSE : null;

        model.addAttribute("logs", adminLogService.searchLogins(loginId, success, failReason, ipAddress, from, to, page));
        model.addAttribute("loginId", loginId);
        model.addAttribute("result", success == null ? null : result);
        model.addAttribute("failReason", failReason);
        model.addAttribute("ipAddress", ipAddress);
        model.addAttribute("from", from);
        model.addAttribute("to", to);
        model.addAttribute("failReasons", LoginFailReason.values());

        return "backoffice/admin/login-logs";
    }
}
