package org.doit.onbank.employee.security;

import java.util.Collection;
import java.util.List;

import org.doit.onbank.common.security.ActorType;
import org.doit.onbank.common.security.BackOfficeUser;
import org.doit.onbank.employee.entity.Employee;
import org.doit.onbank.employee.entity.EmployeeRole;
import org.doit.onbank.employee.entity.EmployeeStatus;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import lombok.Getter;

// 로그인한 직원 정보 (세션에 저장됨)
// /employee/** 컨트롤러에서 @AuthenticationPrincipal EmployeePrincipal me 로 받아 me.getEmployeeId() 사용
@Getter
public class EmployeePrincipal implements UserDetails, CredentialsContainer, BackOfficeUser {

	private final Long employeeId;
	private final String employeeNo;
	private final String name;
	private final EmployeeRole role;
	private final Long branchId;
	private final boolean tempPassword;	// true면 비밀번호 변경 화면으로 보냄 (LoginSuccessHandler, TempPasswordFilter)
	private String password;			// 로그인 성공 후 eraseCredentials()로 지움

	private final boolean locked;		// 5회 실패 잠금 (login_locked_at)
	private final boolean enabled;		// 휴직(ON_LEAVE), 퇴사(RETIRED)면 false

	public EmployeePrincipal(Employee employee) {
		this.employeeId = employee.getEmployeeId();
		this.employeeNo = employee.getEmployeeNo();
		this.name = employee.getName();
		this.role = employee.getRole();
		this.branchId = employee.getBranchId();
		this.tempPassword = employee.isTempPassword();
		this.password = employee.getPassword();
		this.locked = employee.getLoginLockedAt() != null;
		this.enabled = employee.getStatus() == EmployeeStatus.ACTIVE;
	}

	// ROLE_STAFF 또는 ROLE_MANAGER (MANAGER는 RoleHierarchy로 STAFF 권한도 가짐)
	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
	}

	@Override
	public String getUsername() {
		return employeeNo;
	}

	@Override
	public boolean isAccountNonLocked() {
		return !locked;
	}

	@Override
	public boolean isEnabled() {
		return enabled;
	}

	@Override
	public void eraseCredentials() {
		this.password = null;
	}

	@Override
	public ActorType getActorType() {
		return ActorType.EMPLOYEE;
	}

	@Override
	public Long getActorId() {
		return employeeId;
	}
}
