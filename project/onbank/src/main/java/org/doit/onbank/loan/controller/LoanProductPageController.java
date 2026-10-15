
package org.doit.onbank.loan.controller;

import org.doit.onbank.loan.dto.LoanProductForm;
import org.doit.onbank.loan.entity.LoanProduct;
import org.doit.onbank.loan.service.LoanProductService;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
@RequestMapping("/loan/products")
public class LoanProductPageController {

    private final LoanProductService loanProductService;

    @GetMapping("/register")
    public String registerForm(Model model) {

        model.addAttribute(
                "loanProductForm",
                new LoanProductForm()
        );

        return "loan/product-register";
    }

    // 대출 상품 등록 처리
    @PostMapping("/register")
    public String register(
            @Valid LoanProductForm form,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes
    ) {

        if (bindingResult.hasErrors()) {
            return "loan/product-register";
        }

        // 카테고리 검증
        loanProductService.validateCategory(form.getCategory());

        // 상품 저장
        // 실제 서비스의 등록 메서드로 변경
        loanProductService.register(form);

        // 등록 완료 메시지
        redirectAttributes.addFlashAttribute(
                "message",
                "대출 상품이 등록되었습니다."
        );

        // 등록된 카테고리 목록으로 이동
        return "redirect:/loan/categories/" + form.getCategory();
    }
}
