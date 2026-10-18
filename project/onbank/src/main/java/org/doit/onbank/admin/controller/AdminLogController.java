package org.doit.onbank.admin.controller;

import lombok.RequiredArgsConstructor;
import org.doit.onbank.admin.service.AdminLogService;
import org.doit.onbank.common.security.ActorType;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;

// S-14 활동 로그 (LOG-002). 조회만, SUPER_ADMIN 전용
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
    public String list(@RequestParam(required = false) ActorType actorType,
                       @RequestParam(required = false) String actorLoginId,
                       @RequestParam(required = false) String actionType,
                       @RequestParam(required = false) String targetType,
                       @RequestParam(required = false) String targetId,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                       @RequestParam(defaultValue = "0") int page,
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
}
