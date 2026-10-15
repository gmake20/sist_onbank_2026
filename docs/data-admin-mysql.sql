-- =====================================================================
-- 관리자(admin) 시드 데이터 (개발/시연용, MySQL)
--   02_admin_ddl.sql 실행 후 실행
--   공용 DB에는 한 번만 실행 (다시 실행하면 UNIQUE 제약 때문에 실패함)
--
-- 관리자 계정 (/admin/login) - 개발 편의상 비밀번호 변경 강제 해제
--   admin / Admin1234! : SUPER_ADMIN
--
-- 직원 계정(/employee/login)은 data-team1-mysql.sql 참고 (E0001~E0003)
-- 비밀번호 해시는 Spring Security 7.1.1 BCrypt로 일치 확인함 (2026-10-15)
-- 배포(prod) 서버에서는 이 비밀번호를 그대로 쓰지 말 것
-- =====================================================================

SET NAMES utf8mb4;

INSERT INTO admin (login_id, password, name, role, status, temp_pw_yn)
VALUES ('admin', '$2a$10$hFmT1XshdEZJCET1nYxrh.FQq2dyoY7IPQjR9aNhpT45kUiXRbeb6',
        '시스템관리자', 'SUPER_ADMIN', 'ACTIVE', 'N');
