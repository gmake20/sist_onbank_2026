package org.doit.onbank.admin.controller;

import lombok.RequiredArgsConstructor;
import org.doit.onbank.admin.service.EmployeeManageService;
import org.doit.onbank.employee.entity.EmployeeRole;
import org.doit.onbank.employee.entity.EmployeeStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

// 계정 관리 직원 탭. SUPER ADMIN 전용
@Controller
@RequestMapping("/admin/system/employees")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class EmployeeManageController {

    private final EmployeeManageService employeeManageService;

    @GetMapping
    public String list(@RequestParam(required = false) String keyword,
                       @RequestParam(required = false) Long branchId,
                       @RequestParam(required = false) EmployeeRole role,
                       @RequestParam(required = false) EmployeeStatus status,
                       Model model
                       ){
        model.addAttribute("employees", employeeManageService.searchEmployees(keyword, branchId, role, status));
        model.addAttribute("branchNames", employeeManageService.branchNames());
        model.addAttribute("branches", employeeManageService.selectBranches());
        model.addAttribute("keyword", keyword);
        model.addAttribute("role", role);
        model.addAttribute("status", status);
        model.addAttribute("roles",EmployeeRole.values());
        model.addAttribute("statuses",EmployeeStatus.values());

        return "backoffice/admin/system/employees";
    }

}
