package org.doit.onbank.product.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class FinancialProductsController {
    @GetMapping("/financial-products")
    public String financialProducts() {
        return "financial-products";
    }
}
