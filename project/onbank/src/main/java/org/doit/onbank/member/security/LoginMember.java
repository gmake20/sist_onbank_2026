package org.doit.onbank.member.security;

import java.util.Collection;
import java.util.List;

import org.doit.onbank.member.entity.Member;
import org.doit.onbank.member.entity.MemberStatus;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import lombok.Getter;

// 로그인한 고객 정보 (세션에 저장됨)
// 다른 팀원은 컨트롤러에서 @AuthenticationPrincipal LoginMember me 로 받아 me.getMemberId()를 사용
// 엔티티(Member)를 그대로 세션에 두지 않고 필요한 값만 복사한다
@Getter
public class LoginMember implements UserDetails, CredentialsContainer {

	private final Long memberId;
	private final String loginId;
	private final String name;
	private String password;	// 로그인 성공 후 eraseCredentials()로 지움

	private final boolean locked;	// 5회 실패 잠금 (status = LOCKED)
	private final boolean enabled;	// 정지(SUSPENDED), 탈퇴(WITHDRAWN)면 false

	public LoginMember(Member member) {
		this.memberId = member.getMemberId();
		this.loginId = member.getLoginId();
		this.name = member.getName();
		this.password = member.getPassword();
		// 잠금은 enabled에 넣지 않는다 → ?disabled 가 아니라 ?locked 로 안내되도록
		this.locked = member.getStatus() == MemberStatus.LOCKED;
		this.enabled = member.getStatus() == MemberStatus.ACTIVE || locked;
	}

	// 고객은 모두 USER 권한 (직원은 employee 테이블에서 따로 로그인)
	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		return List.of(new SimpleGrantedAuthority("ROLE_USER"));
	}

	@Override
	public String getUsername() {
		return loginId;
	}

	// false → Spring Security가 LockedException을 던짐
	@Override
	public boolean isAccountNonLocked() {
		return !locked;
	}

	// false → Spring Security가 DisabledException을 던짐
	@Override
	public boolean isEnabled() {
		return enabled;
	}

	// 비밀번호 해시가 세션에 남지 않도록 로그인 성공 후 지운다
	@Override
	public void eraseCredentials() {
		this.password = null;
	}
}
