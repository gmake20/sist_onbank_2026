package org.doit.onbank.savings.controller;

import java.util.List;

import org.doit.onbank.account.entity.Account;
import org.doit.onbank.account.entity.AccountStatus;
import org.doit.onbank.account.entity.AccountType;
import org.doit.onbank.account.repository.AccountRepository;
import org.doit.onbank.member.security.LoginMember;
import org.doit.onbank.savings.entity.SavingsProduct;
import org.doit.onbank.savings.repository.SavingsProductRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ResponseStatusException;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
@RequestMapping("/savings/products")
public class SavingsProductDetailController {

    private final SavingsProductRepository productRepository;
    private final AccountRepository accountRepository;

    @GetMapping("/{id}")
    public String productDetail(
            @PathVariable Long id,
            @AuthenticationPrincipal LoginMember member,
            Model model) {

        SavingsProduct product = productRepository
                .findById(id)
                .filter(SavingsProduct::isActive)
                .orElseThrow(() ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "판매 중인 예적금 상품이 없습니다."
                    )
                );

        model.addAttribute("product", product);

        if (member == null) {
            model.addAttribute("myAccounts", List.of());
            return "savings/product-detail";
        }

        // 본인 계좌만 조회
        List<Account> accounts = accountRepository
                .findByMemberId(member.getMemberId());

        // 정상 상태의 입출금계좌만 표시
        List<Account> myAccounts = accounts.stream()
                .filter(account ->
                    "CHECKING".equals(account.getAccountType()) )
                .filter(account ->
                    "ACTIVE".equals(
                        account.getAccountStatus()))
                .toList();

        model.addAttribute("myAccounts", myAccounts);

        return "savings/product-detail";
    }
}
