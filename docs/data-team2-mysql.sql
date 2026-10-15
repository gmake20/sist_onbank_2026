-- =====================================================================
-- 팀원2 테스트 데이터 (화면 확인용)
-- 내 계좌 화면(/mybank/accounts)에서 입금·출금·거래내역을 눌러 보기 위한 테스트 계좌 3개
--
-- 사용 방법
--   1. 아래 @login_id 에 로그인할 회원 아이디를 넣는다 (members 에 이미 있는 회원)
--   2. 워크벤치에서 전체 실행
--   3. 그 아이디로 로그인 → /mybank/accounts
--
-- 계좌 비밀번호 : 3개 모두 1234 (BCrypt 해시)
-- 계좌번호가 TEST- 로 시작하는 행만 테스트 데이터다
-- 공용 DB에 남으므로 확인이 끝나면 맨 아래 "삭제" 부분을 실행한다
-- =====================================================================

SET @login_id = '여기에_로그인_아이디';
SET @member_id = (SELECT member_id FROM members WHERE login_id = @login_id);

-- 회원이 없으면 member_id 가 NULL 이라 아래 INSERT 가 실패한다 (account.member_id NOT NULL)
SELECT @member_id AS member_id;

INSERT INTO account (member_id, account_number, account_type, balance, account_status,
                     password_hash, password_fail_count, today_transfer_amount)
VALUES
    -- 정상 : 입금 O, 출금 O
    (@member_id, 'TEST-0001', NULL, 100000, 'ACTIVE',
     '$2a$10$jwLsTjLi4P/FONHkB4rKru.ztxV.WBCuemcbQBZEGhQhO09RO6.kO', 0, 0),
    -- 동결 : 입금 O, 출금 X
    (@member_id, 'TEST-0002', NULL, 50000, 'FROZEN',
     '$2a$10$jwLsTjLi4P/FONHkB4rKru.ztxV.WBCuemcbQBZEGhQhO09RO6.kO', 0, 0),
    -- 개설대기 : 입금 X, 출금 X
    (@member_id, 'TEST-0003', NULL, 0, 'PENDING',
     '$2a$10$jwLsTjLi4P/FONHkB4rKru.ztxV.WBCuemcbQBZEGhQhO09RO6.kO', 0, 0);

-- 확인
SELECT account_id, account_number, account_status, balance, password_fail_count
FROM account
WHERE account_number LIKE 'TEST-%';


-- =====================================================================
-- 삭제 (테스트 계좌와 그 거래내역)
-- =====================================================================
-- DELETE FROM transaction_history
-- WHERE account_id IN (SELECT account_id FROM (SELECT account_id FROM account WHERE account_number LIKE 'TEST-%') t);
-- DELETE FROM account WHERE account_number LIKE 'TEST-%';


-- =====================================================================
-- 비밀번호 5회 오류로 출금이 막혔을 때 (관리자 잠금 해제 기능이 생기기 전 임시)
-- =====================================================================
-- UPDATE account SET password_fail_count = 0 WHERE account_number = 'TEST-0001';
