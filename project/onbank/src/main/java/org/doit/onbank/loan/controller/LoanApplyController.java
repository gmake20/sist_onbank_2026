
package org.doit.onbank.loan.controller;

import org.doit.onbank.loan.dto.LoanApplicationForm;
import org.doit.onbank.loan.service.LoanApplicationService;
import org.doit.onbank.loan.service.LoanProductService;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
@RequestMapping("/loan/apply")
public class LoanApplyController {

    private final LoanProductService loanProductService;
    private final LoanApplicationService applicationService;

    private String username(Authentication authentication) {

        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return null;
        }

        return authentication.getName();
    }

    // 대출 신청서 화면
    @GetMapping
    public String applyPage(
            @RequestParam("productId") Long productId,
            Authentication authentication,
            Model model
    ) {
        
        if (authentication == null ) {
        	return "redirect:/login";
        }

        model.addAttribute(
                "product",
                loanProductService.findById(productId)
        );

        LoanApplicationForm form = new LoanApplicationForm();
        form.setProductId(productId);

        model.addAttribute("loanApplicationForm", form);

        return "loan/loan-apply";
    }

    // 대출 신청서 저장
    @PostMapping
    public String apply(
            @Valid @ModelAttribute("loanApplicationForm")
            LoanApplicationForm form,
            BindingResult bindingResult,
            Authentication authentication,
            Model model
    ) {
    	 if (authentication == null ) {
          	return "redirect:/login";
          }

        String username = authentication.getName();

        if (form.getProductId() == null) {
            bindingResult.rejectValue(
                    "productId",
                    "required",
                    "상품을 선택하세요."
            );
            return "loan/loan-apply";
        }

        var product = loanProductService.findById(
                form.getProductId()
        );
        model.addAttribute("product", product);

        if (bindingResult.hasErrors()) {
            return "loan/loan-apply";
        }

        if (product.getMaxTermMonths() != null
                && form.getRequestedMonths() != null
                && form.getRequestedMonths()
                        > product.getMaxTermMonths()) {

            bindingResult.rejectValue(
                    "requestedMonths",
                    "maxTerm",
                    "해당 상품의 최대 대출 기간을 초과했습니다."
            );

            return "loan/loan-apply";
        }

        Long applicationId =
                applicationService.apply(form, username);

        return "redirect:/loan/apply/complete/" + applicationId;
    }

    // 신청 접수 완료
    @GetMapping("/complete/{id}")
    public String complete(
            @PathVariable("id") Long id,
            Authentication authentication,
            Model model
    ) {

        if (authentication == null ) {
        	return "redirect:/login";
        }
        
        String username = authentication.getName();

        var application =
                applicationService.findMine(id, username);

        model.addAttribute("application", application);

        return "loan/loan-apply-complete";
    }
}

