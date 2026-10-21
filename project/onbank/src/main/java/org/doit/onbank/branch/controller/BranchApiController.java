package org.doit.onbank.branch.controller;

import java.util.List;

import org.doit.onbank.branch.dto.BranchView;
import org.doit.onbank.branch.entity.BranchServiceType;
import org.doit.onbank.branch.entity.BranchType;
import org.doit.onbank.branch.service.BranchService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

// 지점/ATM 찾기 화면(fetch)에서 호출하는 검색 API (비회원 공개, GET이라 CSRF 토큰 없음)
// 예) GET /api/branches?type=ATM&keyword=강남&open24h=true&service=DEPOSIT
@RestController
@RequestMapping("/api/branches")
@RequiredArgsConstructor
public class BranchApiController {

	private final BranchService branchService;

	@GetMapping
	public List<BranchView> search(@RequestParam(required = false) BranchType type,
			@RequestParam(required = false) String keyword,
			@RequestParam(defaultValue = "false") boolean open24h,
			@RequestParam(required = false) BranchServiceType service) {
		return branchService.search(type, keyword, open24h, service);
	}
}
