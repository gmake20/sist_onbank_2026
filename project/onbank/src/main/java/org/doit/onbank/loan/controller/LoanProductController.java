package org.doit.onbank.loan.controller;

import java.util.List;
import java.util.Map;

import org.doit.onbank.loan.dto.LoanProductListDTO;
import org.doit.onbank.loan.entity.LoanProduct;
import org.doit.onbank.loan.service.LoanProductService;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/loan")
@RequiredArgsConstructor
public class LoanProductController {

    private final LoanProductService loanProductService;

    private static final Map<String, String> CATEGORY_NAMES = Map.of(
            "credit", "신용대출",
            "mortgage", "담보대출",
            "jeonse", "전월세/반환보증",
            "auto", "자동차대출",
            "group", "집단중도금/이주비대출",
            "housing", "주택도시기금대출",
            "business", "개인사업자대출"
    );

    // 모든 대출 카테고리 목록
    @GetMapping("/categories/{category}")
    public String categoryProducts(
            @PathVariable("category") String category,
            Model model
    ) {

        loanProductService.validateCategory(category);

        List<LoanProduct> products =
                loanProductService.findByCategory(category);

        model.addAttribute("products", products);
        model.addAttribute("categoryKey", category);
        model.addAttribute(
                "categoryTitle",
                CATEGORY_NAMES.get(category)
        );

        return "loan/products";
    }

    // 모든 대출 카테고리 API
    @GetMapping("/categories/api/{category}")
    @ResponseBody
    public List<LoanProductListDTO> categoryApi(
            @PathVariable("category") String category
    ) {
        return loanProductService.getProductList(category);
    }

    // 모든 대출 상품 상세
    @GetMapping("/categories/{category}/{id}")
    public String categoryDetail(
            @PathVariable("category") String category,
            @PathVariable("id") Long id,
            Model model
    ) {

        LoanProduct product =
                loanProductService.findDetail(category, id);

        String listUrl =
                "/loan/categories/" + category;

        model.addAttribute("product", product);
        model.addAttribute("categoryKey", category);
        model.addAttribute(
                "categoryTitle",
                CATEGORY_NAMES.get(category)
        );
        model.addAttribute("listUrl", listUrl);

        return "loan/product-detail";
    }
}