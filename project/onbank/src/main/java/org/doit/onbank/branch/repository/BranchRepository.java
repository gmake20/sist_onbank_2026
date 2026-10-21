package org.doit.onbank.branch.repository;

import java.util.List;

import org.doit.onbank.branch.entity.Branch;
import org.doit.onbank.branch.entity.BranchType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface BranchRepository extends JpaRepository<Branch, Long> {

	// 사용 중인 지점(또는 ATM) 목록, 이름순
	// 예) 직원 계정 발급 화면의 소속 지점 선택 : findByBranchTypeAndInUseTrueOrderByNameAsc(BranchType.BRANCH)
	List<Branch> findByBranchTypeAndInUseTrueOrderByNameAsc(BranchType branchType);

	// 지점/ATM 찾기 : 사용 중인 전체 목록 + 이용 가능 업무 (업무를 지점마다 따로 읽지 않도록 한 번에 fetch join)
	@Query("select distinct b from Branch b left join fetch b.services where b.inUse = true order by b.name")
	List<Branch> findAllInUseWithServices();
}
