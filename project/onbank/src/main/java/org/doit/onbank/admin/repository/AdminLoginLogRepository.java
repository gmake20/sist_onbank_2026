package org.doit.onbank.admin.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.doit.onbank.admin.entity.AdminLoginLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AdminLoginLogRepository extends JpaRepository<AdminLoginLog, Long> {

	// 로그인 이력 검색 조건 (S-14 로그인 이력 탭). null 조건은 "전체"
	// 아이디는 입력한 그대로 저장되므로(없는 아이디 포함) login_id_input 으로 찾는다
	String SEARCH_WHERE = """
			WHERE (:loginId IS NULL OR l.loginIdInput = :loginId)
			AND (:success IS NULL OR l.success = :success)
			AND (:failReason IS NULL OR l.failReason = :failReason)
			AND (:ipAddress IS NULL OR l.ipAddress = :ipAddress)
			AND l.loginAt >= :from AND l.loginAt < :to
			""";

	// 페이지용 count 쿼리를 자동으로 만들게 두지 않고 같은 조건으로 직접 적는다
	@Query(value = "SELECT l FROM AdminLoginLog l " + SEARCH_WHERE + " ORDER BY l.loginLogId DESC",
			countQuery = "SELECT count(l) FROM AdminLoginLog l " + SEARCH_WHERE)
	Page<AdminLoginLog> search(@Param("loginId") String loginId,
			@Param("success") Boolean success,
			@Param("failReason") String failReason,
			@Param("ipAddress") String ipAddress,
			@Param("from") LocalDateTime from,
			@Param("to") LocalDateTime to,
			Pageable pageable);

	// 입력한 아이디로 최근 이력부터 조회 (테스트용)
	List<AdminLoginLog> findByLoginIdInputOrderByLoginLogIdDesc(String loginIdInput);
}
