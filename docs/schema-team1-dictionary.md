# 팀원1 테이블 데이터 사전

[schema-team1-mysql.sql](../schema-team1-mysql.sql)에 정의된 테이블 9개의 컬럼 설명입니다.
테이블 정의의 기준은 SQL 파일이며, SQL 파일을 수정하면 이 문서도 함께 고칩니다.

| 구분 | 테이블 |
|---|---|
| 고객 | [members](#1-members--고객-회원), [identity_verification](#4-identity_verification--본인확인-심사-이력), [login_log](#5-login_log--고객-로그인-이력), [transfer_limit_request](#7-transfer_limit_request--이체한도-변경-신청), [otp_device](#8-otp_device--가상-otp-기기) |
| 직원 | [employee](#3-employee--은행-직원), [employee_login_log](#6-employee_login_log--직원-로그인-이력) |
| 공통 | [branch](#2-branch--지점atm), [branch_service](#2-1-branch_service--지점별-가능-업무) |

---

## 공통 규칙

- **`_yn` 컬럼**은 CHAR(1)에 `Y`/`N`을 저장합니다. 엔티티에서는 `boolean` 필드에 `@Convert(converter = YesNoConverter.class)`를 붙여 매핑합니다.
- **상태 컬럼**(`status` 등)은 VARCHAR에 enum 이름을 저장하고, CHECK 제약으로 허용 값을 제한합니다. 엔티티에서는 `@Enumerated(EnumType.STRING)`을 씁니다.
- **지우지 않고 상태를 바꿉니다.** 회원 탈퇴, 지점 폐점, 직원 퇴사, OTP 분실 모두 행을 남긴 채 상태값만 바꿉니다. 금융 데이터는 이력을 남겨야 하고, FK로 연결된 행도 있기 때문입니다.
- **로그 테이블**(`login_log`, `employee_login_log`)은 추가만 하고 고치지 않으므로 `updated_at`이 없습니다.
- **`updated_at`** 에는 MySQL의 `ON UPDATE CURRENT_TIMESTAMP`가 없어서 DB가 값을 넣어 주지 않습니다. 엔티티에서 `@UpdateTimestamp`나 JPA Auditing(`@LastModifiedDate`)으로 채워야 합니다.
- **DATETIME(6)** 의 `(6)`은 마이크로초까지 저장한다는 뜻입니다. 엔티티에서는 `LocalDateTime`으로 매핑합니다.

---

## 1. members — 고객 회원

인터넷뱅킹에 가입한 고객입니다. 직원은 이 테이블에 넣지 않고 `employee`에 따로 저장합니다.
`MEMBER`는 MySQL 8 예약어라서 테이블 이름을 `members`로 했습니다(엔티티에 `@Table(name = "members")`).

| 컬럼 | 타입 / 제약 | 설명 |
|---|---|---|
| `member_id` | BIGINT, PK, 자동 증가 | 회원 번호입니다. 다른 테이블(`login_log`, `otp_device` 등)이 이 값으로 회원을 참조합니다. |
| `login_id` | VARCHAR(30), NOT NULL, UNIQUE | 로그인 아이디입니다. 탈퇴해도 행을 지우지 않고 값도 남겨 두므로, 같은 아이디로 다시 가입할 수 없습니다. 탈퇴한 사람의 아이디를 다른 사람이 가져가 사칭하는 일을 막기 위해서입니다. |
| `password` | VARCHAR(100), NOT NULL | BCrypt로 해시한 비밀번호입니다. 해시 결과는 60자이며, 알고리즘을 바꿀 경우를 생각해 100자로 잡았습니다. |
| `name` | VARCHAR(30), NOT NULL | 고객 실명입니다. |
| `email` | VARCHAR(100), UNIQUE, NULL 허용 | 이메일입니다. 탈퇴하면 NULL로 지웁니다. MySQL의 UNIQUE는 NULL이 여러 개 있어도 되므로, 탈퇴한 사람의 이메일로 새로 가입할 수 있습니다. |
| `phone` | VARCHAR(20), UNIQUE, NULL 허용 | 휴대폰 번호입니다. 하이픈 없이 숫자만 저장합니다(예: `01012345678`). 숫자만 저장해야 `010-1234-5678`과 `01012345678`이 서로 다른 번호로 취급되는 중복을 막을 수 있습니다. 탈퇴하면 NULL로 지웁니다. |
| `birth_date` | DATE, NOT NULL | 생년월일입니다. 본인확인과 연령 확인에 씁니다. |
| `status` | VARCHAR(20), 기본값 `ACTIVE` | 회원 상태입니다. `ACTIVE` 정상, `LOCKED` 로그인 잠금(비밀번호 5회 오류, 시스템이 자동으로 걸고 해제하면 `ACTIVE`), `SUSPENDED` 정지(관리자 조치), `WITHDRAWN` 탈퇴 중 하나입니다. |
| `verification_status` | VARCHAR(20), 기본값 `NONE` | 본인확인 결과입니다. `NONE` 미신청, `PENDING` 심사중, `VERIFIED` 완료, `REJECTED` 반려 중 하나입니다. 상세 심사 이력은 `identity_verification`에 있고, 이 컬럼에는 가장 최근 결과만 담습니다. 매번 이력 테이블을 조회하지 않아도 되도록 둔 것입니다. |
| `login_fail_count` | INT, 기본값 0 | 비밀번호를 연속으로 틀린 횟수입니다. 로그인에 성공하면 0으로 되돌립니다. |
| `login_locked_at` | DATETIME(6), NULL 허용 | 5회 연속 실패로 계정이 잠긴 시각입니다(기록용). 잠김 여부는 `status = LOCKED`로 판단합니다. 해제하면 NULL로 돌아갑니다. |
| `terms_agreed_at` | DATETIME(6), NOT NULL | 필수 약관에 동의한 시각입니다. 동의하지 않으면 가입할 수 없으므로 NOT NULL입니다. |
| `marketing_agree_yn` | CHAR(1), 기본값 `N` | 마케팅 수신 동의 여부(선택 항목)입니다. |
| `created_at` | DATETIME(6), 기본값 현재 시각 | 가입 일시입니다. |
| `updated_at` | DATETIME(6), NULL 허용 | 마지막으로 수정한 일시입니다. 애플리케이션에서 넣습니다. |
| `withdrawn_at` | DATETIME(6), NULL 허용 | 탈퇴 일시입니다. |

**CHECK 제약**
- `ck_members_contact`: 탈퇴 회원이 아니면 이메일과 휴대폰이 모두 있어야 합니다.
- `ck_members_withdrawn`: `status`가 `WITHDRAWN`이면 `withdrawn_at`이 반드시 있어야 하고, 그 밖의 상태에서는 반드시 NULL이어야 합니다. 상태값과 탈퇴 일시가 서로 어긋나지 않게 합니다.

---

## 2. branch — 지점/ATM

지점 찾기 화면에 쓰고, 직원이 어느 지점 소속인지 나타내는 데도 씁니다. 그래서 `employee`보다 먼저 만듭니다.

| 컬럼 | 타입 / 제약 | 설명 |
|---|---|---|
| `branch_id` | BIGINT, PK | 지점 번호입니다. |
| `branch_type` | VARCHAR(10), NOT NULL | `BRANCH`(창구가 있는 영업점, 본점 포함) 또는 `ATM`(무인 기기)입니다. |
| `name` | VARCHAR(50) | 지점 이름입니다(예: "강남역지점", "강남역 2번출구 ATM"). |
| `address` | VARCHAR(200) | 도로명 주소입니다. |
| `latitude` | DOUBLE, -90~90 | 위도입니다. 지도에 표시하고 거리를 계산할 때 씁니다. |
| `longitude` | DOUBLE, -180~180 | 경도입니다. |
| `phone` | VARCHAR(20), NULL 허용 | 지점 대표 번호입니다. ATM은 보통 비어 있습니다. |
| `business_hours` | VARCHAR(50), NULL 허용 | 화면에 그대로 보여 줄 영업시간 문자열입니다(예: `평일 09:00~16:00`). 검색 조건으로는 쓰지 않습니다. |
| `open_24h_yn` | CHAR(1), 기본값 `N` | 24시간 운영 여부입니다. "24시간 ATM 찾기" 같은 필터에 씁니다. |
| `use_yn` | CHAR(1), 기본값 `Y` | 사용 여부입니다. 폐점해도 행을 지우지 않고 `N`으로 바꿉니다. 직원 소속 같은 FK가 이 행을 참조하고 있기 때문입니다. |
| `created_at` / `updated_at` | DATETIME(6) | 등록 일시와 수정 일시입니다. |

**인덱스**
- `ix_branch_type (branch_type, use_yn)`: "사용 중인 ATM만" 같은 조회를 빠르게 합니다.

---

## 2-1. branch_service — 지점별 가능 업무

지점 하나가 업무 여러 개를 가질 수 있는 1:N 관계입니다. JPA에서는 `Branch.services`(`@ElementCollection` + `BranchServiceType` enum)로 매핑합니다.

| 컬럼 | 타입 / 제약 | 설명 |
|---|---|---|
| `branch_id` | BIGINT, PK·FK → branch | 어느 지점의 업무인지 나타냅니다. |
| `service_code` | VARCHAR(30), PK | 업무 코드입니다. `DEPOSIT` 입금, `WITHDRAW` 출금, `TRANSFER` 이체, `BALANCE_INQUIRY` 잔액조회, `ACCOUNT_OPENING` 계좌개설, `LOAN_CONSULTING` 대출상담, `CARD_ISSUANCE` 카드발급 중 하나입니다. |

- PK가 (branch_id, service_code)이므로 한 지점에 같은 업무를 두 번 등록할 수 없습니다.
- 계좌개설, 대출상담, 카드발급은 창구 업무라서 ATM에는 등록하면 안 됩니다. 이 검사는 DB가 아니라 Entity에서 합니다.
- `ix_branch_service_code (service_code, branch_id)`: "입금 가능한 지점 목록"처럼 업무로 지점을 찾는 조회에 씁니다. PK는 branch_id가 앞에 있어서 이 조회에 쓸 수 없기 때문에 인덱스를 따로 만들었습니다.

---

## 3. employee — 은행 직원

관리자 화면(`/admin/**`)에 로그인하는 사용자입니다. 고객(`members`)과 테이블도 로그인도 완전히 분리합니다.

| 컬럼 | 타입 / 제약 | 설명 |
|---|---|---|
| `employee_id` | BIGINT, PK | 직원 번호입니다. 내부 식별용입니다. |
| `employee_no` | VARCHAR(10), UNIQUE | 사번입니다. 직원은 이 값으로 로그인합니다(예: `E2026001`). |
| `password` | VARCHAR(100) | BCrypt로 해시한 비밀번호입니다. |
| `name` | VARCHAR(30) | 직원 이름입니다. |
| `email` | VARCHAR(100), NOT NULL, UNIQUE | 회사 이메일입니다. 직원은 탈퇴 개념이 없으므로 NOT NULL입니다. |
| `phone` | VARCHAR(20), NULL 허용 | 연락처입니다. |
| `branch_id` | BIGINT, FK → branch | 소속 지점입니다. `branch_type`이 `BRANCH`인 지점만 넣을 수 있고, ATM 소속은 안 됩니다. 이 검사는 Service에서 합니다. |
| `position` | VARCHAR(20) | 직급(사원, 대리, 과장, 지점장 등)입니다. 화면에 보여 주기만 하고 권한에는 영향을 주지 않습니다. |
| `role` | VARCHAR(20), 기본값 `STAFF` | 권한입니다. `STAFF` 일반 직원, `MANAGER` 책임자 순으로 권한이 넓어집니다. 시스템 관리자는 이 테이블이 아니라 팀원4의 `ADMIN` 테이블(02_admin_ddl.sql)에서 관리합니다. Spring Security 권한 검사는 이 값으로 합니다. 직급과 권한을 분리했기 때문에, 예를 들어 과장도 MANAGER 권한을 받을 수 있습니다. |
| `status` | VARCHAR(20), 기본값 `ACTIVE` | `ACTIVE` 재직, `LOCKED` 로그인 잠금(비밀번호 5회 오류), `ON_LEAVE` 휴직, `RETIRED` 퇴사 중 하나입니다. 재직(`ACTIVE`) 상태가 아니면 로그인할 수 없습니다. |
| `temp_pw_yn` | CHAR(1), 기본값 `Y` | 임시 비밀번호인지 여부입니다. 직원 계정은 관리자가 임시 비밀번호로 만들기 때문에 기본값이 `Y`이고, 첫 로그인 때 비밀번호를 바꾸게 합니다. |
| `login_fail_count` | INT | 로그인 연속 실패 횟수입니다. members와 같은 방식입니다. |
| `login_locked_at` | DATETIME(6) | 계정이 잠긴 시각입니다(기록용). 잠김 여부는 `status = LOCKED`로 판단합니다. members와 같은 방식입니다. |
| `hired_at` | DATE, NOT NULL | 입사일입니다. |
| `retired_at` | DATE, NULL 허용 | 퇴사일입니다. |
| `created_at` / `updated_at` | DATETIME(6) | 등록 일시와 수정 일시입니다. |

**제약 / 인덱스**
- `ck_employee_retired`: `status`가 `RETIRED`이면 `retired_at`이 반드시 있어야 하고, 그 밖의 상태에서는 반드시 NULL이어야 합니다.
- `ix_employee_branch`: 지점별 직원 목록 조회에 씁니다.

---

## 4. identity_verification — 본인확인 심사 이력

비대면 실명확인(mock)에서 고객이 신청하고 직원이 승인하거나 반려한 기록입니다. 고객이 첫 입출금 계좌를 개설할 때 생깁니다.

| 컬럼 | 타입 / 제약 | 설명 |
|---|---|---|
| `verification_id` | BIGINT, PK | 심사 건 번호입니다. |
| `member_id` | BIGINT, FK → members | 신청한 회원입니다. |
| `id_card_type` | VARCHAR(20) | 신분증 종류입니다. `RESIDENT_CARD` 주민등록증 또는 `DRIVER_LICENSE` 운전면허증입니다. |
| `id_issue_date` | DATE | 신분증 발급일자입니다. 실제 진위확인 서비스는 발급일자로 신분증이 유효한지 확인하므로 그 방식을 흉내 낸 것입니다. |
| `auth_bank_name` | VARCHAR(30) | 1원 인증에 쓴 다른 은행 이름입니다. |
| `auth_account_masked` | VARCHAR(30) | 1원 인증 계좌번호입니다. 개인정보라서 마스킹한 값만 저장합니다(예: `123-****-7890`). |
| `status` | VARCHAR(20), 기본값 `PENDING` | `PENDING` 심사 대기, `APPROVED` 승인, `REJECTED` 반려 중 하나입니다. |
| `reject_reason` | VARCHAR(500) | 반려 사유입니다. 반려할 때는 반드시 넣어야 합니다(`ck_iv_reject_reason`). |
| `requested_at` | DATETIME(6) | 신청 일시입니다. |
| `reviewed_employee_id` | BIGINT, FK → employee | 심사한 직원입니다. STAFF 이상이면 심사할 수 있습니다. |
| `reviewed_at` | DATETIME(6) | 심사 일시입니다. |

**규칙**
- 반려된 뒤 다시 신청하면 기존 행을 고치지 않고 새 행을 추가합니다. 그래야 이력이 모두 남습니다.
- 승인하거나 반려할 때는 같은 트랜잭션 안에서 `members.verification_status`도 함께 바꿉니다(`APPROVED`이면 `VERIFIED`, `REJECTED`이면 `REJECTED`).
- `ck_iv_reviewed`: 심사가 끝난 건(PENDING이 아닌 건)은 심사 직원과 심사 일시가 반드시 있어야 합니다.
- `ux_iv_one_pending`: 회원 한 명당 PENDING 건은 하나만 있을 수 있습니다. 함수 인덱스를 쓰는데, 상태가 PENDING일 때만 member_id 값이 들어가고 나머지는 NULL이 됩니다. UNIQUE 인덱스는 NULL을 중복으로 보지 않으므로, PENDING 건끼리만 중복이 막힙니다.
- `ix_iv_member`는 회원별 이력 조회에, `ix_iv_status`는 관리자의 "심사 대기 목록"에 씁니다.

---

## 5. login_log — 고객 로그인 이력

로그인 시도를 성공·실패 모두 기록합니다. 감사 기록이므로 행을 추가만 하고, 고치거나 지우지 않습니다.

| 컬럼 | 타입 / 제약 | 설명 |
|---|---|---|
| `login_log_id` | BIGINT, PK | 로그 번호입니다. |
| `member_id` | BIGINT, FK, NULL 허용 | 시도한 회원입니다. 없는 아이디로 시도했으면 NULL입니다. |
| `login_id_input` | VARCHAR(30) | 사용자가 실제로 입력한 아이디입니다. 없는 아이디로 시도한 경우도 추적할 수 있습니다(무차별 대입 공격 탐지 등). 30자가 넘으면 잘라서 저장합니다. |
| `success_yn` | CHAR(1) | 성공했는지 여부입니다. |
| `fail_reason` | VARCHAR(30) | 실패 사유입니다. `BAD_CREDENTIALS` 비밀번호 오류, `LOGIN_LOCKED` 잠긴 계정, `SUSPENDED` 정지 회원, `WITHDRAWN` 탈퇴 회원, `UNKNOWN_ID` 없는 아이디 중 하나입니다. |
| `ip_address` | VARCHAR(45) | 접속한 IP입니다. IPv6 주소의 최대 길이인 45자로 잡았습니다. |
| `user_agent` | VARCHAR(500) | 브라우저와 OS 정보(User-Agent 헤더)입니다. |
| `login_at` | DATETIME(6) | 시도한 일시입니다. |

- `ck_login_log_reason_match`: 성공이면 실패 사유가 없어야 하고, 실패면 실패 사유가 반드시 있어야 합니다.
- `ix_login_log_member (member_id, login_at DESC)`: 마이페이지의 "최근 로그인 기록" 조회에 씁니다.

---

## 6. employee_login_log — 직원 로그인 이력

`login_log`의 직원용 테이블입니다. 시스템 관리자(`ADMIN` 테이블)가 관리자 활동 로그 화면에서 조회합니다.

| 컬럼 | 타입 / 제약 | 설명 |
|---|---|---|
| `login_log_id` | BIGINT, PK | 로그 번호입니다. |
| `employee_id` | BIGINT, FK, NULL 허용 | 시도한 직원입니다. 없는 사번이면 NULL입니다. |
| `employee_no_input` | VARCHAR(30) | 입력한 사번입니다. 30자가 넘으면 잘라서 저장합니다. |
| `success_yn` | CHAR(1) | 성공 여부입니다. |
| `fail_reason` | VARCHAR(30) | 실패 사유입니다. `BAD_CREDENTIALS`, `LOGIN_LOCKED`, `ON_LEAVE` 휴직, `RETIRED` 퇴사, `UNKNOWN_ID` 중 하나입니다. 고객과 달리 정지·탈퇴 대신 휴직·퇴사가 있습니다. |
| `ip_address` / `user_agent` / `login_at` | | `login_log`와 같습니다. |

- `ck_ell_reason_match`: `login_log`와 같은 규칙입니다.
- `ix_ell_employee`는 직원별 로그 조회에 씁니다.
- `ix_ell_login_at`은 관리자가 직원 구분 없이 전체 로그를 최신순으로 볼 때 씁니다.

---

## 7. transfer_limit_request — 이체한도 변경 신청

마이페이지 보안설정(13.5)에서 신청합니다. 맡은 사람이 나뉘어 있습니다. 신청 화면은 팀원1, 승인 화면은 팀원4, 실제로 계좌 한도를 바꾸는 것은 팀원2(account)가 합니다.

| 컬럼 | 타입 / 제약 | 설명 |
|---|---|---|
| `request_id` | BIGINT, PK | 신청 번호입니다. |
| `member_id` | BIGINT, FK → members | 신청한 회원입니다. |
| `account_id` | BIGINT | 대상 계좌입니다. FK는 팀원2의 account 테이블을 만든 뒤에 추가합니다. 이 계좌가 신청한 회원 본인의 것인지는 Service에서 검사합니다. |
| `current_limit` | BIGINT | 신청할 때의 1일 이체한도(원)입니다. 나중에 한도가 바뀌어도 신청 당시 값을 남기려고 따로 저장합니다(스냅샷). |
| `requested_limit` | BIGINT, 0보다 큼 | 희망 한도입니다. |
| `reason` | VARCHAR(300), NOT NULL | 신청 사유입니다. |
| `status` | VARCHAR(20), 기본값 `PENDING` | `PENDING`, `APPROVED`, `REJECTED` 중 하나입니다. |
| `reject_reason` | VARCHAR(500) | 반려 사유입니다. 반려할 때는 반드시 넣어야 합니다. |
| `reviewed_employee_id` | BIGINT, FK → employee | 심사한 책임자(MANAGER)입니다. |
| `reviewed_at` | DATETIME(6) | 심사 일시입니다. |
| `created_at` / `updated_at` | DATETIME(6) | 신청 일시와 수정 일시입니다. |

**업무 규칙과 제약**
- **감액**(희망 한도가 현재 한도보다 낮음): 신청하는 즉시 `APPROVED`가 되고 심사 직원이 없습니다(`ck_tlr_decrease_auto`). 한도를 낮추는 쪽은 더 안전하기 때문입니다. 그래서 감액 건은 PENDING이나 REJECTED 상태가 될 수 없습니다.
- **증액**: `PENDING`으로 들어가고, MANAGER가 심사합니다. 심사가 끝나면 심사 직원과 심사 일시가 반드시 있어야 합니다(`ck_tlr_reviewed`).
- `ck_tlr_limit`: 희망 한도가 현재 한도와 같으면 저장할 수 없습니다.
- `ux_tlr_one_pending`: 계좌 하나당 심사 대기 건은 하나만 있을 수 있습니다.
- `ix_tlr_status`는 관리자의 "심사 대기 목록"에, `ix_tlr_member`는 회원별 신청 내역 조회에 씁니다.

---

## 8. otp_device — 가상 OTP 기기

이체할 때 쓰는 보안매체입니다. 자세한 동작은 [auth.md](../auth.md) 4장을 참고하세요.

| 컬럼 | 타입 / 제약 | 설명 |
|---|---|---|
| `otp_device_id` | BIGINT, PK | 기기 번호입니다. |
| `member_id` | BIGINT, FK → members | 소유한 회원입니다. |
| `serial_no` | VARCHAR(20), UNIQUE | OTP 일련번호입니다. 기기 화면에 표시되며, 등록하거나 분실 신고할 때 확인용으로 씁니다. |
| `seed_encrypted` | VARCHAR(255) | TOTP 시드(비밀키)입니다. 서버가 이 시드로 OTP 번호를 다시 계산해 비교해야 하므로, 되돌릴 수 없는 해시가 아니라 AES로 암호화해 저장합니다. 암호화 키는 환경변수로 관리합니다. |
| `status` | VARCHAR(20), 기본값 `ISSUED` | `ISSUED` 발급 → `ACTIVE` 사용중 → `SUSPENDED`(10회 오류로 정지), `LOST` 분실, `DISPOSED` 폐기 순으로 바뀝니다. |
| `fail_count` | INT | OTP 연속 오류 횟수입니다. 10회가 되면 SUSPENDED가 되고, 맞게 입력하면 0으로 되돌립니다. |
| `last_used_step` | BIGINT | 마지막으로 인증에 성공한 30초 구간 번호입니다(현재 시각(초) ÷ 30). 같은 구간의 OTP 번호를 다시 쓰는 것을 막습니다(재사용 공격 방지). |
| `issued_at` | DATETIME(6) | 발급 일시입니다. |
| `activated_at` | DATETIME(6) | 사용 등록을 마친 일시입니다. 등록할 때 OTP 번호를 연속 2회 입력해야 합니다. |
| `updated_at` | DATETIME(6) | 수정 일시입니다. |

- `ck_otp_device_activated`: ACTIVE나 SUSPENDED 상태이면 `activated_at`이 반드시 있어야 합니다.
- `ux_otp_device_one_usable`: 회원 한 명당 쓸 수 있는 상태(ISSUED, ACTIVE, SUSPENDED)의 OTP는 하나만 가질 수 있습니다. LOST나 DISPOSED 상태의 행은 이력으로 몇 개든 남길 수 있습니다.

---

## 아직 남은 작업

- `transfer_limit_request.account_id`에는 아직 FK가 없습니다. 팀원2가 account 테이블을 만든 뒤 SQL 파일 아래쪽에 주석으로 둔 `fk_tlr_account` ALTER 문을 실행해야 합니다.
