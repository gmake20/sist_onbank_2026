package org.doit.onbank.admin.repository;

import java.util.Optional;

import org.doit.onbank.admin.entity.Admin;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminRepository extends JpaRepository<Admin, Long> {

	// 관리자 로그인 : 아이디로 조회
	Optional<Admin> findByLoginId(String loginId);
}
