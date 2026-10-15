package org.doit.onbank.admin.security;

import org.doit.onbank.admin.repository.AdminRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

// POST /admin/login 시 호출 : 아이디로 관리자를 찾아 AdminPrincipal로 돌려준다
// SecurityConfig의 관리자 필터 체인에만 연결됨 (고객·직원 로그인에서는 사용하지 않음)
@Service
@RequiredArgsConstructor
public class AdminDetailsService implements UserDetailsService {

	private final AdminRepository adminRepository;

	@Override
	public UserDetails loadUserByUsername(String loginId) throws UsernameNotFoundException {
		return adminRepository.findByLoginId(loginId)
				.map(AdminPrincipal::new)
				.orElseThrow(() -> new UsernameNotFoundException("관리자 없음: " + loginId));
	}
}
