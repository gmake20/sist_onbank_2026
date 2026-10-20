package org.doit.onbank.loan.repository;

import java.util.List;
import java.util.Optional;

import org.doit.onbank.loan.entity.LoanAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface LoanAccountRepository
extends JpaRepository<LoanAccount, Long> {

	// 대출 계좌번호로 조회
	Optional<LoanAccount> findByLoanAccountNumber(
			String loanAccountNumber);

	// 대출 신청 ID로 대출 계좌 조회
	Optional<LoanAccount> findByLoanApplication_Id(
			Long applicationId);

	// 대출 신청에 연결된 대출 계좌 존재 여부 확인
	boolean existsByLoanApplication_Id(
			Long applicationId);

	List<LoanAccount>
	findByLoanApplication_Member_MemberIdOrderByExecutedAtDesc(
			Long memberId);


	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
			SELECT l
			FROM LoanAccount l
			WHERE l.loanAccountId = :id
			""")
	Optional<LoanAccount> findByIdForUpdate(
			@Param("id") Long id);
	
	
}