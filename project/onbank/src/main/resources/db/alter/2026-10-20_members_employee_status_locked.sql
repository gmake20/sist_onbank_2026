-- =====================================================================
-- members / employee : status 에 LOCKED(로그인 5회 실패 잠금) 추가
-- 작성 : 2026-10-20 (팀원1)
--
-- 이미 만들어진 공용 DB에 한 번만 실행한다 (새로 만드는 DB는 01_auth_ddl.sql 에 반영되어 있음)
-- 잠금 표시를 login_locked_at → status = LOCKED 로 바꾼다 (admin 테이블과 같은 방식)
-- login_locked_at 은 잠긴 시각 기록용으로 남긴다
-- =====================================================================

SET NAMES utf8mb4;

-- 1. CHECK 제약 변경
ALTER TABLE members DROP CHECK ck_members_status;
ALTER TABLE members ADD CONSTRAINT ck_members_status
    CHECK (status IN ('ACTIVE', 'LOCKED', 'SUSPENDED', 'WITHDRAWN'));

ALTER TABLE employee DROP CHECK ck_employee_status;
ALTER TABLE employee ADD CONSTRAINT ck_employee_status
    CHECK (status IN ('ACTIVE', 'LOCKED', 'ON_LEAVE', 'RETIRED'));

-- 2. 컬럼 설명 변경
ALTER TABLE members MODIFY login_locked_at DATETIME(6)
    COMMENT '로그인 5회 실패로 잠긴 시각 (기록용). 잠김 여부는 status = LOCKED';
ALTER TABLE employee MODIFY login_locked_at DATETIME(6)
    COMMENT '로그인 5회 실패로 잠긴 시각 (기록용). 잠김 여부는 status = LOCKED';

-- 3. 이미 잠긴 계정 옮기기 (정상 상태에서 잠긴 계정만)
UPDATE members  SET status = 'LOCKED' WHERE status = 'ACTIVE' AND login_locked_at IS NOT NULL;
UPDATE employee SET status = 'LOCKED' WHERE status = 'ACTIVE' AND login_locked_at IS NOT NULL;

-- 확인
-- SELECT member_id, login_id, status, login_fail_count, login_locked_at FROM members WHERE status = 'LOCKED';
-- SELECT employee_id, employee_no, status, login_fail_count, login_locked_at FROM employee WHERE status = 'LOCKED';
