
SET NAMES utf8mb4;

-- ---------------------------------------------------------------------
-- 1. members : 고객 회원 (직원은 employee 테이블)
-- ---------------------------------------------------------------------
CREATE TABLE members (
    member_id           BIGINT          NOT NULL AUTO_INCREMENT,
    login_id            VARCHAR(30)     NOT NULL COMMENT '로그인 아이디 - 탈퇴 후에도 재사용 불가',
    password            VARCHAR(100)    NOT NULL COMMENT 'BCrypt 해시',
    name                VARCHAR(30)     NOT NULL,
    email               VARCHAR(100)             COMMENT '탈퇴 시 NULL (재가입 허용)',
    phone               VARCHAR(20)              COMMENT '숫자만 저장. 탈퇴 시 NULL (재가입 허용)',
    birth_date          DATE            NOT NULL,
    status              VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',
    verification_status VARCHAR(20)     NOT NULL DEFAULT 'NONE'
                        COMMENT '본인확인 상태: NONE 미확인 / PENDING 심사중 / VERIFIED 완료 / REJECTED 반려',
    login_fail_count    INT             NOT NULL DEFAULT 0,
    login_locked_at     DATETIME(6)              COMMENT '로그인 5회 실패 잠금 시각. NULL이면 잠기지 않음',
    terms_agreed_at     DATETIME(6)     NOT NULL,
    marketing_agree_yn  CHAR(1)         NOT NULL DEFAULT 'N',
    created_at          DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          DATETIME(6),
    withdrawn_at        DATETIME(6),
    CONSTRAINT pk_members PRIMARY KEY (member_id),
    CONSTRAINT uk_members_login_id UNIQUE (login_id),
    CONSTRAINT uk_members_email UNIQUE (email),          -- NULL은 여러 개 허용
    CONSTRAINT uk_members_phone UNIQUE (phone),
    CONSTRAINT ck_members_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'WITHDRAWN')),
    CONSTRAINT ck_members_verification CHECK (verification_status IN ('NONE', 'PENDING', 'VERIFIED', 'REJECTED')),
    CONSTRAINT ck_members_marketing CHECK (marketing_agree_yn IN ('Y', 'N')),
    -- 탈퇴 회원만 이메일·휴대폰이 비어 있을 수 있음
    CONSTRAINT ck_members_contact CHECK (status = 'WITHDRAWN' OR (email IS NOT NULL AND phone IS NOT NULL)),
    -- 탈퇴 상태와 탈퇴일시는 함께 존재
    CONSTRAINT ck_members_withdrawn CHECK (
        (status = 'WITHDRAWN' AND withdrawn_at IS NOT NULL) OR (status <> 'WITHDRAWN' AND withdrawn_at IS NULL)
    )
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '고객 회원 (직원은 employee)';


-- ---------------------------------------------------------------------
-- 2. branch : 지점/ATM (직원의 소속 지점으로도 사용 → employee보다 먼저 생성)
-- ---------------------------------------------------------------------
CREATE TABLE branch (
    branch_id           BIGINT          NOT NULL AUTO_INCREMENT,
    branch_type         VARCHAR(10)     NOT NULL,
    name                VARCHAR(50)     NOT NULL,
    address             VARCHAR(200)    NOT NULL,
    latitude            DOUBLE          NOT NULL,
    longitude           DOUBLE          NOT NULL,
    phone               VARCHAR(20),
    business_hours      VARCHAR(50),
    open_24h_yn         CHAR(1)         NOT NULL DEFAULT 'N',
    use_yn              CHAR(1)         NOT NULL DEFAULT 'Y' COMMENT '삭제 대신 미사용(N) 처리',
    created_at          DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          DATETIME(6),
    CONSTRAINT pk_branch PRIMARY KEY (branch_id),
    CONSTRAINT ck_branch_type CHECK (branch_type IN ('BRANCH', 'ATM')),
    CONSTRAINT ck_branch_open_24h CHECK (open_24h_yn IN ('Y', 'N')),
    CONSTRAINT ck_branch_use CHECK (use_yn IN ('Y', 'N')),
    CONSTRAINT ck_branch_lat CHECK (latitude BETWEEN -90 AND 90),
    CONSTRAINT ck_branch_lng CHECK (longitude BETWEEN -180 AND 180)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '지점/ATM (본점 포함)';

CREATE INDEX ix_branch_type ON branch (branch_type, use_yn);


-- ---------------------------------------------------------------------
-- 2-1. branch_service : 지점/ATM 이용 가능 업무 (지점 1곳에 업무 여러 개)
--    JPA: Branch.services (@ElementCollection + BranchServiceType enum)
--    계좌개설/대출상담/카드발급은 창구 업무라 ATM에 등록 불가 → Entity에서 검사
-- ---------------------------------------------------------------------
CREATE TABLE branch_service (
    branch_id           BIGINT          NOT NULL,
    service_code        VARCHAR(30)     NOT NULL
                        COMMENT 'DEPOSIT 입금 / WITHDRAW 출금 / TRANSFER 이체 / BALANCE_INQUIRY 잔액조회 / ACCOUNT_OPENING 계좌개설 / LOAN_CONSULTING 대출상담 / CARD_ISSUANCE 카드발급',
    CONSTRAINT pk_branch_service PRIMARY KEY (branch_id, service_code),
    CONSTRAINT fk_branch_service_branch FOREIGN KEY (branch_id) REFERENCES branch (branch_id),
    CONSTRAINT ck_branch_service_code CHECK (service_code IN (
        'DEPOSIT', 'WITHDRAW', 'TRANSFER', 'BALANCE_INQUIRY',
        'ACCOUNT_OPENING', 'LOAN_CONSULTING', 'CARD_ISSUANCE'
    ))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '지점/ATM 이용 가능 업무';

-- "입금 가능한 곳" 같은 업무별 필터 (PK는 branch_id가 앞이라 이 검색에 못 씀)
CREATE INDEX ix_branch_service_code ON branch_service (service_code, branch_id);


-- ---------------------------------------------------------------------
-- 3. employee : 은행 직원 (관리자 화면 /admin/** 사용자)
--    role     : STAFF 일반 직원 / MANAGER 책임자 (시스템 관리자는 ADMIN 테이블)
--    position : 직급은 화면 표시용이며 권한과 무관
-- ---------------------------------------------------------------------
CREATE TABLE employee (
    employee_id         BIGINT          NOT NULL AUTO_INCREMENT,
    employee_no         VARCHAR(10)     NOT NULL COMMENT '사번 - 직원 로그인 아이디',
    password            VARCHAR(100)    NOT NULL COMMENT 'BCrypt 해시',
    name                VARCHAR(30)     NOT NULL,
    email               VARCHAR(100)    NOT NULL,
    phone               VARCHAR(20),
    branch_id           BIGINT          NOT NULL COMMENT '소속 지점 (branch_type = BRANCH 인 행만, 본점 포함) - Service에서 검사',
    position            VARCHAR(20)     NOT NULL COMMENT '직급 (사원/대리/과장/차장/부장/지점장 등) - 표시용, 권한과 무관',
    role                VARCHAR(20)     NOT NULL DEFAULT 'STAFF',
    status              VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',
    temp_pw_yn          CHAR(1)         NOT NULL DEFAULT 'Y' COMMENT '임시 비밀번호 여부 - Y면 첫 로그인 시 비밀번호 변경 강제',
    login_fail_count    INT             NOT NULL DEFAULT 0,
    login_locked_at     DATETIME(6),
    hired_at            DATE            NOT NULL,
    retired_at          DATE,
    created_at          DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          DATETIME(6),
    CONSTRAINT pk_employee PRIMARY KEY (employee_id),
    CONSTRAINT uk_employee_no UNIQUE (employee_no),
    CONSTRAINT uk_employee_email UNIQUE (email),
    CONSTRAINT fk_employee_branch FOREIGN KEY (branch_id) REFERENCES branch (branch_id),
    CONSTRAINT ck_employee_role CHECK (role IN ('STAFF', 'MANAGER')),
    CONSTRAINT ck_employee_status CHECK (status IN ('ACTIVE', 'ON_LEAVE', 'RETIRED')),
    CONSTRAINT ck_employee_temp_pw CHECK (temp_pw_yn IN ('Y', 'N')),
    -- 퇴사 상태와 퇴사일은 함께 존재
    CONSTRAINT ck_employee_retired CHECK (
        (status = 'RETIRED' AND retired_at IS NOT NULL) OR (status <> 'RETIRED' AND retired_at IS NULL)
    )
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '은행 직원 (고객 members와 분리)';

CREATE INDEX ix_employee_branch ON employee (branch_id);


-- ---------------------------------------------------------------------
-- 4. identity_verification : 본인확인(비대면 실명확인 mock) 심사 이력
--    반려 후 재신청 시 새 행을 추가한다 (기존 행 수정 X)
--    심사 권한: STAFF 이상
--    승인/반려 시 members.verification_status 도 같은 트랜잭션에서 함께 변경
--      (APPROVED → VERIFIED, REJECTED → REJECTED)
-- ---------------------------------------------------------------------
CREATE TABLE identity_verification (
    verification_id      BIGINT          NOT NULL AUTO_INCREMENT,
    member_id            BIGINT          NOT NULL,
    id_card_type         VARCHAR(20)     NOT NULL,
    id_issue_date        DATE            NOT NULL,
    auth_bank_name       VARCHAR(30)     NOT NULL,
    auth_account_masked  VARCHAR(30)     NOT NULL COMMENT '1원 인증 타행 계좌번호 (마스킹 값만 저장)',
    status               VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    reject_reason        VARCHAR(500),
    requested_at         DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    reviewed_employee_id BIGINT,
    reviewed_at          DATETIME(6),
    CONSTRAINT pk_identity_verification PRIMARY KEY (verification_id),
    CONSTRAINT fk_iv_member FOREIGN KEY (member_id) REFERENCES members (member_id),
    CONSTRAINT fk_iv_employee FOREIGN KEY (reviewed_employee_id) REFERENCES employee (employee_id),
    CONSTRAINT ck_iv_id_card_type CHECK (id_card_type IN ('RESIDENT_CARD', 'DRIVER_LICENSE')),
    CONSTRAINT ck_iv_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    -- 반려 시 사유 필수
    CONSTRAINT ck_iv_reject_reason CHECK (status <> 'REJECTED' OR reject_reason IS NOT NULL),
    -- 심사 완료 건은 심사 직원/일시 필수
    CONSTRAINT ck_iv_reviewed CHECK (status = 'PENDING' OR (reviewed_employee_id IS NOT NULL AND reviewed_at IS NOT NULL))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '본인확인 심사 이력 - 첫 입출금 계좌 개설 시 생성';

-- 회원당 심사 대기(PENDING)는 1건만 허용
-- (함수 인덱스: PENDING이 아니면 NULL이 되고, UNIQUE는 NULL을 여러 개 허용)
CREATE UNIQUE INDEX ux_iv_one_pending
    ON identity_verification ((CASE WHEN status = 'PENDING' THEN member_id END));

CREATE INDEX ix_iv_member ON identity_verification (member_id, requested_at);
CREATE INDEX ix_iv_status ON identity_verification (status, requested_at);


-- ---------------------------------------------------------------------
-- 5. login_log : 고객 로그인 이력 (추가만 하고 수정/삭제하지 않음)
--    없는 아이디로 시도한 경우도 member_id = NULL, fail_reason = UNKNOWN_ID 로 기록
-- ---------------------------------------------------------------------
CREATE TABLE login_log (
    login_log_id        BIGINT          NOT NULL AUTO_INCREMENT,
    member_id           BIGINT                   COMMENT '존재하지 않는 아이디로 시도한 경우 NULL',
    login_id_input      VARCHAR(30)     NOT NULL COMMENT '입력한 아이디 (30자 초과 입력은 잘라서 저장)',
    success_yn          CHAR(1)         NOT NULL,
    fail_reason         VARCHAR(30),
    ip_address          VARCHAR(45)     NOT NULL COMMENT 'IPv6 최대 길이 45',
    user_agent          VARCHAR(500),
    login_at            DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_login_log PRIMARY KEY (login_log_id),
    CONSTRAINT fk_login_log_member FOREIGN KEY (member_id) REFERENCES members (member_id),
    CONSTRAINT ck_login_log_success CHECK (success_yn IN ('Y', 'N')),
    CONSTRAINT ck_login_log_fail_reason CHECK (
        fail_reason IS NULL OR fail_reason IN ('BAD_CREDENTIALS', 'LOGIN_LOCKED', 'SUSPENDED', 'WITHDRAWN', 'UNKNOWN_ID')
    ),
    CONSTRAINT ck_login_log_reason_match CHECK (
        (success_yn = 'Y' AND fail_reason IS NULL) OR (success_yn = 'N' AND fail_reason IS NOT NULL)
    )
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '고객 로그인 이력';

CREATE INDEX ix_login_log_member ON login_log (member_id, login_at DESC);


-- ---------------------------------------------------------------------
-- 6. employee_login_log : 직원 로그인 이력 (추가만 하고 수정/삭제하지 않음)
--    시스템 관리자(ADMIN 테이블)가 관리자 활동 로그 화면에서 조회
-- ---------------------------------------------------------------------
CREATE TABLE employee_login_log (
    login_log_id        BIGINT          NOT NULL AUTO_INCREMENT,
    employee_id         BIGINT                   COMMENT '존재하지 않는 사번으로 시도한 경우 NULL',
    employee_no_input   VARCHAR(30)     NOT NULL COMMENT '입력한 사번 (30자 초과 입력은 잘라서 저장)',
    success_yn          CHAR(1)         NOT NULL,
    fail_reason         VARCHAR(30),
    ip_address          VARCHAR(45)     NOT NULL,
    user_agent          VARCHAR(500),
    login_at            DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_employee_login_log PRIMARY KEY (login_log_id),
    CONSTRAINT fk_ell_employee FOREIGN KEY (employee_id) REFERENCES employee (employee_id),
    CONSTRAINT ck_ell_success CHECK (success_yn IN ('Y', 'N')),
    CONSTRAINT ck_ell_fail_reason CHECK (
        fail_reason IS NULL OR fail_reason IN ('BAD_CREDENTIALS', 'LOGIN_LOCKED', 'ON_LEAVE', 'RETIRED', 'UNKNOWN_ID')
    ),
    CONSTRAINT ck_ell_reason_match CHECK (
        (success_yn = 'Y' AND fail_reason IS NULL) OR (success_yn = 'N' AND fail_reason IS NOT NULL)
    )
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '직원 로그인 이력';

CREATE INDEX ix_ell_employee ON employee_login_log (employee_id, login_at DESC);
CREATE INDEX ix_ell_login_at ON employee_login_log (login_at DESC);


-- ---------------------------------------------------------------------
-- 7. transfer_limit_request : 이체한도 변경 신청 (마이페이지 보안설정 13.5)
--    신청: 팀원1 / 승인 화면: 팀원4 / 실제 한도 반영: 팀원2(account)
--    감액(희망 < 현재): 신청 즉시 APPROVED, 심사 직원 없음 (한도를 내리면 더 안전하므로)
--    증액(희망 > 현재): PENDING → MANAGER 심사
--    account_id 가 member_id 본인의 계좌인지는 Service에서 검사
-- ---------------------------------------------------------------------
CREATE TABLE transfer_limit_request (
    request_id           BIGINT          NOT NULL AUTO_INCREMENT,
    member_id            BIGINT          NOT NULL,
    account_id           BIGINT          NOT NULL,
    current_limit        BIGINT          NOT NULL COMMENT '신청 시점의 1일 이체한도 (원)',
    requested_limit      BIGINT          NOT NULL,
    reason               VARCHAR(300)    NOT NULL,
    status               VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    reject_reason        VARCHAR(500),
    reviewed_employee_id BIGINT,
    reviewed_at          DATETIME(6),
    created_at           DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at           DATETIME(6),
    CONSTRAINT pk_transfer_limit_request PRIMARY KEY (request_id),
    CONSTRAINT fk_tlr_member FOREIGN KEY (member_id) REFERENCES members (member_id),
    CONSTRAINT fk_tlr_employee FOREIGN KEY (reviewed_employee_id) REFERENCES employee (employee_id),
    CONSTRAINT ck_tlr_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    CONSTRAINT ck_tlr_limit CHECK (current_limit >= 0 AND requested_limit > 0 AND requested_limit <> current_limit),
    CONSTRAINT ck_tlr_reject_reason CHECK (status <> 'REJECTED' OR reject_reason IS NOT NULL),
    -- 감액은 항상 즉시 승인(APPROVED)이며 심사 직원·심사일시가 없다
    CONSTRAINT ck_tlr_decrease_auto CHECK (
        requested_limit > current_limit
        OR (status = 'APPROVED' AND reviewed_employee_id IS NULL AND reviewed_at IS NULL)
    ),
    -- 심사가 끝난 증액 건은 심사 직원·심사일시 필수
    CONSTRAINT ck_tlr_reviewed CHECK (
        status = 'PENDING' OR requested_limit < current_limit
        OR (reviewed_employee_id IS NOT NULL AND reviewed_at IS NOT NULL)
    )
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '이체한도 변경 신청';

-- 계좌당 심사 대기 신청은 1건만 (PENDING은 증액 신청만 존재)
CREATE UNIQUE INDEX ux_tlr_one_pending
    ON transfer_limit_request ((CASE WHEN status = 'PENDING' THEN account_id END));

CREATE INDEX ix_tlr_status ON transfer_limit_request (status, created_at);
CREATE INDEX ix_tlr_member ON transfer_limit_request (member_id, created_at);


-- ---------------------------------------------------------------------
-- 8. otp_device : 가상 OTP 기기 (이체용 보안매체, auth.md 4장)
--    상태: ISSUED 발급 → ACTIVE 사용중 → SUSPENDED 정지(오류 10회) / LOST 분실 / DISPOSED 폐기
--    시드는 서버가 TOTP 계산에 다시 써야 하므로 해시가 아니라 AES 암호화로 저장
-- ---------------------------------------------------------------------
CREATE TABLE otp_device (
    otp_device_id       BIGINT          NOT NULL AUTO_INCREMENT,
    member_id           BIGINT          NOT NULL,
    serial_no           VARCHAR(20)     NOT NULL COMMENT 'OTP 일련번호 (기기 화면에 표시)',
    seed_encrypted      VARCHAR(255)    NOT NULL COMMENT 'TOTP 시드 (AES 암호화, 키는 환경변수)',
    status              VARCHAR(20)     NOT NULL DEFAULT 'ISSUED',
    fail_count          INT             NOT NULL DEFAULT 0 COMMENT '연속 오류 횟수 - 10회면 SUSPENDED, 성공 시 0',
    last_used_step      BIGINT                   COMMENT '마지막으로 성공한 30초 구간 번호 (같은 번호 재사용 방지)',
    issued_at           DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    activated_at        DATETIME(6)              COMMENT '사용 등록(연속 2회 입력) 완료 일시',
    updated_at          DATETIME(6),
    CONSTRAINT pk_otp_device PRIMARY KEY (otp_device_id),
    CONSTRAINT uk_otp_device_serial UNIQUE (serial_no),
    CONSTRAINT fk_otp_device_member FOREIGN KEY (member_id) REFERENCES members (member_id),
    CONSTRAINT ck_otp_device_status CHECK (status IN ('ISSUED', 'ACTIVE', 'SUSPENDED', 'LOST', 'DISPOSED')),
    -- 사용 등록을 마친 기기(사용중/정지)는 등록일시 필수
    CONSTRAINT ck_otp_device_activated CHECK (status NOT IN ('ACTIVE', 'SUSPENDED') OR activated_at IS NOT NULL)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '가상 OTP 기기 - 분실/폐기 이력도 보관';

-- 회원당 사용 가능한(발급·사용중·정지) OTP는 1개만
CREATE UNIQUE INDEX ux_otp_device_one_usable
    ON otp_device ((CASE WHEN status IN ('ISSUED', 'ACTIVE', 'SUSPENDED') THEN member_id END));

CREATE INDEX ix_otp_device_member ON otp_device (member_id);

