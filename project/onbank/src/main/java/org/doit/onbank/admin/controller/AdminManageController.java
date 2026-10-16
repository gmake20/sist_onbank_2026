package org.doit.onbank.admin.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

// 계정 관리 S-19·S-20 (ACT-002, ACT-005). SUPER_ADMIN 전용 (서버 쪽 막기는 팀원1과 상의 후)
@Controller
@RequestMapping("/admin/system/admins")
public class AdminManageController {

    // S-19 계정 목록. 지금은 레이아웃 확인용 빈 화면, 목록은 ACT-002 때 채운다
    @GetMapping
    public String list() {
        return "backoffice/admin/system/admins";
    }

}
