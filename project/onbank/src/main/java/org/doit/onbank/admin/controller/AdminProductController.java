package org.doit.onbank.admin.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.doit.onbank.admin.dto.ProductEditForm;
import org.doit.onbank.admin.dto.ProductForm;
import org.doit.onbank.admin.security.AdminPrincipal;
import org.doit.onbank.admin.service.AdminProductService;
import org.doit.onbank.common.exception.BusinessException;
import org.doit.onbank.savings.entity.SavingsProduct;
import org.doit.onbank.savings.entity.SavingsType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Map;

// 상품 관리 S-10·S-11 (PRD-001~003). SUPER_ADMIN·SERVICE (사이드바 상품 관리 메뉴와 같게)
@Controller
@RequestMapping("/admin/products")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN','SERVICE')")
public class AdminProductController {

    private final AdminProductService adminProductService;

    // 판매중지·다시 판매 후 목록으로 돌아갈 때 그대로 붙여 줄 검색 조건 (모달 form 의 hidden 칸)
    private static final List<String> SEARCH_KEYS = List.of("name", "type", "active", "page");

    // S-10 상품 목록. select 에서 "전체"(빈 값)를 고르면 enum·Boolean 은 null 로 들어온다
    @GetMapping
    public String list(@RequestParam(name = "name", required = false) String name,
                       @RequestParam(name = "type", required = false) SavingsType type,
                       @RequestParam(name = "active", required = false) Boolean active,
                       @RequestParam(name = "page", defaultValue = "0") int page,
                       Model model) {
        model.addAttribute("products", adminProductService.search(name, type, active, page));
        model.addAttribute("name", name);
        model.addAttribute("type", type);
        model.addAttribute("active", active);
        model.addAttribute("types", SavingsType.values());
        model.addAttribute("typeLabels", AdminProductService.TYPE_LABELS);

        return "backoffice/admin/product/products";
    }

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
        } catch (IllegalArgumentException e) {        // 최대 금액 < 최소 금액 → 최대 금액 칸 아래에 표시
            bindingResult.rejectValue("maxAmount", "underMinAmount", e.getMessage());
        } catch (IllegalStateException e) {           // 같은 이름 판매중 상품 → 상품명 칸 아래에 표시
            bindingResult.rejectValue("productName", "duplicate", e.getMessage());
        }
        return "backoffice/admin/product/product-new";
    }

    // S-11 상품 수정 폼. 없는 상품이면 목록으로 돌려보내며 안내
    @GetMapping("/{productId}/edit")
    public String editForm(@PathVariable("productId") Long productId, Model model, RedirectAttributes rttr) {
        SavingsProduct product;
        try {
            product = adminProductService.product(productId);
        } catch (BusinessException e) {
            rttr.addFlashAttribute("error", e.getMessage());
            return "redirect:/admin/products";
        }
        model.addAttribute("form", ProductEditForm.from(product));
        model.addAttribute("product", product);
        model.addAttribute("typeLabels", AdminProductService.TYPE_LABELS);
        return "backoffice/admin/product/product-edit";
    }

    // S-11 수정 저장. 성공하면 목록으로 (PRG)
    @PostMapping("/{productId}/edit")
    public String edit(@AuthenticationPrincipal AdminPrincipal me, @PathVariable("productId") Long productId,
                       @Valid @ModelAttribute("form") ProductEditForm form, BindingResult bindingResult,
                       HttpServletRequest request, Model model, RedirectAttributes rttr) {
        try {
            if (!bindingResult.hasErrors()) {
                adminProductService.updateProduct(me, productId, form, request.getRemoteAddr());
                rttr.addFlashAttribute("message", "상품을 수정했습니다.");
                return "redirect:/admin/products";
            }
        } catch (IllegalArgumentException e) {        // 최대 금액 < 최소 금액 → 최대 금액 칸 아래에 표시
            bindingResult.rejectValue("maxAmount", "underMinAmount", e.getMessage());
        } catch (IllegalStateException e) {           // 같은 이름 판매중 상품 → 상품명 칸 아래에 표시
            bindingResult.rejectValue("productName", "duplicate", e.getMessage());
        } catch (BusinessException e) {               // 없는 상품
            rttr.addFlashAttribute("error", e.getMessage());
            return "redirect:/admin/products";
        }
        // 다시 그릴 때 유형·가입기간 표시용 (지금 DB 값)
        model.addAttribute("product", adminProductService.product(productId));
        model.addAttribute("typeLabels", AdminProductService.TYPE_LABELS);
        return "backoffice/admin/product/product-edit";
    }

    // 판매중지. 사유는 활동 로그에 남는다
    @PostMapping("/{productId}/stop")
    public String stop(@AuthenticationPrincipal AdminPrincipal me, @PathVariable("productId") Long productId,
                       @RequestParam("reason") String reason, @RequestParam Map<String, String> params,
                       HttpServletRequest request, RedirectAttributes rttr) {
        try {
            adminProductService.stop(me, productId, reason, request.getRemoteAddr());
            rttr.addFlashAttribute("message", "판매를 중지했습니다.");
        } catch (BusinessException e) {
            rttr.addFlashAttribute("error", e.getMessage());
        }
        return backToList(params, rttr);
    }

    // 다시 판매
    @PostMapping("/{productId}/resume")
    public String resume(@AuthenticationPrincipal AdminPrincipal me, @PathVariable("productId") Long productId,
                         @RequestParam("reason") String reason, @RequestParam Map<String, String> params,
                         HttpServletRequest request, RedirectAttributes rttr) {
        try {
            adminProductService.resume(me, productId, reason, request.getRemoteAddr());
            rttr.addFlashAttribute("message", "다시 판매합니다.");
        } catch (BusinessException e) {
            rttr.addFlashAttribute("error", e.getMessage());
        }
        return backToList(params, rttr);
    }

    // 보던 검색 조건·페이지 그대로 목록으로 (PRG). AdminAccountController 와 같은 방식
    private String backToList(Map<String, String> params, RedirectAttributes rttr) {
        for (String key : SEARCH_KEYS) {
            String value = params.get(key);
            if (value != null && !value.isBlank()) {
                rttr.addAttribute(key, value);
            }
        }
        return "redirect:/admin/products";
    }
}
