package org.doit.onbank.admin.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.doit.onbank.admin.dto.ProductForm;
import org.doit.onbank.admin.security.AdminPrincipal;
import org.doit.onbank.admin.service.AdminProductService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

// 상품 관리 S-10·S-11 (PRD-001~003). SUPER_ADMIN·SERVICE (사이드바 상품 관리 메뉴와 같게)
@Controller
@RequestMapping("/admin/products")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN','SERVICE')")
public class AdminProductController {

    private final AdminProductService adminProductService;

    // S-11 상품 등록 폼
    @GetMapping("/new")
    public String createForm(Model model) {
        model.addAttribute("form", new ProductForm());
        return "backoffice/admin/product/product-new";
    }

    // S-11 저장. 성공하면 목록으로 리다이렉트(PRG)
    @PostMapping
    public String create(@AuthenticationPrincipal AdminPrincipal me,
                         @Valid @ModelAttribute("form") ProductForm form, BindingResult bindingResult,
                         HttpServletRequest request, RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            return "backoffice/admin/product/product-new";
        }
        try {
            adminProductService.createProduct(me, form, request.getRemoteAddr());
            redirectAttributes.addFlashAttribute("message", "상품이 등록되었습니다.");
            return "redirect:/admin/products";
        } catch (IllegalArgumentException e) {        // 중도해지 이율 > 기본금리 → 그 칸 아래에 표시
            bindingResult.rejectValue("earlyTerminationRate", "overBaseRate", e.getMessage());
        } catch (IllegalStateException e) {           // 같은 이름 상품 → 상품명 칸 아래에 표시
            bindingResult.rejectValue("productName", "duplicate", e.getMessage());
        } catch (UnsupportedOperationException e) {   // 임시 : ProductRepository 연결 전. 연결되면 이 catch 지우기
            bindingResult.reject("notReady", e.getMessage());
        }
        return "backoffice/admin/product/product-new";
    }
}
