-- =====================================================================
-- 관리자(admin) 시드 데이터 (개발/시연용, MySQL)
--   02_admin_ddl.sql 실행 후 실행
--   공용 DB에는 한 번만 실행 (다시 실행하면 UNIQUE 제약 때문에 실패함)
--
-- 관리자 계정 (/admin/login) - 비밀번호 모두 admin1234!, 개발 편의상 비밀번호 변경 강제 해제
--   admin    : SUPER_ADMIN (총괄 관리자)    - 계정·지점 관리, 활동 로그·로그인 이력 조회, 모든 관리자 업무
--   operator : OPERATOR    (운영 관리자)    - 이상거래(FDS) 처리, 모니터링, 회원·계좌·거래 조회
--   service  : SERVICE     (고객지원 관리자) - 공지사항·FAQ 관리
--   ※ 역할별 세부 권한은 docs/ROLE-관리자·직원-권한정리.md (팀원4 제안, 확정 전)
--
-- 직원 계정(/employee/login)은 data-team1-mysql.sql 참고 (E0001~E0003)
-- 비밀번호 해시는 Spring Security 7.1.1 BCrypt로 일치 확인함 (2026-10-15)
-- 배포(prod) 서버에서는 이 비밀번호를 그대로 쓰지 말 것
-- =====================================================================

SET NAMES utf8mb4;

INSERT INTO admin (login_id, password, name, role, status, temp_pw_yn) VALUES
('admin',    '$2a$10$NC8qPrUsf9.qJs5tl01UOezXnYD6vBceSPgNooElPAXWErUnB1Rlm', '최총괄', 'SUPER_ADMIN', 'ACTIVE', 'N'),
('operator', '$2a$10$NC8qPrUsf9.qJs5tl01UOezXnYD6vBceSPgNooElPAXWErUnB1Rlm', '김운영', 'OPERATOR',    'ACTIVE', 'N'),
('service',  '$2a$10$NC8qPrUsf9.qJs5tl01UOezXnYD6vBceSPgNooElPAXWErUnB1Rlm', '박지원', 'SERVICE',     'ACTIVE', 'N');


-- =====================================================================
-- 변경 이력 (공용 DB에 이미 시드가 들어가 있으면 아래 문장만 실행)
-- =====================================================================
-- [2026-10-15] 관리자 admin 비밀번호 Admin1234! → admin1234!
-- UPDATE admin SET password = '$2a$10$NC8qPrUsf9.qJs5tl01UOezXnYD6vBceSPgNooElPAXWErUnB1Rlm'
--  WHERE login_id = 'admin';

-- [2026-10-15] 역할별 관리자 계정 추가 (operator, service), admin 이름 변경
-- INSERT INTO admin (login_id, password, name, role, status, temp_pw_yn) VALUES
-- ('operator', '$2a$10$NC8qPrUsf9.qJs5tl01UOezXnYD6vBceSPgNooElPAXWErUnB1Rlm', '김운영', 'OPERATOR', 'ACTIVE', 'N'),
-- ('service',  '$2a$10$NC8qPrUsf9.qJs5tl01UOezXnYD6vBceSPgNooElPAXWErUnB1Rlm', '박지원', 'SERVICE',  'ACTIVE', 'N');
-- UPDATE admin SET name = '최총괄' WHERE login_id = 'admin';
