package org.doit.onbank.loan.controller;

import org.doit.onbank.loan.dto.LoanProductForm;
import org.doit.onbank.loan.service.LoanProductService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
@RequestMapping("/loan/products")
public class LoanProductPageController {

    private final LoanProductService loanProductService;

    // 1. 상품 목록
    @GetMapping
    public String list(
            @RequestParam(name = "keyword", required = false)
            String keyword,
            Model model) {

        model.addAttribute(
            "products",
            loanProductService.findProducts(keyword)
        );

        model.addAttribute("keyword", keyword);
        model.addAttribute("activeCategory", "신용대출");

        return "loan/products";
    }

    // 2. 상품 상세
    @GetMapping("/{id}")
    public String detail(
            @PathVariable("id") Long id,
            Model model) {

        model.addAttribute(
            "product",
            loanProductService.findById(id)
        );

        model.addAttribute("activeCategory", "신용대출");

        return "loan/product-detail";
    }

    // 3. 상품 등록 화면
    @GetMapping("/new")
    public String registerPage(Model model) {

        model.addAttribute(
            "loanProductForm",
            new LoanProductForm()
        );

        return "loan/product-register";
    }

    // 4. 상품 등록 처리
    @PostMapping
    public String register(
            @Valid @ModelAttribute("loanProductForm")
            LoanProductForm form,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            return "loan/product-register";
        }

        Long productId = loanProductService.register(form);

        redirectAttributes.addFlashAttribute(
            "message",
            "대출 상품이 등록되었습니다."
        );

        return "redirect:/loan/products/" + productId;
    }
}