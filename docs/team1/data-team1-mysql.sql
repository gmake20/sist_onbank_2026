-- =====================================================================
-- 팀원1 시드 데이터 (개발/시연용, MySQL)
--   schema-team1-mysql.sql 실행 후 실행
--   공용 DB에는 한 번만 실행 (다시 실행하면 UNIQUE 제약 때문에 실패함)
--
-- 고객 계정 (/login)
--   user1  / User1234!   : 본인확인 완료 → 2주차부터 계좌/이체 개발에 사용
--   user2  / User1234!   : 본인확인 전  → 첫 계좌 개설(본인확인 신청) 흐름 테스트
--   user3  / User1234!   : 본인확인 심사중 → 직원 심사 화면 테스트
--     ※ user3의 개설대기(PENDING) 계좌는 팀원2의 account 시드에서 함께 만들어야 함
--
-- 직원 계정 (/admin/login) - 비밀번호 모두 Staff1234!, 개발 편의상 비밀번호 변경 강제 해제
--   E0001 : SYSTEM_ADMIN (본점)     - 직원/지점/로그 관리
--   E0002 : MANAGER      (강남지점) - 승인 업무 (동결, 한도, 대출, 상품, 카드)
--   E0003 : STAFF        (강남지점) - 조회, 본인확인 심사, 고객센터
--
-- 비밀번호 해시는 Spring Security 7.1.1 BCrypt로 일치 확인함 (2026-10-12)
-- =====================================================================

SET NAMES utf8mb4;
START TRANSACTION;

-- ---------------------------------------------------------------------
-- branch (모의 데이터 - 좌표는 지역 대표 위치, 실제 은행 지점 아님)
--   employee가 참조하므로 가장 먼저 입력
-- ---------------------------------------------------------------------
INSERT INTO branch (branch_type, name, address, latitude, longitude, phone, business_hours, open_24h_yn) VALUES
('BRANCH', '모의은행 본점',       '서울특별시 중구 을지로 일대',             37.5660000, 126.9910000, '0215880000', '평일 09:00~16:00', 'N'),
('BRANCH', '모의은행 시청지점',   '서울특별시 중구 세종대로 일대',           37.5663000, 126.9779000, '0215880001', '평일 09:00~16:00', 'N'),
('BRANCH', '모의은행 강남지점',   '서울특별시 강남구 강남대로 일대',         37.4979000, 127.0276000, '0215880002', '평일 09:00~16:00', 'N'),
('BRANCH', '모의은행 여의도지점', '서울특별시 영등포구 여의대로 일대',       37.5219000, 126.9245000, '0215880003', '평일 09:00~16:00', 'N'),
('BRANCH', '모의은행 판교지점',   '경기도 성남시 분당구 판교역로 일대',      37.3948000, 127.1112000, '0315880004', '평일 09:00~16:00', 'N'),
('BRANCH', '모의은행 서면지점',   '부산광역시 부산진구 중앙대로 일대',       35.1578000, 129.0600000, '0515880005', '평일 09:00~16:00', 'N'),
('ATM',    '모의은행 강남역 ATM',     '서울특별시 강남구 강남대로 일대 (역사 내)', 37.4981000, 127.0279000, NULL, '24시간',      'Y'),
('ATM',    '모의은행 홍대입구역 ATM', '서울특별시 마포구 양화로 일대 (역사 내)',   37.5572000, 126.9245000, NULL, '24시간',      'Y'),
('ATM',    '모의은행 잠실 ATM',       '서울특별시 송파구 올림픽로 일대',           37.5133000, 127.1001000, NULL, '07:00~23:00', 'N'),
('ATM',    '모의은행 종로 ATM',       '서울특별시 종로구 종로 일대',               37.5704000, 126.9920000, NULL, '24시간',      'Y');

-- ---------------------------------------------------------------------
-- branch_service (이용 가능 업무)
-- ---------------------------------------------------------------------
-- 모든 지점: 입금, 출금, 이체, 계좌개설, 카드발급
INSERT INTO branch_service (branch_id, service_code)
SELECT b.branch_id, s.code
FROM branch b
CROSS JOIN (SELECT 'DEPOSIT' AS code UNION ALL
            SELECT 'WITHDRAW' UNION ALL
            SELECT 'TRANSFER' UNION ALL
            SELECT 'ACCOUNT_OPENING' UNION ALL
            SELECT 'CARD_ISSUANCE') s
WHERE b.branch_type = 'BRANCH';

-- 대출상담: 판교지점 제외
INSERT INTO branch_service (branch_id, service_code)
SELECT branch_id, 'LOAN_CONSULTING' FROM branch
WHERE branch_type = 'BRANCH' AND name <> '모의은행 판교지점';

-- ATM: 입금, 출금, 이체, 잔액조회 (잠실 ATM 제외)
INSERT INTO branch_service (branch_id, service_code)
SELECT b.branch_id, s.code
FROM branch b
CROSS JOIN (SELECT 'DEPOSIT' AS code UNION ALL
            SELECT 'WITHDRAW' UNION ALL
            SELECT 'TRANSFER' UNION ALL
            SELECT 'BALANCE_INQUIRY') s
WHERE b.branch_type = 'ATM' AND b.name <> '모의은행 잠실 ATM';

-- 잠실 ATM: 출금, 잔액조회만 (출금 전용 기기)
INSERT INTO branch_service (branch_id, service_code)
SELECT b.branch_id, s.code
FROM branch b
CROSS JOIN (SELECT 'WITHDRAW' AS code UNION ALL
            SELECT 'BALANCE_INQUIRY') s
WHERE b.name = '모의은행 잠실 ATM';

-- ---------------------------------------------------------------------
-- employee (비밀번호: Staff1234!)
-- ---------------------------------------------------------------------
INSERT INTO employee (employee_no, password, name, email, phone, branch_id, position,
                      role, status, temp_pw_yn, hired_at)
SELECT 'E0001', '$2a$10$tjbLDI3KlIrVr/Flis8YAeMfn7vJRFIPin7Gdl6anU3okK/SmnUau',
       '최시스템', 'e0001@mockbank.local', '0215880100', branch_id, '차장',
       'SYSTEM_ADMIN', 'ACTIVE', 'N', DATE '2015-03-02'
FROM branch WHERE name = '모의은행 본점';

INSERT INTO employee (employee_no, password, name, email, phone, branch_id, position,
                      role, status, temp_pw_yn, hired_at)
SELECT 'E0002', '$2a$10$tjbLDI3KlIrVr/Flis8YAeMfn7vJRFIPin7Gdl6anU3okK/SmnUau',
       '정책임', 'e0002@mockbank.local', '0215880201', branch_id, '과장',
       'MANAGER', 'ACTIVE', 'N', DATE '2017-07-03'
FROM branch WHERE name = '모의은행 강남지점';

INSERT INTO employee (employee_no, password, name, email, phone, branch_id, position,
                      role, status, temp_pw_yn, hired_at)
SELECT 'E0003', '$2a$10$tjbLDI3KlIrVr/Flis8YAeMfn7vJRFIPin7Gdl6anU3okK/SmnUau',
       '한창구', 'e0003@mockbank.local', '0215880202', branch_id, '대리',
       'STAFF', 'ACTIVE', 'N', DATE '2021-01-04'
FROM branch WHERE name = '모의은행 강남지점';

-- ---------------------------------------------------------------------
-- members (비밀번호: User1234!)
--   가입일을 과거로 두어 "가입 → 본인확인 신청 → 심사" 순서가 맞도록 함
-- ---------------------------------------------------------------------
INSERT INTO members (login_id, password, name, email, phone, birth_date,
                     status, verification_status, terms_agreed_at, marketing_agree_yn, created_at) VALUES
('user1', '$2a$10$n3/sWtwUkO4SdI0SMtxz7.EFq5ajWIuzj8xS9Cm0YjWUXxlUzzIai',
 '김테스트', 'user1@mockbank.local', '01011111111', DATE '1995-03-15',
 'ACTIVE', 'VERIFIED', NOW(6) - INTERVAL 30 DAY, 'Y', NOW(6) - INTERVAL 30 DAY),
('user2', '$2a$10$n3/sWtwUkO4SdI0SMtxz7.EFq5ajWIuzj8xS9Cm0YjWUXxlUzzIai',
 '이신규', 'user2@mockbank.local', '01022222222', DATE '1998-07-20',
 'ACTIVE', 'NONE',     NOW(6) - INTERVAL 3 DAY,  'N', NOW(6) - INTERVAL 3 DAY),
('user3', '$2a$10$n3/sWtwUkO4SdI0SMtxz7.EFq5ajWIuzj8xS9Cm0YjWUXxlUzzIai',
 '박심사', 'user3@mockbank.local', '01033333333', DATE '2000-11-02',
 'ACTIVE', 'PENDING',  NOW(6) - INTERVAL 1 DAY,  'N', NOW(6) - INTERVAL 1 DAY);

-- ---------------------------------------------------------------------
-- identity_verification
-- ---------------------------------------------------------------------
-- user1: 승인 완료 이력 (STAFF E0003이 심사)
INSERT INTO identity_verification (member_id, id_card_type, id_issue_date,
                                   auth_bank_name, auth_account_masked, status,
                                   requested_at, reviewed_employee_id, reviewed_at)
SELECT m.member_id, 'RESIDENT_CARD', DATE '2018-05-10', '가상은행', '110-***-**1234', 'APPROVED',
       NOW(6) - INTERVAL 29 DAY, e.employee_id, NOW(6) - INTERVAL 28 DAY
FROM members m, employee e
WHERE m.login_id = 'user1' AND e.employee_no = 'E0003';

-- user3: 심사 대기
INSERT INTO identity_verification (member_id, id_card_type, id_issue_date,
                                   auth_bank_name, auth_account_masked, status, requested_at)
SELECT member_id, 'DRIVER_LICENSE', DATE '2021-02-01', '샘플은행', '333-***-**5678', 'PENDING',
       NOW(6) - INTERVAL 1 HOUR
FROM members WHERE login_id = 'user3';

COMMIT;
