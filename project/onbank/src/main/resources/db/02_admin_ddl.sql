-- ============================================================
-- 모의 인터넷뱅킹 관리자(백오피스) 파트 DDL (MySQL 8.0.16+ 기준, 초안 v0.7)
-- 소유: admin, admin_log, fds_detection, fds_handling_history, admin_notification, admin_login_log
-- admin = 백오피스 시스템 관리자 / employee = 은행 업무 직원 (별개 테이블, 둘 다 관리자 콘솔 /admin/** 로그인 가능)
-- 실행 순서: 팀원2 DDL의 `account`, `account_transfer`, `transaction_history` 테이블이 먼저 생성되고,
--            PK를 거는 ALTER TABLE까지 실행되어 있어야 FK가 걸립니다.
-- 이름 규칙: 테이블·컬럼은 소문자(팀원1·팀원2와 같음). FK 컬럼명은 참조하는 컬럼명과 같게 쓰고,
--            역할 구분이 필요하면 앞에 붙인다 (예: assigned_admin_id, 팀원1의 reviewed_employee_id와 같은 방식)
-- v0.3: ROLE을 SUPER_ADMIN/OPERATOR/SERVICE로 분리(기본값 없음), ACCOUNT FK 활성화
-- v0.4: ADMIN.TEMP_PW_YN 추가 (임시 비밀번호 여부, 기본 'Y')
-- v0.5: ADMIN_LOG.ADMIN_ID(FK) -> ACTOR_TYPE + ACTOR_ID (ADMIN/EMPLOYEE 둘 다 기록, 두 테이블을 가리키므로 FK 없음)
--       ADMIN_NOTIFICATION.ADMIN_ID(FK) -> RECIPIENT_TYPE + RECIPIENT_ID (창구 직원도 알림 수신)
-- v0.6: ADMIN_LOGIN_LOG 추가 (ADMIN 로그인 이력. 직원은 팀원1의 employee_login_log 사용)
-- v0.7: 팀원2 DDL 반영
--       - 테이블명·컬럼명 전부 소문자로 변경 (제약·인덱스 이름과 저장되는 코드값은 대문자 유지)
--       - fds_detection: TRANSFER(transfer_id) FK -> account_transfer(account_transfer_id) FK,
--         컬럼명 TRANSFER_ID -> account_transfer_id, ASSIGNED_TO -> assigned_admin_id
--       - fds_detection: 출금도 탐지하도록 transaction_history_id 추가.
--         account_transfer_id / transaction_history_id 둘 다 NULL 허용, 둘 중 하나는 반드시 있어야 함 (CK_FDS_DET_SOURCE)
-- ============================================================

-- 재실행용 삭제 (자식 -> 부모 순서)
-- DROP TABLE IF EXISTS admin_login_log;
-- DROP TABLE IF EXISTS admin_notification;
-- DROP TABLE IF EXISTS fds_handling_history;
-- DROP TABLE IF EXISTS fds_detection;
-- DROP TABLE IF EXISTS admin_log;
-- DROP TABLE IF EXISTS admin;

-- 1. 관리자 계정
CREATE TABLE admin
(
    admin_id         BIGINT       NOT NULL AUTO_INCREMENT,
    login_id         VARCHAR(50)  NOT NULL,
    password         VARCHAR(100) NOT NULL COMMENT 'BCrypt 해시(60자)',
    name             VARCHAR(50)  NOT NULL,
    role             VARCHAR(20)  NOT NULL COMMENT 'SUPER_ADMIN / OPERATOR / SERVICE (기본값 없음: 계정 생성 시 반드시 지정)',
    status           VARCHAR(10)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE / INACTIVE / LOCKED',
    temp_pw_yn       CHAR(1)      NOT NULL DEFAULT 'Y' COMMENT '임시 비밀번호 여부 - Y면 첫 로그인 시 비밀번호 변경 강제',
    login_fail_count TINYINT      NOT NULL DEFAULT 0 COMMENT '5회 실패 시 LOCKED',
    last_login_at    DATETIME     NULL,
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (admin_id),
    UNIQUE KEY UK_ADMIN_LOGIN_ID (login_id),
    CONSTRAINT CK_ADMIN_ROLE CHECK (role IN ('SUPER_ADMIN', 'OPERATOR', 'SERVICE')),
    CONSTRAINT CK_ADMIN_STATUS CHECK (status IN ('ACTIVE', 'INACTIVE', 'LOCKED')),
    CONSTRAINT CK_ADMIN_TEMP_PW CHECK (temp_pw_yn IN ('Y', 'N'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='관리자 계정';

-- 2. 관리자 활동 로그 (감사 로그: 수정/삭제 기능을 만들지 않음)
CREATE TABLE admin_log
(
    log_id       BIGINT       NOT NULL AUTO_INCREMENT,
    actor_type   VARCHAR(10)  NOT NULL COMMENT 'ADMIN / EMPLOYEE (작업한 사람이 속한 테이블)',
    actor_id     BIGINT       NOT NULL COMMENT 'admin_id 또는 employee_id. 두 테이블을 가리키므로 FK 없음',
    action_type  VARCHAR(50)  NOT NULL COMMENT '예: ACCOUNT_FREEZE',
    target_type  VARCHAR(30)  NOT NULL COMMENT 'MEMBER / ACCOUNT / PRODUCT / CARD ...',
    target_id    VARCHAR(50)  NOT NULL COMMENT '대상이 여러 테이블이라 FK 없음',
    before_value VARCHAR(500) NULL,
    after_value  VARCHAR(500) NULL,
    reason       VARCHAR(200) NULL,
    request_ip   VARCHAR(45)  NULL COMMENT 'IPv6 대비 45자',
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (log_id),
    KEY IDX_ADMIN_LOG_ACTOR (actor_type, actor_id),
    KEY IDX_ADMIN_LOG_CREATED (created_at),
    KEY IDX_ADMIN_LOG_TARGET (target_type, target_id),
    CONSTRAINT CK_ADMIN_LOG_ACTOR CHECK (actor_type IN ('ADMIN', 'EMPLOYEE'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='관리자 활동 로그';

-- 3. 이상거래 탐지 (이체 + 출금)
CREATE TABLE fds_detection
(
    detection_id           BIGINT       NOT NULL AUTO_INCREMENT,
    account_id             BIGINT       NOT NULL COMMENT '대상 계좌 (출금 계좌)',
    account_transfer_id    BIGINT       NULL COMMENT '탐지 근거가 된 이체. 이체가 아닌 출금이면 NULL',
    transaction_history_id BIGINT       NULL COMMENT '탐지 근거가 된 거래내역(출금). 이체 건이면 NULL일 수 있음',
    rule_code              VARCHAR(30)  NOT NULL COMMENT '탐지 규칙 구분',
    detail                 VARCHAR(500) NULL COMMENT '판정 근거(횟수, 금액 등)',
    status                 VARCHAR(20)  NOT NULL DEFAULT 'UNCHECKED',
    assigned_admin_id      BIGINT       NULL COMMENT '담당 관리자, 배정 전에는 NULL',
    assigned_at            DATETIME     NULL,
    closed_at              DATETIME     NULL,
    detected_at            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (detection_id),
    KEY IDX_FDS_DET_STATUS (status, detected_at),
    KEY IDX_FDS_DET_ASSIGNED (assigned_admin_id),
    KEY IDX_FDS_DET_ACCOUNT (account_id),
    KEY IDX_FDS_DET_TRANSFER (account_transfer_id),
    KEY IDX_FDS_DET_HISTORY (transaction_history_id),
    CONSTRAINT FK_FDS_DET_ADMIN FOREIGN KEY (assigned_admin_id) REFERENCES admin (admin_id),
    CONSTRAINT FK_FDS_DET_ACCOUNT FOREIGN KEY (account_id) REFERENCES account (account_id),
    CONSTRAINT FK_FDS_DET_TRANSFER FOREIGN KEY (account_transfer_id) REFERENCES account_transfer (account_transfer_id),
    CONSTRAINT FK_FDS_DET_HISTORY FOREIGN KEY (transaction_history_id) REFERENCES transaction_history (transaction_history_id),
    CONSTRAINT CK_FDS_DET_STATUS CHECK (status IN
                                        ('UNCHECKED', 'ASSIGNED', 'CONTACTING', 'NORMAL_CONFIRMED', 'FRAUD_ACTION')),
    CONSTRAINT CK_FDS_DET_SOURCE CHECK (account_transfer_id IS NOT NULL OR transaction_history_id IS NOT NULL)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='이상거래 탐지';

-- 4. 이상거래 처리 이력 (단계 변경 + 고객 연락 결과)
CREATE TABLE fds_handling_history
(
    history_id     BIGINT       NOT NULL AUTO_INCREMENT,
    detection_id   BIGINT       NOT NULL,
    admin_id       BIGINT       NOT NULL,
    stage          VARCHAR(20)  NOT NULL COMMENT '변경된 단계',
    memo           VARCHAR(500) NULL,
    contact_at     DATETIME     NULL COMMENT '고객 연락 단계에서만 입력',
    contact_method VARCHAR(20)  NULL COMMENT '전화 등',
    contact_result VARCHAR(30)  NULL COMMENT 'CONFIRMED_BY_CUSTOMER / NOT_CUSTOMER / NO_ANSWER',
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (history_id),
    KEY IDX_FDS_HIS_DETECTION (detection_id, created_at),
    KEY IDX_FDS_HIS_ADMIN (admin_id),
    CONSTRAINT FK_FDS_HIS_DETECTION FOREIGN KEY (detection_id) REFERENCES fds_detection (detection_id),
    CONSTRAINT FK_FDS_HIS_ADMIN FOREIGN KEY (admin_id) REFERENCES admin (admin_id),
    CONSTRAINT CK_FDS_HIS_STAGE CHECK (stage IN
                                       ('UNCHECKED', 'ASSIGNED', 'CONTACTING', 'NORMAL_CONFIRMED', 'FRAUD_ACTION')),
    CONSTRAINT CK_FDS_HIS_CONTACT_RESULT CHECK (contact_result IS NULL OR contact_result IN
                                                                          ('CONFIRMED_BY_CUSTOMER', 'NOT_CUSTOMER',
                                                                           'NO_ANSWER'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='이상거래 처리 이력';

-- 5. 관리자·직원 알림함 (받는 사람이 admin 또는 employee)
CREATE TABLE admin_notification
(
    notification_id BIGINT       NOT NULL AUTO_INCREMENT,
    recipient_type  VARCHAR(10)  NOT NULL COMMENT 'ADMIN / EMPLOYEE (받는 사람이 속한 테이블)',
    recipient_id    BIGINT       NOT NULL COMMENT 'admin_id 또는 employee_id. 두 테이블을 가리키므로 FK 없음',
    type            VARCHAR(30)  NOT NULL COMMENT '예: FDS_DETECTED',
    target_id       BIGINT       NULL COMMENT '탐지 ID 등',
    message         VARCHAR(300) NOT NULL,
    read_at         DATETIME     NULL COMMENT 'NULL이면 미확인',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (notification_id),
    KEY IDX_ADMIN_NOTI_RECIPIENT (recipient_type, recipient_id, read_at, created_at),
    CONSTRAINT CK_ADMIN_NOTI_RECIPIENT CHECK (recipient_type IN ('ADMIN', 'EMPLOYEE'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='관리자·직원 알림함';

-- 6. 관리자 로그인 이력 (추가만 하고 수정/삭제하지 않음. 팀원1 employee_login_log와 같은 모양)
CREATE TABLE admin_login_log
(
    login_log_id   BIGINT       NOT NULL AUTO_INCREMENT,
    admin_id       BIGINT       NULL COMMENT '존재하지 않는 아이디로 시도한 경우 NULL',
    login_id_input VARCHAR(50)  NOT NULL COMMENT '입력한 아이디 (50자 초과 입력은 잘라서 저장)',
    success_yn     CHAR(1)      NOT NULL,
    fail_reason    VARCHAR(30)  NULL,
    ip_address     VARCHAR(45)  NOT NULL COMMENT 'IPv6 대비 45자',
    user_agent     VARCHAR(500) NULL,
    login_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (login_log_id),
    KEY IDX_ADMIN_LOGIN_ADMIN (admin_id, login_at),
    KEY IDX_ADMIN_LOGIN_AT (login_at),
    CONSTRAINT FK_ADMIN_LOGIN_ADMIN FOREIGN KEY (admin_id) REFERENCES admin (admin_id),
    CONSTRAINT CK_ADMIN_LOGIN_SUCCESS CHECK (success_yn IN ('Y', 'N')),
    CONSTRAINT CK_ADMIN_LOGIN_FAIL_REASON CHECK (
        fail_reason IS NULL OR fail_reason IN ('BAD_CREDENTIALS', 'LOGIN_LOCKED', 'INACTIVE', 'UNKNOWN_ID')),
    CONSTRAINT CK_ADMIN_LOGIN_MATCH CHECK (
        (success_yn = 'Y' AND fail_reason IS NULL) OR (success_yn = 'N' AND fail_reason IS NOT NULL))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='관리자 로그인 이력';