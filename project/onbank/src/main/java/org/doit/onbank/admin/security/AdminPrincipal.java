package org.doit.onbank.admin.security;

import java.util.Collection;
import java.util.List;

import org.doit.onbank.admin.entity.Admin;
import org.doit.onbank.admin.entity.AdminRole;
import org.doit.onbank.admin.entity.AdminStatus;
import org.doit.onbank.common.security.ActorType;
import org.doit.onbank.common.security.BackOfficeUser;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import lombok.Getter;

// 로그인한 관리자 정보 (세션에 저장됨)
// /admin/** 컨트롤러에서 @AuthenticationPrincipal AdminPrincipal me 로 받아 me.getAdminId() 사용
@Getter
public class AdminPrincipal implements UserDetails, CredentialsContainer, BackOfficeUser {

	private final Long adminId;
	private final String loginId;
	private final String name;
	private final AdminRole role;
	private final boolean tempPassword;	// 다음 단계 : true면 비밀번호 변경 화면으로 보냄
	private final AdminStatus status;
	private String password;			// 로그인 성공 후 eraseCredentials()로 지움

	public AdminPrincipal(Admin admin) {
		this.adminId = admin.getAdminId();
		this.loginId = admin.getLoginId();
		this.name = admin.getName();
		this.role = admin.getRole();
		this.tempPassword = admin.isTempPassword();
		this.status = admin.getStatus();
		this.password = admin.getPassword();
	}

	// ROLE_SUPER_ADMIN / ROLE_OPERATOR / ROLE_SERVICE
	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
	}

	@Override
	public String getUsername() {
		return loginId;
	}

	// status = LOCKED → LockedException
	@Override
	public boolean isAccountNonLocked() {
		return status != AdminStatus.LOCKED;
	}

	// status = INACTIVE → DisabledException
	@Override
	public boolean isEnabled() {
		return status != AdminStatus.INACTIVE;
	}

	@Override
	public void eraseCredentials() {
		this.password = null;
	}

	@Override
	public ActorType getActorType() {
		return ActorType.ADMIN;
	}

	@Override
	public Long getActorId() {
		return adminId;
	}
}
