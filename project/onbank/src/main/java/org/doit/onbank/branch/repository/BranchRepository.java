package org.doit.onbank.branch.repository;

import java.util.List;

import org.doit.onbank.branch.entity.Branch;
import org.doit.onbank.branch.entity.BranchType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BranchRepository extends JpaRepository<Branch, Long> {

	// 사용 중인 지점(또는 ATM) 목록, 이름순
	// 예) 직원 계정 발급 화면의 소속 지점 선택 : findByBranchTypeAndInUseTrueOrderByNameAsc(BranchType.BRANCH)
	List<Branch> findByBranchTypeAndInUseTrueOrderByNameAsc(BranchType branchType);
}
