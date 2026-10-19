package org.doit.onbank.admin.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.doit.onbank.admin.dto.EmployeeCreateForm;
import org.doit.onbank.admin.security.AdminPrincipal;
import org.doit.onbank.admin.service.EmployeeManageService;
import org.doit.onbank.common.exception.BusinessException;
import org.doit.onbank.employee.entity.EmployeeRole;
import org.doit.onbank.employee.entity.EmployeeStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

// 계정 관리 직원 탭, 직원 생성. SUPER ADMIN 전용
@Controller
@RequestMapping("/admin/system/employees")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class EmployeeManageController {

    private static final String FORM_VIEW = "backoffice/admin/system/employees-new";

    private final EmployeeManageService employeeManageService;

    @GetMapping
    public String list(@RequestParam(name = "keyword", required = false) String keyword,
                       @RequestParam(name = "branchId", required = false) Long branchId,
                       @RequestParam(name = "role", required = false) EmployeeRole role,
                       @RequestParam(name = "status", required = false) EmployeeStatus status,
                       Model model
                       ){
        model.addAttribute("employees", employeeManageService.searchEmployees(keyword, branchId, role, status));
        model.addAttribute("branchNames", employeeManageService.branchNames());
        model.addAttribute("branches", employeeManageService.selectableBranches());
        model.addAttribute("keyword", keyword);
        model.addAttribute("role", role);
        model.addAttribute("status", status);
        model.addAttribute("roles",EmployeeRole.values());
        model.addAttribute("statuses",EmployeeStatus.values());

        return "backoffice/admin/system/employees";
    }

    // 직원 계정 생성 폼
    @GetMapping("/new")
    public  String createForm(Model model){
        model.addAttribute("form", new EmployeeCreateForm());
        addFormOption(model);
        return  FORM_VIEW;
    }

    // 저장 성공하면 목록으로 리다이렉트, 임시 비밀번호는 flash로 한 번만 보여준다.
    @PostMapping
    public String create(@AuthenticationPrincipal AdminPrincipal me,
                         @Valid @ModelAttribute("form") EmployeeCreateForm form,
                         BindingResult bindingResult,
                         HttpServletRequest request,
                         Model model,
                         RedirectAttributes rttr){
        addFormOption(model);
        if(bindingResult.hasErrors()){
            return FORM_VIEW;
        }
        try {
            rttr.addFlashAttribute("created",
                                    employeeManageService.createEmployee(me,form, request.getRemoteAddr()));
            return  "redirect:/admin/system/employees";
        } catch (IllegalStateException e) { // 이메일 중복
            bindingResult.rejectValue("email", "duplicate", e.getMessage());
        } catch (BusinessException e) { // 지점 직급 오류, 사번 충돌
            bindingResult.reject("invalid", e.getMessage());
        }
        return FORM_VIEW;
    }

    // 생성 폼의 select 선택지
    private  void addFormOption(Model model){
        model.addAttribute("branches", employeeManageService.selectableBranches());
        model.addAttribute("positions", EmployeeManageService.POSITIONS);
        model.addAttribute("roles",EmployeeRole.values());
    }




} // class
