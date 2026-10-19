
package org.doit.onbank.loan.controller.employee;

import java.util.List;

import org.doit.onbank.account.entity.Account;
import org.doit.onbank.account.repository.AccountRepository;
import org.doit.onbank.employee.security.EmployeePrincipal;
import org.doit.onbank.loan.entity.LoanApplication;
import org.doit.onbank.loan.entity.LoanApplicationStatus;
import org.doit.onbank.loan.repository.LoanApplicationRepository;
import org.doit.onbank.loan.service.LoanExecutionService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
@RequestMapping("/employee/loans")
@PreAuthorize("hasAnyRole('STAFF', 'MANAGER')")
public class LoanReviewPageController {

    private final LoanApplicationRepository applicationRepository;
    private final LoanExecutionService loanExecutionService;
    private final AccountRepository accountRepository;

    
    @GetMapping("/review")
    public String reviewPage(
            @RequestParam(name = "id", required = false)
            Long id,

            @RequestParam(name = "status", required = false)
            LoanApplicationStatus status,

            @AuthenticationPrincipal
            EmployeePrincipal employee,

            Model model) {

        // 1. 직원 로그인 정보 확인
        if (employee == null) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "은행 직원 로그인이 필요합니다."
            );
        }

        // 2. 대출 신청 목록 조회
        List<LoanApplication> all =
                applicationRepository
                        .findAllByOrderByCreatedAtDesc();

        // 3. 상태별 필터링
        List<LoanApplication> filtered =
                status == null
                    ? all
                    : all.stream()
                        .filter(application ->
                                application.getStatus() == status
                        )
                        .toList();

        // 4. 선택한 대출 신청 상세 조회
        LoanApplication selected = null;

        if (id != null) {

            selected = all.stream()
                    .filter(application ->
                            id.equals(application.getId())
                    )
                    .findFirst()
                    .orElseThrow(() ->
                            new ResponseStatusException(
                                    HttpStatus.NOT_FOUND,
                                    "대출 신청이 존재하지 않습니다."
                            )
                    );
        }

        // 5. 신청 목록 및 상세 정보
        model.addAttribute("applications", filtered);
        model.addAttribute("selected", selected);

        // 6. 현재 필터 상태
        model.addAttribute("currentStatus", status);
        model.addAttribute(
                "statusValues",
                LoanApplicationStatus.values()
        );

        // 7. 상태별 건수
        model.addAttribute("totalCount", all.size());

        model.addAttribute(
                "pendingCount",
                count(all, LoanApplicationStatus.PENDING)
        );

        model.addAttribute(
                "reviewingCount",
                count(all, LoanApplicationStatus.REVIEWING)
        );

        model.addAttribute(
                "additionalCount",
                count(all, LoanApplicationStatus.ADDITIONAL_INFO)
        );

        model.addAttribute(
                "approvedCount",
                count(all, LoanApplicationStatus.APPROVED)
        );

        model.addAttribute(
                "rejectedCount",
                count(all, LoanApplicationStatus.REJECTED)
        );

        // 8. 직원 정보
        model.addAttribute("employeeName", employee.getName());
        model.addAttribute("employeeRole", employee.getRole());
        model.addAttribute("employeeId", employee.getEmployeeId());

        // 9. 페이지 반환
        return "loan/employee-loan-review";
    }

    // 상태별 건수 계산
    private long count(
            List<LoanApplication> applications,
            LoanApplicationStatus status) {

        return applications.stream()
                .filter(application ->
                        application.getStatus() == status
                )
                .count();
    }

    @GetMapping("/execute/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    public String executePage(
            @PathVariable("id") Long id,
            Model model) {

        LoanApplication application =
                loanExecutionService.findApprovedApplication(id);

        Account account = application.getDisbursementAccount();
        
        model.addAttribute("loanApplication", application);
        model.addAttribute("recipientAccount", account);

        return "employee/loan/employee-loan-execute";
    }
    
    @PostMapping("/execute/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    public String executeLoan(
            @PathVariable("id") Long id,
            @AuthenticationPrincipal EmployeePrincipal employee,
            RedirectAttributes redirectAttributes) {

        if (employee == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "직원 로그인이 필요합니다."
            );
        }

        
        loanExecutionService.execute(id, employee.getEmployeeId());

        redirectAttributes.addFlashAttribute(
                "successMessage",
                "대출 실행 요청이 처리되었습니다."
        );

        return "redirect:/employee/loans/review?id=" + id;
    }
}
