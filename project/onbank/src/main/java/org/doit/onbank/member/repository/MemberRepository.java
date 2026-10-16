package org.doit.onbank.member.repository;

import java.util.Optional;

import org.doit.onbank.loan.entity.LoanProduct;
import org.doit.onbank.member.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberRepository extends JpaRepository<Member, Long> {

	// 로그인 : 아이디로 회원 조회
	Optional<Member> findByLoginId(String loginId);

	// 회원가입 : 아이디 중복 검사 (탈퇴 회원의 아이디도 재사용 불가)
	boolean existsByLoginId(String loginId);

}
