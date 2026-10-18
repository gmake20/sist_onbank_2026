package org.doit.onbank.account.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.stream.Collectors;

import org.doit.onbank.account.dto.FavoriteGroups;
import org.doit.onbank.account.dto.RecipientInfo;
import org.doit.onbank.account.entity.Account;
import org.doit.onbank.account.entity.AccountFavorite;
import org.doit.onbank.account.repository.AccountFavoriteRepository;
import org.doit.onbank.account.repository.AccountRepository;
import org.doit.onbank.account.repository.AccountTransferRepository;
import org.doit.onbank.account.repository.RecentDepositAccount;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

// 계좌 즐겨찾기 : 자주 쓰는 내 계좌 + 자주 보내는 다른 사람 계좌를 한 테이블에서 관리 (기획서 4.5 + 5.6)
// 회원 단위 : 등록할 때 account_id 에 내 계좌(기본 출금계좌)를 넣고, 조회는 내 모든 계좌의 즐겨찾기를 모은다
// 지금은 당행만 : financial_institution_id = NULL (ONBANK)
@Service
@RequiredArgsConstructor
public class AccountFavoriteService {

	public static final int MAX_FAVORITES = 20;
	public static final int RECENT_LIMIT = 10;

	private final AccountRepository accountRepository;
	private final AccountFavoriteRepository accountFavoriteRepository;
	private final AccountTransferRepository accountTransferRepository;
	private final AccountTransferService accountTransferService;

	// 내 즐겨찾기 목록 (정렬 순서대로)
	@Transactional(readOnly = true)
	public List<AccountFavorite> getMyFavorites(Long memberId) {
		return findMyFavorites(myAccountIds(memberId));
	}

	// 이체 화면용 : 즐겨찾기를 내 계좌(출금계좌로 고름) / 상대계좌(입금계좌로 고름)로 나눈다
	// 구분 컬럼이 없으므로 즐겨찾기 계좌번호가 지금 내 계좌 번호 중 하나면 내 계좌로 본다 (각 목록은 정렬 순서 유지)
	@Transactional(readOnly = true)
	public FavoriteGroups getMyFavoriteGroups(Long memberId) {
		List<Account> myAccounts = accountRepository.findByMemberIdOrderByAccountIdAsc(memberId);
		Set<String> myAccountNumbers = myAccounts.stream()
				.map(Account::getAccountNumber)
				.collect(Collectors.toSet());
		Map<Boolean, List<AccountFavorite>> groups = findMyFavorites(myAccounts.stream().map(Account::getAccountId).toList())
				.stream()
				.collect(Collectors.partitioningBy(f -> myAccountNumbers.contains(f.getRecipientAccountNumber())));
		return new FavoriteGroups(groups.get(true), groups.get(false));
	}

	// 계좌번호 → 즐겨찾기 ID (전계좌조회의 ★ 표시, 이체 완료 화면의 "등록됨" 표시)
	@Transactional(readOnly = true)
	public Map<String, Long> getMyFavoriteIdsByNumber(Long memberId) {
		return getMyFavorites(memberId).stream()
				.collect(Collectors.toMap(AccountFavorite::getRecipientAccountNumber,
						AccountFavorite::getAccountFavoriteId, (first, second) -> first));
	}

	// 즐겨찾기 등록 : 본인 계좌, 다른 사람 계좌 모두 가능
	// 예금주명은 다른 사람 이름이 그대로 남지 않도록 가린 이름(예: 홍*동)으로 저장
	@Transactional
	public AccountFavorite add(Long memberId, Long accountId, String recipientAccountNumber, String alias,
			String memo) {
		List<Long> myAccountIds = myAccountIds(memberId);
		if (accountId == null || !myAccountIds.contains(accountId)) {
			throw new NoSuchElementException("계좌가 없습니다.");
		}

		// 입금받을 수 있는 계좌인지 확인 (이체 확인 단계와 같은 검사) → 없는 계좌, 개설대기·해지 계좌는 등록 불가
		RecipientInfo recipient = accountTransferService.findRecipient(recipientAccountNumber);
		String number = recipient.accountNumber();

		if (accountFavoriteRepository.existsByAccountIdInAndRecipientAccountNumber(myAccountIds, number)) {
			throw new IllegalArgumentException("이미 즐겨찾기에 등록된 계좌입니다.");
		}
		if (accountFavoriteRepository.countByAccountIdIn(myAccountIds) >= MAX_FAVORITES) {
			throw new IllegalStateException("즐겨찾기는 " + MAX_FAVORITES + "개까지 등록할 수 있습니다.");
		}

		Integer maxSortOrder = accountFavoriteRepository.findMaxSortOrder(myAccountIds);
		return accountFavoriteRepository.save(AccountFavorite.builder()
				.accountId(accountId)
				.financialInstitutionId(null)	// NULL = 당행(ONBANK)
				.recipientAccountNumber(number)
				.recipientAccountName(recipient.maskedName())
				.favoriteAlias(trimToNull(alias))
				.memo(trimToNull(memo))
				.favoriteCreatedAt(LocalDateTime.now())
				.sortOrder(maxSortOrder == null ? 1 : maxSortOrder + 1)
				.build());
	}

	// 내 즐겨찾기 1개 (남의 즐겨찾기면 없는 것과 같은 메시지)
	@Transactional(readOnly = true)
	public AccountFavorite getMyFavorite(Long memberId, Long favoriteId) {
		return findMine(myAccountIds(memberId), favoriteId);
	}

	// 별칭·메모 수정
	@Transactional
	public AccountFavorite update(Long memberId, Long favoriteId, String alias, String memo) {
		AccountFavorite favorite = findMine(myAccountIds(memberId), favoriteId);
		favorite.update(trimToNull(alias), trimToNull(memo));
		return favorite;
	}

	// 순서 이동 : 바로 위(또는 아래) 즐겨찾기와 자리를 바꾼다. 맨 위에서 위로, 맨 아래에서 아래로는 그대로
	// 정렬 순서가 비어 있거나 겹친 경우에도 맞도록 지금 목록 순서대로 1부터 다시 매긴 뒤 바꾼다
	@Transactional
	public void move(Long memberId, Long favoriteId, boolean up) {
		List<Long> myAccountIds = myAccountIds(memberId);
		List<AccountFavorite> favorites = new ArrayList<>(findMyFavorites(myAccountIds));
		int index = indexOf(favorites, favoriteId);
		if (index < 0) {
			throw new NoSuchElementException("즐겨찾기가 없습니다.");
		}

		int target = up ? index - 1 : index + 1;
		if (target >= 0 && target < favorites.size()) {
			favorites.add(target, favorites.remove(index));
		}
		for (int i = 0; i < favorites.size(); i++) {
			favorites.get(i).changeSortOrder(i + 1);
		}
	}

	// 즐겨찾기 삭제
	@Transactional
	public void delete(Long memberId, Long favoriteId) {
		accountFavoriteRepository.delete(findMine(myAccountIds(memberId), favoriteId));
	}

	// 최근입금계좌 : 내 계좌들에서 이체한 계좌번호를 최근 순서로 (최대 10개)
	@Transactional(readOnly = true)
	public List<RecentDepositAccount> getRecentDepositAccounts(Long memberId) {
		List<Long> myAccountIds = myAccountIds(memberId);
		if (myAccountIds.isEmpty()) {
			return List.of();
		}
		return accountTransferRepository.findRecentDepositAccounts(myAccountIds, PageRequest.of(0, RECENT_LIMIT));
	}

	private List<AccountFavorite> findMyFavorites(List<Long> myAccountIds) {
		if (myAccountIds.isEmpty()) {
			return List.of();
		}
		return accountFavoriteRepository.findByAccountIdInOrderBySortOrderAscAccountFavoriteIdAsc(myAccountIds);
	}

	private AccountFavorite findMine(List<Long> myAccountIds, Long favoriteId) {
		return accountFavoriteRepository.findById(favoriteId)
				.filter(f -> myAccountIds.contains(f.getAccountId()))
				.orElseThrow(() -> new NoSuchElementException("즐겨찾기가 없습니다."));
	}

	private int indexOf(List<AccountFavorite> favorites, Long favoriteId) {
		for (int i = 0; i < favorites.size(); i++) {
			if (favorites.get(i).getAccountFavoriteId().equals(favoriteId)) {
				return i;
			}
		}
		return -1;
	}

	private List<Long> myAccountIds(Long memberId) {
		return accountRepository.findByMemberIdOrderByAccountIdAsc(memberId).stream()
				.map(Account::getAccountId)
				.toList();
	}

	private String trimToNull(String value) {
		return (value == null || value.isBlank()) ? null : value.trim();
	}
}
