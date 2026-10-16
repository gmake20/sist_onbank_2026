
package org.doit.onbank.loan.controller;

import org.doit.onbank.loan.dto.LoanApplicationForm;
import org.doit.onbank.loan.service.LoanApplicationService;
import org.doit.onbank.loan.service.LoanProductService;
import org.doit.onbank.member.security.LoginMember;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
@RequestMapping("/loan/apply")
public class LoanApplyController {

    private final LoanProductService loanProductService;
    private final LoanApplicationService applicationService;

    // 대출 신청서 화면
    @GetMapping
    public String applyPage(
            @RequestParam("productId") Long productId,
            @AuthenticationPrincipal LoginMember me,
            Model model
    ) {
        
        if (me == null ) {
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
            @AuthenticationPrincipal LoginMember me,
            Model model
    ) {
    	if (me == null) {
            return "redirect:/login";
        }

        // 2. 로그인 회원 ID
        Long memberId = me.getMemberId();

        System.out.println("로그인 회원 ID = " + memberId);

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
                applicationService.apply(form, memberId);

        return "redirect:/loan/apply/complete/" + applicationId;
    }

    // 신청 접수 완료
    @GetMapping("/complete/{id}")
    public String complete(
            @PathVariable("id") Long id,
            @AuthenticationPrincipal LoginMember me,
            Model model
    ) {

        if (me == null ) {
        	return "redirect:/login";
        }
        
        var application =
                applicationService.findMine(id, me.getMemberId());

        model.addAttribute("loanApplication", application);

        return "loan/loan-apply-complete";
    }
}

