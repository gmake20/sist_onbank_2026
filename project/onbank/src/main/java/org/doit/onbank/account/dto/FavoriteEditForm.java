package org.doit.onbank.account.dto;

import org.doit.onbank.account.entity.AccountFavorite;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

// 즐겨찾기 수정 입력값 : 별칭·메모만 바꾼다
@Getter
@Setter
public class FavoriteEditForm {

	@Size(max = 20, message = "별칭은 20자까지 입력할 수 있습니다.")
	private String favoriteAlias;			// 비우면 예금주명(가림)으로 표시

	@Size(max = 50, message = "메모는 50자까지 입력할 수 있습니다.")
	private String memo;

	public static FavoriteEditForm from(AccountFavorite favorite) {
		FavoriteEditForm form = new FavoriteEditForm();
		form.setFavoriteAlias(favorite.getFavoriteAlias());
		form.setMemo(favorite.getMemo());
		return form;
	}
}
