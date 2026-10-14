package org.doit.onbank.member.security;

import org.doit.onbank.member.repository.MemberRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

// POST /login 시 Spring Security가 호출 : 로그인 폼의 아이디로 회원을 찾아 LoginMember로 돌려준다
// 비밀번호 비교, 잠금/정지 확인은 Spring Security가 LoginMember를 보고 처리한다
@Service
@RequiredArgsConstructor
public class MemberDetailsService implements UserDetailsService {

	private final MemberRepository memberRepository;

	@Override
	public UserDetails loadUserByUsername(String loginId) throws UsernameNotFoundException {
		return memberRepository.findByLoginId(loginId)
				.map(LoginMember::new)
				// 화면에는 "아이디 또는 비밀번호가 올바르지 않습니다"로만 안내됨 (아이디 존재 여부를 알려주지 않음)
				.orElseThrow(() -> new UsernameNotFoundException("회원 없음: " + loginId));
	}
}
