package org.doit.onbank.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collection;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

// SecurityConfig의 권한 계층 확인 (DB·Spring 컨텍스트 없이 실행)
class RoleHierarchyTests {

	private final RoleHierarchy roleHierarchy = SecurityConfig.roleHierarchy();

	// 이 권한으로 로그인하면 실제로 갖게 되는 권한 목록
	private List<String> reachable(String role) {
		Collection<? extends GrantedAuthority> authorities =
				roleHierarchy.getReachableGrantedAuthorities(List.of(new SimpleGrantedAuthority(role)));
		return AuthorityUtils.authorityListToSet(authorities).stream().sorted().toList();
	}

	@Test
	void managerIncludesStaff() {
		assertThat(reachable("ROLE_MANAGER")).containsExactly("ROLE_MANAGER", "ROLE_STAFF");
		assertThat(reachable("ROLE_STAFF")).containsExactly("ROLE_STAFF");
	}

	@Test
	void superAdminIncludesOperatorAndService() {
		assertThat(reachable("ROLE_SUPER_ADMIN"))
				.containsExactly("ROLE_OPERATOR", "ROLE_SERVICE", "ROLE_SUPER_ADMIN");
	}

	// 운영 관리자와 고객지원 관리자는 서로의 권한을 갖지 않는다
	@Test
	void operatorAndServiceAreSeparate() {
		assertThat(reachable("ROLE_OPERATOR")).containsExactly("ROLE_OPERATOR");
		assertThat(reachable("ROLE_SERVICE")).containsExactly("ROLE_SERVICE");
	}

	// 직원과 관리자 계층은 서로 섞이지 않는다
	@Test
	void employeeAndAdminRolesDoNotMix() {
		assertThat(reachable("ROLE_SUPER_ADMIN")).doesNotContain("ROLE_MANAGER", "ROLE_STAFF");
		assertThat(reachable("ROLE_MANAGER")).doesNotContain("ROLE_SUPER_ADMIN", "ROLE_OPERATOR", "ROLE_SERVICE");
	}
}
