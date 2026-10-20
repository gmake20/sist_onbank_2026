package org.doit.onbank.admin.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.doit.onbank.admin.dto.AdminCreateForm;
import org.doit.onbank.admin.entity.AdminRole;
import org.doit.onbank.admin.entity.AdminStatus;
import org.doit.onbank.admin.security.AdminPrincipal;
import org.doit.onbank.admin.service.AdminManageService;
import org.doit.onbank.common.exception.BusinessException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

// 계정 관리 S-19·S-20 (ACT-002, ACT-005). SUPER_ADMIN 전용 (서버 쪽 막기는 팀원1과 상의 후)
@Controller
@RequestMapping("/admin/system/admins")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminManageController {

    // 생성 화면의 역할 선택지 : 최고 관리자는 1명(시드)이라 뺀다 (D-14)
    private static final List<AdminRole> CREATABLE_ROLES = List.of(AdminRole.OPERATOR, AdminRole.SERVICE);
    private final AdminManageService adminManageService;

    // S-19 계정 목록 (관리자 탭). select에서 "전체"(빈 값)를 고르면 enum이 null로 들어온다
    @GetMapping
    public String list(@RequestParam(name = "keyword", required = false) String keyword,
                       @RequestParam(name = "role", required = false) AdminRole role,
                       @RequestParam(name = "status", required = false) AdminStatus status,
                       Model model) {
        model.addAttribute("admins",adminManageService.searchAdmins(keyword, role, status));
        model.addAttribute("keyword", keyword);
        model.addAttribute("role", role);
        model.addAttribute("status", status);
        model.addAttribute("roles", AdminRole.values());
        model.addAttribute("statuses", AdminStatus.values());

        return "backoffice/admin/system/admins";
    }

    // S-20 관리자 계정 생성 폼
    @GetMapping("/new")
    public String createForm(Model model) {
        model.addAttribute("form", new AdminCreateForm());
        model.addAttribute("roles", CREATABLE_ROLES);
        return "backoffice/admin/system/admin-new";
    }

    // S-20 저장. 성공하면 목록으로 리다이렉트(PRG)하고, M-03 임시 비밀번호는 flash로 한 번만 보여 준다
    @PostMapping
    public String create(@AuthenticationPrincipal AdminPrincipal me,
                         @Valid @ModelAttribute("form") AdminCreateForm form, BindingResult bindingResult,
                         HttpServletRequest request, Model model, RedirectAttributes redirectAttributes) {
        model.addAttribute("roles", CREATABLE_ROLES);
        if (bindingResult.hasErrors()) {
            return "backoffice/admin/system/admin-new";
        }
        try {
            redirectAttributes.addFlashAttribute("created",
                    adminManageService.createAdmin(me, form, request.getRemoteAddr()));
            return "redirect:/admin/system/admins";
        } catch (IllegalStateException e) {        // 아이디 중복 → 아이디 칸 아래에 표시
            bindingResult.rejectValue("loginId", "duplicate", e.getMessage());
        } catch (IllegalArgumentException e) {     // SUPER_ADMIN (화면을 거치지 않은 요청)
            bindingResult.rejectValue("role", "notAllowed", e.getMessage());
        }
        return "backoffice/admin/system/admin-new";
    }

    // 비활성화 알림
    @PostMapping("{/adminId}/deactivate")
    public String deactivate (@AuthenticationPrincipal AdminPrincipal me, @PathVariable("adminId") Long adminId,
                             @RequestParam("reason") String reason, HttpServletRequest request,RedirectAttributes rttr
                             ) {
        try {
            adminManageService.deactivate(me, adminId, reason, request.getRemoteAddr());
            rttr.addFlashAttribute("message", "계정을 비활성화했습니다.");

        } catch (BusinessException e) {
            rttr.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/system/admins";
    }

    // 활성화, 알림
    @PostMapping("{/adminId}/activate")
    public String activate (@AuthenticationPrincipal AdminPrincipal me, @PathVariable("adminId") Long adminId,
                              @RequestParam("reason") String reason, HttpServletRequest request,RedirectAttributes rttr
                                ) {
        try {
            adminManageService.deactivate(me, adminId, reason, request.getRemoteAddr());
            rttr.addFlashAttribute("message", "계정을 활성화했습니다.");

        } catch (BusinessException e) {
            rttr.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/system/admins";
    }

    // 잠금해제, 알림
    @PostMapping("{/adminId}/unlock")
    public String unlock(@AuthenticationPrincipal AdminPrincipal me, @PathVariable("adminId") Long adminId,
                            @RequestParam("reason") String reason, HttpServletRequest request,RedirectAttributes rttr
    ) {
        try {
            adminManageService.deactivate(me, adminId, reason, request.getRemoteAddr());
            rttr.addFlashAttribute("message", "로그인 잠금을 해제했습니다.");

        } catch (BusinessException e) {
            rttr.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/system/admins";
    }

}
