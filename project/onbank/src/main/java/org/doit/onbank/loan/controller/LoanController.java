package org.doit.onbank.loan.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import lombok.extern.log4j.Log4j2;

@Controller
@RequestMapping("/loan")
@Log4j2
public class LoanController {
	
	@GetMapping
	public String index() {
		log.info("LoanController.index");
		return "loan/index";
	}
}
