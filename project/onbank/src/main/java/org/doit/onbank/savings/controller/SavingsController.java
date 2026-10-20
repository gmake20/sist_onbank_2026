package org.doit.onbank.savings.controller;
import lombok.RequiredArgsConstructor;
import org.doit.onbank.savings.service.SavingsService;
import org.doit.onbank.member.security.LoginMember;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
@Controller
@RequiredArgsConstructor
@RequestMapping("/savings")
public class SavingsController {
	
	private final SavingsService savings;
	
	@GetMapping("/products")
	public String products(@AuthenticationPrincipal LoginMember me,Model model){
		if (me==null) 
			return "redirect:/login";
		
		model.addAttribute("products",savings.products());
		
		return "savings/products";
	}
	
	@GetMapping("/mine") 
	public String mine(@AuthenticationPrincipal LoginMember me,Model model){
		if(me==null) return "redirect:/login";
		
		model.addAttribute("accounts",savings.mine(me.getMemberId()));
		
		return "savings/mine";
	}
	
	@GetMapping("/{id}") 
	public String detail(@PathVariable Long id,@AuthenticationPrincipal LoginMember me,Model model){
		
		if (me==null) return "redirect:/login";
		
		model.addAttribute("account",savings.detail(id,me.getMemberId()));
		model.addAttribute("payments",savings.history(id,me.getMemberId()));
		
		return "savings/detail";
	}
	
	@PostMapping("/enroll")
	public String enroll(@AuthenticationPrincipal LoginMember me,@RequestParam Long productId,@RequestParam Long sourceAccountId,@RequestParam long amount,RedirectAttributes ra){
		if (me==null) return "redirect:/login";
		
		try {
			Long id=savings.enroll(productId,me.getMemberId(),sourceAccountId,amount);
			ra.addFlashAttribute("message","가입이 완료되었습니다.");
			
			return "redirect:/savings/"+id;
		}
		
		catch(IllegalArgumentException|IllegalStateException|ArithmeticException e){
			ra.addFlashAttribute("error",e.getMessage());
			return "redirect:/savings/products";
		}
	}
	
	@PostMapping("/{id}/pay") 
	public String pay(@PathVariable Long id,@AuthenticationPrincipal LoginMember me,RedirectAttributes ra){
		if (me==null) return "redirect:/login";
		
		try{
			savings.pay(id,me.getMemberId());
			ra.addFlashAttribute("message","납입되었습니다.");
		}
		catch(IllegalArgumentException|IllegalStateException|ArithmeticException e){
			ra.addFlashAttribute("error",e.getMessage());
		}
		
		return "redirect:/savings/"+id;
	}
	
	@PostMapping("/{id}/close") 
	public String close(@PathVariable Long id,@AuthenticationPrincipal LoginMember me,RedirectAttributes ra){
		if (me==null) return "redirect:/login";
		
		try {
			long amt=savings.closeAtMaturity(id,me.getMemberId());
			ra.addFlashAttribute("message","만기 해지가 완료되었습니다. 지급 금액: "+amt+"원");
		}
		catch(IllegalArgumentException|IllegalStateException|ArithmeticException e){
			ra.addFlashAttribute("error",e.getMessage());
		}
		return "redirect:/savings/"+id;
	}
	
	@GetMapping({"", "/"})
	public String savingsHome() {
	    return "savings/index";
	}
}
