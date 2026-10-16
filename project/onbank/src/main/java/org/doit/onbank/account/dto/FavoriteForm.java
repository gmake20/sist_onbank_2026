package org.doit.onbank.account.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

// 즐겨찾기 등록 입력값 (관리 화면, 이체 결과 화면 공통)
@Getter
@Setter
public class FavoriteForm {

	@NotNull(message = "기본 출금계좌를 선택하세요.")
	private Long accountId;					// 등록 계좌 (즐겨찾기로 이체할 때 출금계좌로 미리 선택)

	@NotBlank(message = "입금계좌번호를 입력하세요.")
	@Size(max = 30, message = "입금계좌번호가 너무 깁니다.")
	private String recipientAccountNumber;

	@Size(max = 20, message = "별칭은 20자까지 입력할 수 있습니다.")
	private String favoriteAlias;			// 비우면 예금주명(가림)으로 표시

	@Size(max = 50, message = "메모는 50자까지 입력할 수 있습니다.")
	private String memo;
}
