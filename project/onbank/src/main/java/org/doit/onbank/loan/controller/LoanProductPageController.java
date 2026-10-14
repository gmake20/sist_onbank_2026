package org.doit.onbank.loan.controller;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/loan/products")
public class LoanProductPageController {

    private static final List<Map<String, Object>> PRODUCTS = List.of(
        Map.of("id", 1L, "name", "ON 직장인 신용대출", "subtitle", "재직 중인 고객을 위한 간편한 금융 서비스", "target", "직장인", "limit", "최대 1억원", "method", "원리금균등·원금균등", "tag", "직장인"),
        Map.of("id", 2L, "name", "ON 비상금대출", "subtitle", "일상에서 필요한 자금을 간편하게 확인하세요", "target", "개인", "limit", "최대 300만원", "method", "만기일시상환", "tag", "소액대출"),
        Map.of("id", 3L, "name", "ON 생활안정 신용대출", "subtitle", "생활자금 마련을 위한 신용대출 상품", "target", "직장인·개인", "limit", "상품별 심사", "method", "원리금균등", "tag", "생활자금")
    );

    @GetMapping
    public String list(Model model) {
        model.addAttribute("products", PRODUCTS);
        model.addAttribute("activeCategory", "신용대출");
        return "loan/products";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable("id") Long id, Model model) {
        var product = PRODUCTS.stream().filter(p -> p.get("id").equals(id)).findFirst()
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "상품을 찾을 수 없습니다."));
        model.addAttribute("product", product);
        model.addAttribute("activeCategory", "신용대출");
        return "loan/product-detail";
    }
}
