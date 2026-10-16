package org.doit.onbank.loan.controller.admin;

import java.util.List;
import org.doit.onbank.loan.entity.LoanApplication;
import org.doit.onbank.loan.entity.LoanApplicationStatus;
import org.doit.onbank.loan.repository.LoanApplicationRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
@RequestMapping("/admin/loans")
@PreAuthorize("hasRole('ADMIN')")
public class LoanReviewPageController {
	private final LoanApplicationRepository applicationRepository;

	@GetMapping("/review")
	public String reviewPage(
			@RequestParam(name = "id", required = false) Long id,
			@RequestParam(name = "status", required = false) LoanApplicationStatus status,
			Model model) {
		List<LoanApplication> all = applicationRepository.findAllByOrderByCreatedAtDesc();
		List<LoanApplication> filtered = status == null ? all : all.stream()
				.filter(a -> a.getStatus() == status).toList();
		LoanApplication selected = null;
		if (id != null) {
			selected = all.stream().filter(a -> id.equals(a.getId())).findFirst()
					.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
							"대출 신청이 존재하지 않습니다."));
		}
		model.addAttribute("applications", filtered);
		model.addAttribute("selected", selected);
		model.addAttribute("currentStatus", status);
		model.addAttribute("statusValues", LoanApplicationStatus.values());
		model.addAttribute("totalCount", all.size());
		model.addAttribute("pendingCount", count(all, LoanApplicationStatus.PENDING));
		model.addAttribute("reviewingCount", count(all, LoanApplicationStatus.REVIEWING));
		model.addAttribute("additionalCount", count(all, LoanApplicationStatus.ADDITIONAL_INFO));
		model.addAttribute("approvedCount", count(all, LoanApplicationStatus.APPROVED));
		return "loan/admin-loan-review";
	}

	private long count(List<LoanApplication> items, LoanApplicationStatus status) {
		return items.stream().filter(a -> a.getStatus() == status).count();
	}
}
