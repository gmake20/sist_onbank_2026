# ERD 이미지 vs MySQL 스키마 비교 결과

> 확인일: 2026-10-13
> 비교 대상: [bank_table_team1.png](bank_table_team1.png) ↔ [schema-team1-mysql.sql](../schema-team1-mysql.sql)

**결론**: 9개 테이블과 컬럼 구성은 모두 일치합니다. 다만 ERD에 **실수 2가지**가 있고, **DB 형식이 Oracle 기준**이라 SQL 파일과 다릅니다.

---

## 1. 일치하는 부분 ✅

| 테이블 | 컬럼 수 (SQL 기준) | 컬럼 구성 |
|---|---|---|
| MEMBER | 16 | ✅ 일치 (불필요한 `Key` 행 제외, 2-1 참고) |
| BRANCH | 12 | ✅ 일치 |
| BRANCH_SERVICE | 2 | ✅ 일치 (복합 PK + 식별 관계도 올바름) |
| EMPLOYEE | 17 | ✅ 일치 |
| IDENTITY_VERIFICATION | 11 | ✅ 일치 (`Key` 행 제외) |
| LOGIN_LOG | 8 | ✅ 일치 (`Key` 행 제외) |
| EMPLOYEE_LOGIN_LOG | 8 | ✅ 일치 |
| TRANSFER_LIMIT_REQUEST | 12 | ✅ 일치 (`Key` 행 제외). `ACCOUNT_ID`에 FK 선이 없는 것도 SQL과 같음 (account 테이블 생성 후 연결) |
| otp_device | 10 | ✅ 일치 (`Key` 행 제외, `MEMBER_ID` 문제는 2-2 참고) |

관계선 중 다음은 SQL의 FK와 같습니다.
- MEMBER → LOGIN_LOG / IDENTITY_VERIFICATION / TRANSFER_LIMIT_REQUEST / otp_device
- BRANCH → EMPLOYEE / BRANCH_SERVICE
- EMPLOYEE → IDENTITY_VERIFICATION / TRANSFER_LIMIT_REQUEST / EMPLOYEE_LOGIN_LOG

---

## 2. 고쳐야 할 실수 ⚠️

### 2-1. MEMBER에 잘못된 PK 컬럼 `Key`가 있음 (가장 중요)

- MEMBER의 두 번째 행 `Key`(타입 비어 있음)에 **노란 열쇠(PK) 아이콘**이 붙어 있습니다. 그래서 MEMBER의 PK가 `(MEMBER_ID, Key)` 두 컬럼짜리 복합키가 되었습니다.
- 그 결과 MEMBER를 참조하는 **모든 자식 테이블에 `Key`라는 FK 컬럼이 자동으로 따라 생겼습니다.**
  - LOGIN_LOG, IDENTITY_VERIFICATION, TRANSFER_LIMIT_REQUEST, otp_device의 `Key` 행
- **해결**: MEMBER에서 `Key` 행만 삭제합니다. 대부분의 ERD 도구에서는 자식 테이블의 `Key`도 함께 사라집니다. 남아 있으면 직접 지웁니다.

### 2-2. otp_device의 `MEMBER_ID`가 PK로 잡혀 있음 (식별 관계로 잘못 연결)

- `MEMBER_ID` 옆 아이콘이 BRANCH_SERVICE의 `BRANCH_ID`와 같은 **청록색(PK + FK)** 이고, MEMBER → otp_device 선도 **실선(식별 관계)** 입니다.
- SQL에서 `member_id`는 **PK가 아닌 일반 FK**입니다. 분실·재발급 이력 때문에 회원 1명이 OTP 여러 개를 가질 수 있기 때문입니다.
- **해결**: 관계를 **비식별 관계(점선)** 로 바꿉니다.
  - BRANCH → BRANCH_SERVICE의 실선은 SQL에서도 복합 PK `(branch_id, service_code)`이므로 맞습니다.

---

## 3. DB 형식 차이 (Oracle 기준 ERD vs MySQL SQL)

ERD는 처음의 Oracle 파일로 그린 것으로 보입니다. otp_device만 MySQL 파일에서 추가해서, 혼자 소문자 + MySQL 타입이라 섞여 있습니다.

| 항목 | ERD (otp_device 제외) | schema-team1-mysql.sql |
|---|---|---|
| 테이블·컬럼 이름 | 대문자 (`MEMBER`, `LOGIN_ID`) | 소문자 (`members`, `login_id`) |
| 회원 테이블 이름 | `MEMBER` | `members` (MySQL 8 예약어 회피) |
| `NUMBER(19)` | ← | `BIGINT` |
| `NUMBER(15)` (한도) | ← | `BIGINT` |
| `NUMBER(2)` (실패 횟수) | ← | `INT` |
| `NUMBER(10,7)` (위도·경도) | ← | `DOUBLE` |
| `VARCHAR2(30 CHAR)` | ← | `VARCHAR(30)` |
| `TIMESTAMP` | ← | `DATETIME(6)` |
| `CHAR(1)`, `DATE` | 같음 | 같음 |

- otp_device 안에서도 자동 생성된 `MEMBER_ID`만 대문자 + `NUMBER(19)`라서 다른 컬럼(`BIGINT`)과 맞지 않습니다.
- **해결**: ERD 도구의 DB 종류를 **MySQL**로 바꾸고 이름을 소문자로 맞춥니다. 팀 규칙(README 13장 8번)상 실제 정의는 SQL 파일이 기준이므로, 발표용 ERD도 같은 형식이어야 혼란이 없습니다.

---

## 4. 이미지로는 확인할 수 없는 부분

### NULL 허용 여부, UNIQUE, CHECK 제약
이미지에 나타나지 않으므로 ERD 도구에서 직접 확인합니다. 특히 아래는 SQL에서 **일부러 NULL을 허용**한 컬럼입니다.

| 컬럼 | NULL이 되는 경우 |
|---|---|
| `members.email`, `members.phone` | 탈퇴 시 |
| `login_log.member_id` | 없는 아이디로 로그인 시도 |
| `employee_login_log.employee_id` | 없는 사번으로 로그인 시도 |
| `identity_verification.reviewed_employee_id`, `transfer_limit_request.reviewed_employee_id` | 심사 전 (이체한도 감액은 항상 NULL) |

### 선 끝 모양(카디널리티)
이미지 해상도로는 정확히 판별하기 어렵습니다. 위 NULL 허용 FK는 **부모 쪽이 "0 또는 1"(선택)** 이어야 합니다. 예: 로그인 이력은 회원이 없을 수도 있으므로 MEMBER 쪽 끝에 동그라미(o)가 있어야 맞습니다.

---

## 5. 수정 체크리스트

- [ ] MEMBER의 `Key` 행 삭제 → 자식 테이블(LOGIN_LOG, IDENTITY_VERIFICATION, TRANSFER_LIMIT_REQUEST, otp_device)의 `Key` 행도 사라졌는지 확인
- [ ] MEMBER → otp_device 관계를 비식별(점선)로 변경, `member_id`의 PK 표시 해제
- [ ] ERD 도구의 DB 종류를 MySQL로 변경, 타입을 3장 표대로 맞춤
- [ ] 테이블·컬럼 이름을 소문자로 변경 (`MEMBER` → `members`)
- [ ] 4장의 NULL 허용 컬럼과 관계선 끝 모양 확인
- [ ] 수정한 ERD 이미지를 다시 내보내 `bank_table_team1.png` 교체
