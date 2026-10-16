package org.doit.onbank.admin.controller;

import lombok.RequiredArgsConstructor;
import org.doit.onbank.admin.entity.AdminRole;
import org.doit.onbank.admin.entity.AdminStatus;
import org.doit.onbank.admin.service.AdminManageService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

// 계정 관리 S-19·S-20 (ACT-002, ACT-005). SUPER_ADMIN 전용 (서버 쪽 막기는 팀원1과 상의 후)
@Controller
@RequestMapping("/admin/system/admins")
@RequiredArgsConstructor
public class AdminManageController {

    private final AdminManageService adminManageService;

    // S-19 계정 목록 (관리자 탭). select에서 "전체"(빈 값)를 고르면 enum이 null로 들어온다
    @GetMapping
    public String list(@RequestParam(required = false) String keyword,
                       @RequestParam(required = false) AdminRole role,
                       @RequestParam(required = false) AdminStatus status,
                       Model model) {
        model.addAttribute("admins",adminManageService.searchAdmins(keyword, role, status));
        model.addAttribute("keyword", keyword);
        model.addAttribute("role", role);
        model.addAttribute("status", status);
        model.addAttribute("roles", AdminRole.values());
        model.addAttribute("statuses", AdminStatus.values());

        return "backoffice/admin/system/admins";
    }


}
