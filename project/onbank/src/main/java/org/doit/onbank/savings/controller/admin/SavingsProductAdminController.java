package org.doit.onbank.savings.controller.admin;

import org.doit.onbank.savings.dto.SavingsProductCreateForm;
import org.doit.onbank.savings.entity.SavingsType;
import org.doit.onbank.savings.service.admin.SavingsProductAdminService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
@RequestMapping("/admin/savings/products")
@PreAuthorize("hasRole('ADMIN')")
public class SavingsProductAdminController {
	
    private final SavingsProductAdminService service;

    @ModelAttribute("types")
    public SavingsType[] types() {
        return SavingsType.values();
    }

    @GetMapping("/new")
    public String form(Model model) {
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new SavingsProductCreateForm());
        }
        return "admin/savings/product-register";
    }

    @PostMapping
    public String register(
            @ModelAttribute("form") SavingsProductCreateForm form,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            model.addAttribute("error", "입력값 형식을 확인하세요. 숫자 항목에는 숫자만 입력해야 합니다.");
            return "admin/savings/product-register";
        }
        try {
            Long id = service.register(form);
            redirectAttributes.addFlashAttribute("message", "예적금 상품 등록이 완료되었습니다. 상품 ID: " + id);
            return "redirect:/admin/savings/products/new";
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
            return "admin/savings/product-register";
        }
    }
}
