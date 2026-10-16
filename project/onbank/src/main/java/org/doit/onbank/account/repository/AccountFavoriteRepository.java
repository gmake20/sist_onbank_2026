package org.doit.onbank.account.repository;

import java.util.Collection;
import java.util.List;

import org.doit.onbank.account.entity.AccountFavorite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

// 즐겨찾기는 회원 단위로 본다 : account_id 는 등록할 때 고른 내 계좌, 조회는 내 모든 계좌의 즐겨찾기
public interface AccountFavoriteRepository extends JpaRepository<AccountFavorite, Long> {

	// 내 계좌들의 즐겨찾기 (정렬 순서 → 등록 순서)
	List<AccountFavorite> findByAccountIdInOrderBySortOrderAscAccountFavoriteIdAsc(Collection<Long> accountIds);

	long countByAccountIdIn(Collection<Long> accountIds);

	boolean existsByAccountIdInAndRecipientAccountNumber(Collection<Long> accountIds, String recipientAccountNumber);

	// 다음 정렬 순서를 정하기 위한 현재 최댓값 (없으면 null)
	@Query("select max(f.sortOrder) from AccountFavorite f where f.accountId in :accountIds")
	Integer findMaxSortOrder(@Param("accountIds") Collection<Long> accountIds);
}
