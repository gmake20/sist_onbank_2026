package org.doit.onbank.member.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import org.doit.onbank.member.entity.Member;
import org.doit.onbank.member.entity.MemberStatus;
import org.doit.onbank.member.entity.VerificationStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

// @DataJpaTest : JPA 관련 Bean만 띄우고, 테스트가 끝나면 롤백한다 (공용 DB에 데이터가 남지 않음)
// replace = NONE : 내장 DB로 바꾸지 않고 application-local.properties의 MySQL을 그대로 사용
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class MemberRepositoryTests {

	@Autowired
	private MemberRepository memberRepository;

	@Autowired
	private TestEntityManager em;

	// 공용 DB의 기존 회원과 겹치지 않는 값
	private final String suffix = String.valueOf(System.currentTimeMillis() % 100_000_000);

	private Member newMember() {
		return Member.builder()
				.loginId("test" + suffix)
				.password("{bcrypt}dummy")	// 저장 테스트용. 실제로는 PasswordEncoder로 암호화
				.name("테스트")
				.email("test" + suffix + "@onbank.test")
				.phone("010" + suffix)
				.birthDate(LocalDate.of(2000, 1, 1))
				.termsAgreedAt(LocalDateTime.now())
				.marketingAgree(true)
				.build();
	}

	@Test
	void saveAndFindByLoginId() {
		Member saved = memberRepository.save(newMember());
		em.flush();	// INSERT 실행
		em.clear();	// 1차 캐시를 비워서 아래 조회가 DB에서 다시 읽도록 함

		Optional<Member> result = memberRepository.findByLoginId("test" + suffix);

		assertThat(result).isPresent();
		Member member = result.get();
		System.out.println("👍👍 findByLoginId() : " + member);

		assertThat(member.getMemberId()).isEqualTo(saved.getMemberId());
		// 기본값
		assertThat(member.getStatus()).isEqualTo(MemberStatus.ACTIVE);
		assertThat(member.getVerificationStatus()).isEqualTo(VerificationStatus.NONE);
		assertThat(member.getLoginFailCount()).isZero();
		// Y/N 변환
		assertThat(member.isMarketingAgree()).isTrue();
		// JPA Auditing
		assertThat(member.getCreatedAt()).isNotNull();
	}

	@Test
	void findByLoginIdNotFound() {
		assertThat(memberRepository.findByLoginId("no-such-id-" + suffix)).isEmpty();
	}

	@Test
	void existsByLoginId() {
		memberRepository.save(newMember());
		em.flush();

		assertThat(memberRepository.existsByLoginId("test" + suffix)).isTrue();
		assertThat(memberRepository.existsByLoginId("no-such-id-" + suffix)).isFalse();
	}
}
