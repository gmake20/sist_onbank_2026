package org.doit.onbank.branch.service;

import java.util.List;

import org.doit.onbank.branch.dto.BranchView;
import org.doit.onbank.branch.entity.BranchServiceType;
import org.doit.onbank.branch.entity.BranchType;
import org.doit.onbank.branch.repository.BranchRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

// 지점/ATM 찾기 (비회원도 사용)
// 지점·ATM은 수십 건 수준이라 사용 중인 전체를 읽고 조건 필터는 Java에서 한다
// (건수가 많아지면 Repository 검색 쿼리로 옮긴다)
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BranchService {

	private final BranchRepository branchRepository;

	// 조건은 모두 선택 : type·service 가 null 이면 전체, keyword 는 이름·주소에 포함, open24hOnly 면 24시간 운영만
	public List<BranchView> search(BranchType type, String keyword, boolean open24hOnly, BranchServiceType service) {
		String word = keyword == null ? "" : keyword.trim().toLowerCase();
		return branchRepository.findAllInUseWithServices().stream()
				.filter(b -> type == null || b.getBranchType() == type)
				.filter(b -> word.isEmpty() || contains(b.getName(), word) || contains(b.getAddress(), word))
				.filter(b -> !open24hOnly || b.isOpen24h())
				.filter(b -> service == null || b.getServices().contains(service))
				.map(BranchView::from)
				.toList();
	}

	private static boolean contains(String text, String word) {
		return text != null && text.toLowerCase().contains(word);
	}
}
