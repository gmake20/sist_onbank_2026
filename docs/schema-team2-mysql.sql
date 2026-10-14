-- =====================================================================
-- 팀원2 담당 테이블 DDL (MySQL 8.0.16 이상 호환)
-- 담당: account, transaction_history, account_transfer, account_favorite, asset_report, financial_institution_code
--
-- [금액 타입]
--   - 원화 금액은 모두 BIGINT(원 단위)로 저장한다. 팀원1(transfer_limit_request), 팀원3(loan 등)과 동일
--     엔티티에서는 Long 으로 매핑
--   - 이자율은 팀원3과 같은 DECIMAL(7,4) ↔ BigDecimal
--
-- [이체한도]
--   - 1일/1회 이체한도는 계좌가 아니라 members.daily_transfer_limit / once_transfer_limit (팀원1 테이블)
--   - 당일 누적 이체 금액은 컬럼으로 두지 않고 account_transfer 에서 계산한다
--     (그 회원의 모든 계좌에서 오늘 출금된 COMPLETED 이체 합계)
--
-- 실행 순서: schema-team1-mysql.sql(members) → 이 파일 → team1 파일 맨 아래 "account 생성 후 실행" 블록
-- 이 파일 안의 생성 순서: financial_institution_code → account → account_transfer → transaction_history
--                        → account_favorite → asset_report
-- =====================================================================

SET NAMES utf8mb4;


-- ---------------------------------------------------------------------
-- 1. financial_institution_code : 금융기관 코드 (타행 이체 등에 사용)
-- ---------------------------------------------------------------------
CREATE TABLE financial_institution_code (
    financial_institution_id            BIGINT          NOT NULL AUTO_INCREMENT,
    financial_institution_name          VARCHAR(100),
    financial_institution_category_code VARCHAR(20),
    CONSTRAINT pk_financial_institution_code PRIMARY KEY (financial_institution_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '금융기관 코드';


-- ---------------------------------------------------------------------
-- 2. account : 계좌 정보 (단일 테이블로 모든 계좌 상품 속성 관리)
--    상태: PENDING 개설대기 / ACTIVE 정상 / FROZEN 동결 / LOCKED 비밀번호 잠금 / CLOSED 해지 / REJECTED 개설거절
--    FROZEN, LOCKED 는 출금/이체만 차단하고 입금은 허용
-- ---------------------------------------------------------------------
CREATE TABLE account (
    account_id              BIGINT          NOT NULL AUTO_INCREMENT,
    member_id               BIGINT          NOT NULL COMMENT '회원 번호 (members FK)',
    account_number          VARCHAR(30)     NOT NULL,
    account_type            VARCHAR(30)     NOT NULL COMMENT 'CHECKING 입출금 / DEPOSIT 예금 / SAVINGS 적금 / LOAN 대출',
    balance                 BIGINT          NOT NULL DEFAULT 0,
    account_status          VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',
    password_hash           VARCHAR(255)    NOT NULL COMMENT 'BCrypt 해시',
    password_fail_count     INT             NOT NULL DEFAULT 0 COMMENT '연속 비밀번호 오류 횟수 - 5회면 LOCKED, 성공 시 0',
    last_transaction_at     DATETIME(6),
    opened_date             DATE            NOT NULL COMMENT '계좌 개설일 (모든 계좌 공통, 예적금은 가입일과 같음)',
    -- 아래 예적금/대출 계약 컬럼은 팀원3 테이블(product_subscription, loan)과 정보가 겹침 - 팀원3과 협의 후 정리 예정
    -- (대응표: schema-team2-dictionary.md 1장 참고)
    expired_date            DATE                     COMMENT '예적금/대출 만기일',
    interest_rate           DECIMAL(7,4),
    signup_period           INT                      COMMENT '가입 기간(개월)',
    monthly_payment         BIGINT                   COMMENT '월 납입 금액',
    loan_amount             BIGINT                   COMMENT '대출 총금액',
    loan_balance            BIGINT                   COMMENT '대출 잔액',
    payment_day             INT                      COMMENT '결제일/이체일',
    repayment_method        VARCHAR(30)              COMMENT '상환 방식',
    current_payment_round   INT                      COMMENT '현재 납입 회차',
    total_payment_rounds    INT                      COMMENT '총 납입 회차',
    CONSTRAINT pk_account PRIMARY KEY (account_id),
    CONSTRAINT uk_account_number UNIQUE (account_number),
    CONSTRAINT fk_account_member FOREIGN KEY (member_id) REFERENCES members (member_id),
    CONSTRAINT ck_account_type CHECK (account_type IN ('CHECKING', 'DEPOSIT', 'SAVINGS', 'LOAN')),
    CONSTRAINT ck_account_status CHECK (account_status IN ('PENDING', 'ACTIVE', 'FROZEN', 'LOCKED', 'CLOSED', 'REJECTED')),
    CONSTRAINT ck_account_balance CHECK (balance >= 0),
    CONSTRAINT ck_account_fail_count CHECK (password_fail_count BETWEEN 0 AND 5)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '계좌 통합 관리';

CREATE INDEX ix_account_member ON account (member_id);


-- ---------------------------------------------------------------------
-- 3. account_transfer : 계좌 이체 이력 (송금 정보 상세)
--    당행 이체: to_account_id 에 입금 계좌 ID / 타행 이체: to_account_id NULL
--    상태: COMPLETED 완료 / SCHEDULED 예약대기 / FAILED 실패 / CANCELLED 취소
-- ---------------------------------------------------------------------
CREATE TABLE account_transfer (
    account_transfer_id         BIGINT          NOT NULL AUTO_INCREMENT,
    account_id                  BIGINT          NOT NULL COMMENT '출금 계좌 ID',
    to_account_id               BIGINT                   COMMENT '입금 계좌 ID (당행 이체만, 타행은 NULL)',
    withdrawal_account_number   VARCHAR(30)     NOT NULL,
    financial_institution_id    BIGINT,
    deposit_account_number      VARCHAR(30)     NOT NULL,
    transfer_amount             BIGINT          NOT NULL,
    sender_memo                 VARCHAR(100),
    receiver_memo               VARCHAR(100),
    transfer_at                 DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '엔티티에서 직접 값을 넣는다',
    transfer_type               VARCHAR(30)     NOT NULL,
    transfer_status             VARCHAR(20)     NOT NULL DEFAULT 'COMPLETED',
    transaction_sqno            VARCHAR(100)    NOT NULL COMMENT '중복 이체 방지 고유번호',
    CONSTRAINT pk_account_transfer PRIMARY KEY (account_transfer_id),
    CONSTRAINT uk_transfer_sqno UNIQUE (transaction_sqno),
    CONSTRAINT fk_transfer_account FOREIGN KEY (account_id) REFERENCES account (account_id),
    CONSTRAINT fk_transfer_to_account FOREIGN KEY (to_account_id) REFERENCES account (account_id),
    CONSTRAINT fk_transfer_bank FOREIGN KEY (financial_institution_id) REFERENCES financial_institution_code (financial_institution_id),
    CONSTRAINT ck_transfer_status CHECK (transfer_status IN ('COMPLETED', 'SCHEDULED', 'FAILED', 'CANCELLED')),
    CONSTRAINT ck_transfer_amount CHECK (transfer_amount > 0),
    CONSTRAINT ck_transfer_not_self CHECK (to_account_id IS NULL OR to_account_id <> account_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '이체 요청 및 상세 정보';

-- 출금 계좌별 이체 조회 + 당일 누적 이체 금액 계산용
CREATE INDEX ix_transfer_account_at ON account_transfer (account_id, transfer_at);


-- ---------------------------------------------------------------------
-- 4. transaction_history : 거래 내역 (모든 입출금 내역, 거래 원장)
--    한 번 기록된 행은 수정·삭제하지 않는다
--    이체 1건 = 출금 계좌 1행 + 입금 계좌 1행 (같은 transaction_sqno, 같은 account_transfer_id)
-- ---------------------------------------------------------------------
CREATE TABLE transaction_history (
    transaction_history_id      BIGINT          NOT NULL AUTO_INCREMENT,
    account_id                  BIGINT          NOT NULL,
    account_transfer_id         BIGINT                   COMMENT '이체로 생긴 거래면 account_transfer FK, 단순 입출금은 NULL',
    transaction_type            VARCHAR(30)     NOT NULL,
    transaction_amount          BIGINT          NOT NULL,
    balance_after_transaction   BIGINT          NOT NULL,
    counterpart_account_number  VARCHAR(30)              COMMENT '상대 계좌번호',
    counterpart_name            VARCHAR(100)             COMMENT '상대 예금주명',
    transaction_content         VARCHAR(255),
    transaction_at              DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '엔티티에서 직접 값을 넣는다',
    transaction_status          VARCHAR(20)     NOT NULL DEFAULT 'SUCCESS',
    payment_round               INT,
    transaction_sqno            VARCHAR(100)    NOT NULL COMMENT '중복 거래 방지 고유번호',
    CONSTRAINT pk_transaction_history PRIMARY KEY (transaction_history_id),
    CONSTRAINT uk_tx_history_sqno UNIQUE (account_id, transaction_sqno),
    CONSTRAINT fk_tx_history_account FOREIGN KEY (account_id) REFERENCES account (account_id),
    CONSTRAINT fk_tx_history_transfer FOREIGN KEY (account_transfer_id) REFERENCES account_transfer (account_transfer_id),
    CONSTRAINT ck_tx_history_amount CHECK (transaction_amount > 0),
    CONSTRAINT ck_tx_history_balance CHECK (balance_after_transaction >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '계좌별 상세 거래내역';

-- 계좌별 거래내역 기간 조회/페이징용
CREATE INDEX ix_tx_history_account_at ON transaction_history (account_id, transaction_at);


-- ---------------------------------------------------------------------
-- 5. account_favorite : 자주 쓰는 계좌 (즐겨찾기)
-- ---------------------------------------------------------------------
CREATE TABLE account_favorite (
    account_favorite_id         BIGINT          NOT NULL AUTO_INCREMENT,
    account_id                  BIGINT          NOT NULL COMMENT '회원 계좌 식별',
    financial_institution_id    BIGINT          NOT NULL,
    recipient_account_number    VARCHAR(30)     NOT NULL,
    recipient_account_name      VARCHAR(100)    NOT NULL,
    memo                        VARCHAR(255),
    favorite_alias              VARCHAR(100),
    favorite_created_at         DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    sort_order                  INT             NOT NULL DEFAULT 0,
    CONSTRAINT pk_account_favorite PRIMARY KEY (account_favorite_id),
    CONSTRAINT fk_favorite_account FOREIGN KEY (account_id) REFERENCES account (account_id),
    CONSTRAINT fk_favorite_bank FOREIGN KEY (financial_institution_id) REFERENCES financial_institution_code (financial_institution_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '자주 쓰는 계좌 즐겨찾기';


-- ---------------------------------------------------------------------
-- 6. asset_report : 자산 리포트 통계
-- ---------------------------------------------------------------------
CREATE TABLE asset_report (
    asset_report_id                 BIGINT          NOT NULL AUTO_INCREMENT,
    account_id                      BIGINT          NOT NULL,
    recent_deposit_amount           BIGINT          DEFAULT 0,
    recent_withdrawal_amount        BIGINT          DEFAULT 0,
    savings_amount                  BIGINT          DEFAULT 0,
    loan_interest_repayment_amount  BIGINT          DEFAULT 0,
    investment_amount               BIGINT          DEFAULT 0,
    utility_bill_amount             BIGINT          DEFAULT 0,
    maintenance_fee_amount          BIGINT          DEFAULT 0,
    standard_year_month             CHAR(6)         NOT NULL COMMENT 'YYYYMM 기준년월',
    CONSTRAINT pk_asset_report PRIMARY KEY (asset_report_id),
    CONSTRAINT fk_asset_report_account FOREIGN KEY (account_id) REFERENCES account (account_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '월별 자산 분석 리포트';


-- =====================================================================
-- 전체 삭제 (공용 DB에서는 팀 공지 후에만 실행! 데이터가 모두 사라짐)
-- FK 때문에 만든 순서의 반대로 삭제. team1의 fk_tlr_account 를 먼저 지워야 account 삭제 가능
-- =====================================================================
-- ALTER TABLE transfer_limit_request DROP FOREIGN KEY fk_tlr_account;
-- DROP TABLE IF EXISTS asset_report, account_favorite, transaction_history, account_transfer,
--                      account, financial_institution_code;


-- =====================================================================
-- 변경 이력 (공용 DB에 이미 테이블이 있으면 아래 ALTER만 실행)
-- 형식: -- [날짜] 작성자 - 내용  다음 줄에 ALTER 문
-- =====================================================================
-- (아직 없음)
