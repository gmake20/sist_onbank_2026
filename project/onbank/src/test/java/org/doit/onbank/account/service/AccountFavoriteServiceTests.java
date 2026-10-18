package org.doit.onbank.account.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import org.doit.onbank.account.dto.FavoriteGroups;
import org.doit.onbank.account.dto.RecipientInfo;
import org.doit.onbank.account.entity.Account;
import org.doit.onbank.account.entity.AccountFavorite;
import org.doit.onbank.account.entity.AccountStatus;
import org.doit.onbank.account.repository.AccountFavoriteRepository;
import org.doit.onbank.account.repository.AccountRepository;
import org.doit.onbank.account.repository.AccountTransferRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

// AccountFavoriteService 단위 테스트 : DB 대신 가짜(Mock) 사용
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)	// 공통 준비(setUp)를 쓰지 않는 테스트도 있음
class AccountFavoriteServiceTests {

	private static final Long MEMBER_ID = 100L;
	private static final Long OTHER_MEMBER_ID = 200L;
	private static final Long MY_ACCOUNT_ID = 1L;
	private static final String RECIPIENT_NUMBER = "1002-111-222222";

	@Mock
	private AccountRepository accountRepository;

	@Mock
	private AccountFavoriteRepository accountFavoriteRepository;

	@Mock
	private AccountTransferRepository accountTransferRepository;

	@Mock
	private AccountTransferService accountTransferService;

	@InjectMocks
	private AccountFavoriteService accountFavoriteService;

	private final List<Long> myAccountIds = List.of(MY_ACCOUNT_ID);

	@BeforeEach
	void setUp() {
		given(accountRepository.findByMemberIdOrderByAccountIdAsc(MEMBER_ID))
				.willReturn(List.of(account(MY_ACCOUNT_ID, MEMBER_ID, "1002-000-000001")));
		given(accountTransferService.findRecipient(RECIPIENT_NUMBER))
				.willReturn(new RecipientInfo(RECIPIENT_NUMBER, "김*수"));
		given(accountFavoriteRepository.save(any(AccountFavorite.class)))
				.willAnswer(invocation -> invocation.getArgument(0));
	}

	private Account account(Long id, Long memberId, String number) {
		Account account = Account.builder()
				.memberId(memberId)
				.accountNumber(number)
				.accountStatus(AccountStatus.ACTIVE)
				.balance(0L)
				.todayTransferAmount(0L)
				.build();
		ReflectionTestUtils.setField(account, "accountId", id);
		return account;
	}

	// ---------- 등록 ----------

	@Test
	void addSavesMaskedNameAndNextSortOrder() {
		given(accountFavoriteRepository.findMaxSortOrder(myAccountIds)).willReturn(3);

		AccountFavorite favorite = accountFavoriteService.add(MEMBER_ID, MY_ACCOUNT_ID, RECIPIENT_NUMBER, " 엄마 ", " ");

		assertThat(favorite.getAccountId()).isEqualTo(MY_ACCOUNT_ID);
		assertThat(favorite.getFinancialInstitutionId()).isNull();	// 당행
		assertThat(favorite.getRecipientAccountNumber()).isEqualTo(RECIPIENT_NUMBER);
		assertThat(favorite.getRecipientAccountName()).isEqualTo("김*수");	// 가린 이름 저장
		assertThat(favorite.getFavoriteAlias()).isEqualTo("엄마");
		assertThat(favorite.getMemo()).isNull();
		assertThat(favorite.getFavoriteCreatedAt()).isNotNull();
		assertThat(favorite.getSortOrder()).isEqualTo(4);
	}

	@Test
	void addFirstFavoriteStartsSortOrderAtOne() {
		given(accountFavoriteRepository.findMaxSortOrder(myAccountIds)).willReturn(null);

		AccountFavorite favorite = accountFavoriteService.add(MEMBER_ID, MY_ACCOUNT_ID, RECIPIENT_NUMBER, null, null);

		assertThat(favorite.getSortOrder()).isEqualTo(1);
	}

	@Test
	void addRejectsOthersAccountAsBase() {
		assertThatThrownBy(() -> accountFavoriteService.add(MEMBER_ID, 99L, RECIPIENT_NUMBER, null, null))
				.isInstanceOf(NoSuchElementException.class);
		verify(accountFavoriteRepository, never()).save(any());
	}

	@Test
	void addRejectsUnknownRecipient() {
		given(accountTransferService.findRecipient("1002-999-999999"))
				.willThrow(new IllegalArgumentException("입금계좌를 찾을 수 없습니다. 계좌번호를 확인하세요."));

		assertThatThrownBy(() -> accountFavoriteService.add(MEMBER_ID, MY_ACCOUNT_ID, "1002-999-999999", null, null))
				.isInstanceOf(IllegalArgumentException.class);
		verify(accountFavoriteRepository, never()).save(any());
	}

	// 본인 계좌도 등록할 수 있다 (전계좌조회의 ☆ : 기본 출금계좌 = 그 계좌 자신)
	@Test
	void addAllowsMyOwnAccount() {
		given(accountTransferService.findRecipient("1002-000-000001"))
				.willReturn(new RecipientInfo("1002-000-000001", "테*트"));

		AccountFavorite favorite = accountFavoriteService.add(MEMBER_ID, MY_ACCOUNT_ID, "1002-000-000001", null, null);

		assertThat(favorite.getRecipientAccountNumber()).isEqualTo("1002-000-000001");
		assertThat(favorite.getAccountId()).isEqualTo(MY_ACCOUNT_ID);
	}

	@Test
	void addRejectsDuplicate() {
		given(accountFavoriteRepository.existsByAccountIdInAndRecipientAccountNumber(myAccountIds, RECIPIENT_NUMBER))
				.willReturn(true);

		assertThatThrownBy(() -> accountFavoriteService.add(MEMBER_ID, MY_ACCOUNT_ID, RECIPIENT_NUMBER, null, null))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("이미");
		verify(accountFavoriteRepository, never()).save(any());
	}

	@Test
	void addRejectsWhenFull() {
		given(accountFavoriteRepository.countByAccountIdIn(myAccountIds))
				.willReturn((long) AccountFavoriteService.MAX_FAVORITES);

		assertThatThrownBy(() -> accountFavoriteService.add(MEMBER_ID, MY_ACCOUNT_ID, RECIPIENT_NUMBER, null, null))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("20개");
		verify(accountFavoriteRepository, never()).save(any());
	}

	// ---------- 조회·삭제 ----------

	@Test
	void getMyFavoritesReturnsEmptyWhenNoAccount() {
		given(accountRepository.findByMemberIdOrderByAccountIdAsc(OTHER_MEMBER_ID)).willReturn(List.of());

		assertThat(accountFavoriteService.getMyFavorites(OTHER_MEMBER_ID)).isEmpty();
		assertThat(accountFavoriteService.getRecentDepositAccounts(OTHER_MEMBER_ID)).isEmpty();
	}

	// 이체 화면용 : 즐겨찾기 계좌번호가 내 계좌면 내 계좌, 아니면 상대계좌 (정렬 순서 유지)
	@Test
	void getMyFavoriteGroupsSplitsByMyAccountNumber() {
		AccountFavorite mine = AccountFavorite.builder().accountId(MY_ACCOUNT_ID)
				.recipientAccountNumber("1002-000-000001").sortOrder(2).build();
		AccountFavorite other1 = AccountFavorite.builder().accountId(MY_ACCOUNT_ID)
				.recipientAccountNumber(RECIPIENT_NUMBER).sortOrder(1).build();
		AccountFavorite other2 = AccountFavorite.builder().accountId(MY_ACCOUNT_ID)
				.recipientAccountNumber("1002-333-444444").sortOrder(3).build();
		given(accountFavoriteRepository.findByAccountIdInOrderBySortOrderAscAccountFavoriteIdAsc(myAccountIds))
				.willReturn(List.of(other1, mine, other2));

		FavoriteGroups groups = accountFavoriteService.getMyFavoriteGroups(MEMBER_ID);

		assertThat(groups.myAccounts()).containsExactly(mine);
		assertThat(groups.otherAccounts()).containsExactly(other1, other2);
	}

	@Test
	void getMyFavoriteGroupsEmptyWhenNoAccount() {
		given(accountRepository.findByMemberIdOrderByAccountIdAsc(OTHER_MEMBER_ID)).willReturn(List.of());

		FavoriteGroups groups = accountFavoriteService.getMyFavoriteGroups(OTHER_MEMBER_ID);

		assertThat(groups.myAccounts()).isEmpty();
		assertThat(groups.otherAccounts()).isEmpty();
	}

	@Test
	void deleteMyFavorite() {
		AccountFavorite favorite = AccountFavorite.builder().accountId(MY_ACCOUNT_ID)
				.recipientAccountNumber(RECIPIENT_NUMBER).build();
		given(accountFavoriteRepository.findById(5L)).willReturn(Optional.of(favorite));

		accountFavoriteService.delete(MEMBER_ID, 5L);

		verify(accountFavoriteRepository).delete(favorite);
	}

	// 남의 즐겨찾기는 없는 것과 같은 메시지
	@Test
	void deleteRejectsOthersFavorite() {
		AccountFavorite favorite = AccountFavorite.builder().accountId(99L)
				.recipientAccountNumber(RECIPIENT_NUMBER).build();
		given(accountFavoriteRepository.findById(5L)).willReturn(Optional.of(favorite));

		assertThatThrownBy(() -> accountFavoriteService.delete(MEMBER_ID, 5L))
				.isInstanceOf(NoSuchElementException.class);
		verify(accountFavoriteRepository, never()).delete(any());
	}

	// ---------- 수정·순서 이동 ----------

	private AccountFavorite saved(Long id, Long accountId, String number, Integer sortOrder) {
		AccountFavorite favorite = AccountFavorite.builder()
				.accountId(accountId)
				.recipientAccountNumber(number)
				.favoriteAlias("원래 별칭")
				.memo("원래 메모")
				.sortOrder(sortOrder)
				.build();
		ReflectionTestUtils.setField(favorite, "accountFavoriteId", id);
		return favorite;
	}

	@Test
	void updateChangesAliasAndMemo() {
		AccountFavorite favorite = saved(5L, MY_ACCOUNT_ID, RECIPIENT_NUMBER, 1);
		given(accountFavoriteRepository.findById(5L)).willReturn(Optional.of(favorite));

		accountFavoriteService.update(MEMBER_ID, 5L, " 월세 ", "");

		assertThat(favorite.getFavoriteAlias()).isEqualTo("월세");
		assertThat(favorite.getMemo()).isNull();
		assertThat(favorite.getRecipientAccountNumber()).isEqualTo(RECIPIENT_NUMBER);	// 계좌번호는 그대로
	}

	@Test
	void updateRejectsOthersFavorite() {
		given(accountFavoriteRepository.findById(5L)).willReturn(Optional.of(saved(5L, 99L, RECIPIENT_NUMBER, 1)));

		assertThatThrownBy(() -> accountFavoriteService.update(MEMBER_ID, 5L, "월세", null))
				.isInstanceOf(NoSuchElementException.class);
	}

	// 정렬 순서가 비어 있거나 겹쳐도 목록 순서대로 1부터 다시 매긴 뒤 자리를 바꾼다
	@Test
	void moveUpSwapsWithPrevious() {
		AccountFavorite first = saved(1L, MY_ACCOUNT_ID, "A", null);
		AccountFavorite second = saved(2L, MY_ACCOUNT_ID, "B", 3);
		AccountFavorite third = saved(3L, MY_ACCOUNT_ID, "C", 3);
		given(accountFavoriteRepository.findByAccountIdInOrderBySortOrderAscAccountFavoriteIdAsc(myAccountIds))
				.willReturn(List.of(first, second, third));

		accountFavoriteService.move(MEMBER_ID, 3L, true);

		assertThat(first.getSortOrder()).isEqualTo(1);
		assertThat(third.getSortOrder()).isEqualTo(2);
		assertThat(second.getSortOrder()).isEqualTo(3);
	}

	@Test
	void moveDownSwapsWithNext() {
		AccountFavorite first = saved(1L, MY_ACCOUNT_ID, "A", 1);
		AccountFavorite second = saved(2L, MY_ACCOUNT_ID, "B", 2);
		given(accountFavoriteRepository.findByAccountIdInOrderBySortOrderAscAccountFavoriteIdAsc(myAccountIds))
				.willReturn(List.of(first, second));

		accountFavoriteService.move(MEMBER_ID, 1L, false);

		assertThat(first.getSortOrder()).isEqualTo(2);
		assertThat(second.getSortOrder()).isEqualTo(1);
	}

	@Test
	void moveUpAtTopKeepsOrder() {
		AccountFavorite first = saved(1L, MY_ACCOUNT_ID, "A", 1);
		AccountFavorite second = saved(2L, MY_ACCOUNT_ID, "B", 2);
		given(accountFavoriteRepository.findByAccountIdInOrderBySortOrderAscAccountFavoriteIdAsc(myAccountIds))
				.willReturn(List.of(first, second));

		accountFavoriteService.move(MEMBER_ID, 1L, true);

		assertThat(first.getSortOrder()).isEqualTo(1);
		assertThat(second.getSortOrder()).isEqualTo(2);
	}

	@Test
	void moveRejectsOthersFavorite() {
		given(accountFavoriteRepository.findByAccountIdInOrderBySortOrderAscAccountFavoriteIdAsc(myAccountIds))
				.willReturn(List.of(saved(1L, MY_ACCOUNT_ID, "A", 1)));

		assertThatThrownBy(() -> accountFavoriteService.move(MEMBER_ID, 9L, true))
				.isInstanceOf(NoSuchElementException.class);
	}

	@Test
	void favoriteIdsByNumber() {
		given(accountFavoriteRepository.findByAccountIdInOrderBySortOrderAscAccountFavoriteIdAsc(myAccountIds))
				.willReturn(List.of(saved(1L, MY_ACCOUNT_ID, "A", 1), saved(2L, MY_ACCOUNT_ID, "B", 2)));

		assertThat(accountFavoriteService.getMyFavoriteIdsByNumber(MEMBER_ID))
				.containsEntry("A", 1L)
				.containsEntry("B", 2L);
	}

	@Test
	void addTrimsRecipientNumberThroughFindRecipient() {
		// 입력값 앞뒤 공백은 findRecipient 가 정리한 계좌번호로 저장된다
		given(accountTransferService.findRecipient(" " + RECIPIENT_NUMBER + " "))
				.willReturn(new RecipientInfo(RECIPIENT_NUMBER, "김*수"));

		AccountFavorite favorite = accountFavoriteService.add(MEMBER_ID, MY_ACCOUNT_ID,
				" " + RECIPIENT_NUMBER + " ", null, null);

		assertThat(favorite.getRecipientAccountNumber()).isEqualTo(RECIPIENT_NUMBER);
		// 중복 확인도 정리된 계좌번호로 한다
		verify(accountFavoriteRepository).existsByAccountIdInAndRecipientAccountNumber(myAccountIds, RECIPIENT_NUMBER);
	}
}
