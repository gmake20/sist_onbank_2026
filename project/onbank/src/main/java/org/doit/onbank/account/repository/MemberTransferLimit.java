package org.doit.onbank.account.repository;

// members 테이블의 이체한도 컬럼 2개만 읽어 오는 조회 결과 (AccountRepository.findTransferLimit)
// members 는 팀원1 테이블이고 Member 엔티티에 두 필드가 없으므로 팀2에서 컬럼만 직접 조회한다
public interface MemberTransferLimit {

	Long getDailyTransferLimit();	// members.daily_transfer_limit (NULL 가능)

	Long getOnceTransferLimit();	// members.once_transfer_limit (NULL 가능)
}
