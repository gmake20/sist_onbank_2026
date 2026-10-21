package org.doit.onbank.branch.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import java.util.List;
import java.util.Set;

import org.doit.onbank.branch.dto.BranchView;
import org.doit.onbank.branch.entity.Branch;
import org.doit.onbank.branch.entity.BranchServiceType;
import org.doit.onbank.branch.entity.BranchType;
import org.doit.onbank.branch.repository.BranchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

// BranchService 단위 테스트 : DB 대신 가짜(Mock) 사용
@ExtendWith(MockitoExtension.class)
class BranchServiceTests {

	@Mock
	private BranchRepository branchRepository;

	@InjectMocks
	private BranchService branchService;

	@BeforeEach
	void setUp() {
		given(branchRepository.findAllInUseWithServices()).willReturn(List.of(
				branch(1L, BranchType.BRANCH, "모의은행 강남지점", "서울특별시 강남구 강남대로 일대", "0215880002", false,
						Set.of(BranchServiceType.DEPOSIT, BranchServiceType.ACCOUNT_OPENING)),
				branch(2L, BranchType.ATM, "모의은행 강남역 ATM", "서울특별시 강남구 강남대로 일대 (역사 내)", null, true,
						Set.of(BranchServiceType.WITHDRAW, BranchServiceType.DEPOSIT)),
				branch(3L, BranchType.BRANCH, "모의은행 판교지점", "경기도 성남시 분당구 판교역로 일대", "0315880004", false,
						Set.of(BranchServiceType.DEPOSIT))));
	}

	@Test
	void searchAllWithoutCondition() {
		assertThat(branchService.search(null, null, false, null)).hasSize(3);
	}

	@Test
	void searchByType() {
		assertThat(branchService.search(BranchType.ATM, null, false, null))
				.extracting(BranchView::branchId).containsExactly(2L);
	}

	// 이름·주소 어디에 있어도 찾고, 앞뒤 공백은 무시
	@Test
	void searchByKeywordInNameOrAddress() {
		assertThat(branchService.search(null, " 강남 ", false, null))
				.extracting(BranchView::branchId).containsExactly(1L, 2L);
		assertThat(branchService.search(null, "분당구", false, null))
				.extracting(BranchView::branchId).containsExactly(3L);
	}

	@Test
	void searchOpen24hOnly() {
		assertThat(branchService.search(null, null, true, null))
				.extracting(BranchView::branchId).containsExactly(2L);
	}

	@Test
	void searchByService() {
		assertThat(branchService.search(null, null, false, BranchServiceType.ACCOUNT_OPENING))
				.extracting(BranchView::branchId).containsExactly(1L);
	}

	// 화면 표시용 : 구분 이름, '-' 넣은 전화번호, 업무는 enum 선언 순서의 한글 이름
	@Test
	void viewForScreen() {
		List<BranchView> result = branchService.search(null, null, false, null);

		BranchView gangnam = result.get(0);
		assertThat(gangnam.typeLabel()).isEqualTo("지점");
		assertThat(gangnam.phone()).isEqualTo("02-1588-0002");
		assertThat(gangnam.services()).containsExactly("입금", "계좌개설");

		assertThat(result.get(1).phone()).isNull();
		assertThat(result.get(1).services()).containsExactly("입금", "출금");
		assertThat(result.get(2).phone()).isEqualTo("031-588-0004");
	}

	private static Branch branch(Long id, BranchType type, String name, String address, String phone, boolean open24h,
			Set<BranchServiceType> services) {
		Branch branch = BeanUtils.instantiateClass(Branch.class);	// JPA용 기본 생성자 (protected)
		ReflectionTestUtils.setField(branch, "branchId", id);
		ReflectionTestUtils.setField(branch, "branchType", type);
		ReflectionTestUtils.setField(branch, "name", name);
		ReflectionTestUtils.setField(branch, "address", address);
		ReflectionTestUtils.setField(branch, "latitude", 37.5);
		ReflectionTestUtils.setField(branch, "longitude", 127.0);
		ReflectionTestUtils.setField(branch, "phone", phone);
		ReflectionTestUtils.setField(branch, "businessHours", open24h ? "24시간" : "평일 09:00~16:00");
		ReflectionTestUtils.setField(branch, "open24h", open24h);
		ReflectionTestUtils.setField(branch, "services", services);
		return branch;
	}
}
