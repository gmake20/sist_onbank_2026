package org.doit.onbank.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

	// 예전 목업 주소 → 실제 기능 화면으로 이동 (예전 링크가 깨지지 않도록 남겨 둠)
	// 실제 화면 : account/controller/AccountPageController, TransferPageController
	@Controller
	@RequestMapping("/account")
	public class AccountController {

	    // /account/list → 내 계좌 목록
	    @GetMapping("/list")
	    public String accountList() {
	        return "redirect:/mybank/accounts";
	    }

	    // /account/transfer → 이체 화면
	    @GetMapping("/transfer")
	    public String transfer() {
	    	return "redirect:/mybank/transfer";
	    }
	}
