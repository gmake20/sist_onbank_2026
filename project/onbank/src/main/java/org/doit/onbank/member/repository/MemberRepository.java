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

	// 회원가입 : 휴대폰 번호 중복 검사 (탈퇴 회원은 NULL로 비워지므로 재가입 가능)
	boolean existsByPhone(String phone);

	// 회원가입 : 이메일 중복 검사 (탈퇴 회원은 NULL로 비워지므로 재가입 가능)
	boolean existsByEmail(String email);

}
