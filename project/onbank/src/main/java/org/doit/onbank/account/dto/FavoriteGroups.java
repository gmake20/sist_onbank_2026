package org.doit.onbank.account.dto;

import java.util.List;

import org.doit.onbank.account.entity.AccountFavorite;

// 즐겨찾기를 계좌번호로 나눈 결과 (이체 화면의 "내 계좌 즐겨찾기" / "상대계좌 즐겨찾기")
// account_favorite 에 구분 컬럼이 없으므로 즐겨찾기 계좌번호가 내 계좌 번호인지로 나눈다
public record FavoriteGroups(List<AccountFavorite> myAccounts, List<AccountFavorite> otherAccounts) {
}
