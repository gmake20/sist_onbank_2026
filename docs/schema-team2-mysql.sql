-- =====================================================================
-- 팀원2 담당 테이블 DDL (MySQL 8.0 이상)
-- 담당: account, transaction_history, account_transfer, account_favorite, asset_report, financial_institution_code
--
-- 기준: 팀원2 데이터 사전 + 아래 변경
--   - 금액은 모두 BIGINT(원 단위) ↔ 엔티티 Long
--   - 이자율은 DECIMAL(7,4) ↔ 엔티티 BigDecimal
--   - account.password_fail_count 추가 (연속 비밀번호 오류 횟수)
--
-- 엔티티: project/onbank/src/main/java/org/doit/onbank/account/entity
-- JPA는 ddl-auto=validate 이므로 컬럼을 바꾸면 엔티티도 함께 바꾼다
-- =====================================================================

SET NAMES utf8mb4;


-- ---------------------------------------------------------------------
-- 1. financial_institution_code : 금융기관 코드 (타행 이체 등에 사용)
-- ---------------------------------------------------------------------
CREATE TABLE financial_institution_code (
    financial_institution_id            BIGINT          NOT NULL AUTO_INCREMENT,
    financial_institution_name          VARCHAR(100)             COMMENT '기관명 (예: 신한은행, 국민은행)',
    financial_institution_category_code VARCHAR(20)              COMMENT '기관 분류 코드 (은행, 증권 등)',
    CONSTRAINT pk_financial_institution_code PRIMARY KEY (financial_institution_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '금융기관 코드';


-- ---------------------------------------------------------------------
-- 2. account : 계좌 (입출금, 예적금, 대출 등의 계좌 정보를 통합 관리)
-- ---------------------------------------------------------------------
CREATE TABLE account (
    account_id              BIGINT          NOT NULL AUTO_INCREMENT,
    member_id               BIGINT          NOT NULL COMMENT '계좌를 소유한 회원 번호 (members 참조)',
    account_number          VARCHAR(30)              COMMENT '실제 계좌번호',
    account_type            VARCHAR(30)              COMMENT '계좌 유형 (입출금, 예적금, 대출 등)',
    balance                 BIGINT                   COMMENT '현재 잔액',
    account_status          VARCHAR(20)              COMMENT '계좌 상태 (정상, 정지, 해지 등)',
    password_hash           VARCHAR(255)             COMMENT '계좌 비밀번호 (해시)',
    password_fail_count     INT             NOT NULL DEFAULT 0 COMMENT '연속 비밀번호 오류 횟수 (성공 시 0)',
    last_transaction_at     DATETIME(6)              COMMENT '최종 거래 일시',
    signup_date             DATE                     COMMENT '가입일 (예적금/대출용)',
    expired_date            DATE                     COMMENT '만기일 (예적금/대출용)',
    interest_rate           DECIMAL(7,4)             COMMENT '적용 이자율',
    signup_period           INT                      COMMENT '가입 기간(개월)',
    monthly_payment         BIGINT                   COMMENT '월 납입 금액',
    loan_amount             BIGINT                   COMMENT '대출 원금',
    loan_balance            BIGINT                   COMMENT '대출 잔액',
    payment_day             INT                      COMMENT '매월 납입/결제일',
    repayment_method        VARCHAR(30)              COMMENT '대출 상환 방식',
    current_payment_round   INT                      COMMENT '현재 납입 회차',
    total_payment_rounds    INT                      COMMENT '총 납입 회차',
    today_transfer_amount   BIGINT                   COMMENT '당일 누적 이체 금액 (한도 체크용)',
    CONSTRAINT pk_account PRIMARY KEY (account_id),
    CONSTRAINT uk_account_number UNIQUE (account_number)
)ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '계좌';


-- ---------------------------------------------------------------------
-- 3. transaction_history : 거래 내역 (입금, 출금, 이체 등의 모든 거래)
-- ---------------------------------------------------------------------
CREATE TABLE transaction_history (
    transaction_history_id      BIGINT          NOT NULL AUTO_INCREMENT,
    account_id                  BIGINT                   COMMENT '대상 계좌 ID (account 참조)',
    transaction_type            VARCHAR(30)              COMMENT '거래 유형 (입금, 출금, 이체 등)',
    transaction_amount          BIGINT                   COMMENT '거래 금액',
    balance_after_transaction   BIGINT                   COMMENT '거래 후 잔액',
    transaction_content         VARCHAR(255)             COMMENT '거래 내용 (적요)',
    transaction_at              DATETIME(6)              COMMENT '거래 일시',
    transaction_status          VARCHAR(20)              COMMENT '거래 상태 (성공, 실패 등)',
    payment_round               INT                      COMMENT '납입 회차 (예적금/대출인 경우)',
    transaction_sqno            VARCHAR(100)             COMMENT '거래 고유 번호 (중복 방지 식별키)',
    CONSTRAINT pk_transaction_history PRIMARY KEY (transaction_history_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '거래 내역';


-- ---------------------------------------------------------------------
-- 4. account_transfer : 계좌 이체 (송금 및 수취 정보 상세)
-- ---------------------------------------------------------------------
CREATE TABLE account_transfer (
    account_transfer_id         BIGINT          NOT NULL AUTO_INCREMENT,
    account_id                  BIGINT                   COMMENT '출금 계좌 ID',
    withdrawal_account_number   VARCHAR(30)              COMMENT '출금 계좌번호',
    financial_institution_id    BIGINT                   COMMENT '입금 금융기관 ID (financial_institution_code 참조)',
    deposit_account_number      VARCHAR(30)              COMMENT '입금 계좌번호',
    transfer_amount             BIGINT                   COMMENT '이체 금액',
    sender_memo                 VARCHAR(100)             COMMENT '보내는 분 통장 표시 내용',
    receiver_memo               VARCHAR(100)             COMMENT '받는 분 통장 표시 내용',
    transfer_at                 DATETIME(6)              COMMENT '이체 일시',
    transfer_type               VARCHAR(30)              COMMENT '이체 유형 (즉시이체, 예약이체 등)',
    transaction_sqno            VARCHAR(100)             COMMENT '거래 고유 번호',
    CONSTRAINT pk_account_transfer PRIMARY KEY (account_transfer_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '계좌 이체';


-- ---------------------------------------------------------------------
-- 5. account_favorite : 계좌 즐겨찾기 (자주 쓰는 이체 대상 계좌)
-- ---------------------------------------------------------------------
CREATE TABLE account_favorite (
    account_favorite_id         BIGINT          NOT NULL AUTO_INCREMENT,
    account_id                  BIGINT                   COMMENT '회원(또는 본인 계좌) ID',
    financial_institution_id    BIGINT                   COMMENT '상대방 금융기관 ID',
    recipient_account_number    VARCHAR(30)              COMMENT '상대방 계좌번호',
    recipient_account_name      VARCHAR(100)             COMMENT '상대방 예금주명',
    memo                        VARCHAR(255)             COMMENT '메모',
    favorite_alias              VARCHAR(100)             COMMENT '즐겨찾기 별칭',
    favorite_created_at         DATETIME(6)              COMMENT '등록 일시',
    sort_order                  INT                      COMMENT '정렬 순서',
    CONSTRAINT pk_account_favorite PRIMARY KEY (account_favorite_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '계좌 즐겨찾기';


-- ---------------------------------------------------------------------
-- 6. asset_report : 자산 리포트 (특정 기준년월의 자산 증감 및 항목별 리포트)
-- ---------------------------------------------------------------------
CREATE TABLE asset_report (
    asset_report_id                 BIGINT          NOT NULL AUTO_INCREMENT,
    account_id                      BIGINT                   COMMENT '기준 계좌 (또는 회원)',
    recent_deposit_amount           BIGINT                   COMMENT '최근 입금액',
    recent_withdrawal_amount        BIGINT                   COMMENT '최근 출금액',
    savings_amount                  BIGINT                   COMMENT '저축금액',
    loan_interest_repayment_amount  BIGINT                   COMMENT '대출 이자/상환액',
    investment_amount               BIGINT                   COMMENT '투자 금액',
    utility_bill_amount             BIGINT                   COMMENT '공과금 지출액',
    maintenance_fee_amount          BIGINT                   COMMENT '관리비 지출액',
    standard_year_month             CHAR(6)                  COMMENT '기준 년월 (예: 202610)',
    CONSTRAINT pk_asset_report PRIMARY KEY (asset_report_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '자산 리포트';


-- =====================================================================
-- 전체 삭제 (공용 DB에서는 팀 공지 후에만 실행! 데이터가 모두 사라짐)
-- =====================================================================
-- DROP TABLE IF EXISTS asset_report, account_favorite, account_transfer, transaction_history,
--                      account, financial_institution_code;


-- =====================================================================
-- 변경 이력 (공용 DB에 이미 테이블이 있으면 아래 ALTER만 실행)
-- 형식: -- [날짜] 작성자 - 내용  다음 줄에 ALTER 문
-- =====================================================================
-- (아직 없음)
