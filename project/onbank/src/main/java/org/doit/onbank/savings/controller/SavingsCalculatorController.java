package org.doit.onbank.savings.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SavingsCalculatorController {

    @GetMapping("/savings/calculator")
    public String calculator() {
        return "savings/calculator";
    }
}
