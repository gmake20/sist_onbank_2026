package org.doit.onbank.savings.controller;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.doit.onbank.member.security.LoginMember;
import org.doit.onbank.savings.entity.SavingsAccount;
import org.doit.onbank.savings.repository.SavingsAccountRepository;
import org.doit.onbank.savings.service.SavingsScheduleViewService;
import org.doit.onbank.savings.service.SavingsService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
@Controller
@RequiredArgsConstructor
@RequestMapping("/savings")
public class SavingsController {

	private final SavingsService savings;
	private final SavingsScheduleViewService scheduleViewService;
	private final SavingsAccountRepository savingsAccountRepository;

	@GetMapping("/products")
	public String products(@AuthenticationPrincipal LoginMember me,Model model){
		if (me==null) 
			return "redirect:/login";

		model.addAttribute("products",savings.products());

		return "savings/products";
	}




	@GetMapping("/mine")
	@Transactional(readOnly = true)
	public String mine(
			@AuthenticationPrincipal LoginMember member,
			@RequestParam(defaultValue = "1") int page,
			@RequestParam(defaultValue = "1") int schedulePage,
			@RequestParam(required = false) Long accountId,
			HttpServletRequest request,
			Model model) {

		if (member == null) {
			return "redirect:/login";
		}

		Long memberId = member.getMemberId();

		// 1. 가입 계좌 페이징
		int pageNumber = Math.max(page, 1);

		Pageable pageable = PageRequest.of(
				pageNumber - 1,
				6,
				Sort.by(Sort.Direction.DESC, "id")
				);

		Page<SavingsAccount> accountPage =
				savingsAccountRepository.findByMemberId(
						memberId, pageable);

		int totalPages = accountPage.getTotalPages();

		if (totalPages > 0 && pageNumber > totalPages) {
			pageNumber = totalPages;

			accountPage = savingsAccountRepository.findByMemberId(
					memberId,
					PageRequest.of(
							pageNumber - 1,
							6,
							Sort.by(Sort.Direction.DESC, "id")
							)
					);
		}

		List<SavingsAccount> accounts =
				accountPage.getContent();

		model.addAttribute("accounts", accounts);
		model.addAttribute("currentPage", pageNumber);
		model.addAttribute("totalPages", totalPages);
		model.addAttribute("totalCount", accountPage.getTotalElements());

		// 2. 전체 원금
		long totalPrincipal =
				savingsAccountRepository.findByMemberId(memberId)
				.stream()
				.mapToLong(SavingsAccount::getPrincipal)
				.sum();

		model.addAttribute("totalPrincipal", totalPrincipal);

		LocalDate today =
				LocalDate.now(ZoneId.of("Asia/Seoul"));

		model.addAttribute("today", today);

		// 3. 선택 계좌
		SavingsAccount selected = null;

		if (accountId == null) {
			if (!accounts.isEmpty()) {
				selected = accounts.get(0);
			}
		} else {
			selected = savingsAccountRepository.findById(accountId)
					.filter(a -> memberId.equals(a.getMemberId()))
					.orElseThrow(() ->
					new AccessDeniedException(
							"조회 권한이 없는 예적금 계좌입니다."
							)
							);
		}

		model.addAttribute("selectedAccount", selected);

		// 4. 전체 납입 스케줄 계산
		SavingsScheduleViewService.ScheduleSummary summary =
				scheduleViewService.buildSummary(selected);

		model.addAttribute("scheduleSummary", summary);

		// 5. 적금 납입 스케줄 페이징
		int schedulePageSize = 5;

		List<SavingsScheduleViewService.ScheduleRow> allRows =
				summary.rows();

		int scheduleTotalPages =
				(allRows.size() + schedulePageSize - 1)
				/ schedulePageSize;

		int currentSchedulePage = Math.max(1, schedulePage);

		if (scheduleTotalPages > 0) {
			currentSchedulePage = Math.min(
					currentSchedulePage,
					scheduleTotalPages
					);
		}

		int start = Math.min(
				(currentSchedulePage - 1) * schedulePageSize,
				allRows.size()
				);

		int end = Math.min(
				start + schedulePageSize,
				allRows.size()
				);

		List<SavingsScheduleViewService.ScheduleRow> scheduleRows =
				allRows.subList(start, end);

		model.addAttribute("scheduleRows", scheduleRows);
		model.addAttribute("currentSchedulePage", currentSchedulePage);
		model.addAttribute("scheduleTotalPages", scheduleTotalPages);
		
		boolean ajax = "XMLHttpRequest".equals(
	            request.getHeader("X-Requested-With")
	    );

	    if (ajax) {
	        return "savings/mine :: ajaxContent";
	    }

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
