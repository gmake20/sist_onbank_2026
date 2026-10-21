package org.doit.onbank.branch.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.doit.onbank.branch.entity.Branch;
import org.doit.onbank.branch.entity.BranchType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

// 공용 DB의 branch 시드 데이터(data-team1-mysql.sql)를 읽기만 한다
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class BranchRepositoryTests {

	@Autowired
	private BranchRepository branchRepository;

	@Test
	void findBranchesInUseOrderByName() {
		List<Branch> branches = branchRepository.findByBranchTypeAndInUseTrueOrderByNameAsc(BranchType.BRANCH);

		assertThat(branches).isNotEmpty();
		assertThat(branches).allMatch(b -> b.getBranchType() == BranchType.BRANCH && b.isInUse());
		assertThat(branches).extracting(Branch::getName).isSorted();
	}

	// branch_service 를 Branch.services 로 읽을 수 있다 (시드 데이터에 업무가 등록된 지점이 있음)
	@Test
	void loadServices() {
		List<Branch> all = branchRepository.findAll();

		assertThat(all).anyMatch(b -> !b.getServices().isEmpty());
	}

	// 지점/ATM 찾기 : 사용 중인 지점·ATM 모두, 이름순, 업무가 여러 개여도 지점이 중복되지 않음
	@Test
	void findAllInUseWithServices() {
		List<Branch> branches = branchRepository.findAllInUseWithServices();

		assertThat(branches).isNotEmpty().allMatch(Branch::isInUse).doesNotHaveDuplicates();
		assertThat(branches).extracting(Branch::getBranchType).contains(BranchType.BRANCH, BranchType.ATM);
		assertThat(branches).extracting(Branch::getName).isSorted();
		assertThat(branches).anyMatch(b -> !b.getServices().isEmpty());
	}
}
