# 팀원2 작업 진행 기록 (계좌/이체/자산관리)

> Claude에게 이어서 작업을 요청할 때 이 파일을 먼저 읽게 하세요.
> 예: "docs/team2-progress.md 읽고 이어서 작업하자"

마지막 정리: 2026-10-14

---

## 1. 내 역할

- 팀원2. 담당: 계좌, 입출금, 이체, 거래내역, 자산관리 (기획서 [README.md](README.md) 12장)
- 현재 목표: **계좌 이체·출금 기능 구현**. 그 전에 테이블을 확정해야 함
- 처리 규칙의 기준은 기획서 11-1장 (비밀번호 검증 → 비관적 락 → 검증 → 잔액 갱신 → 원장 기록 → 커밋 후 이벤트)

## 2. 내 파일

| 파일 | 상태 |
|---|---|
| [schema-team2-mysql.sql](schema-team2-mysql.sql) | 수정 완료, **아직 커밋·DB 실행 안 함** |
| [schema-team2-dictionary.md](schema-team2-dictionary.md) | SQL과 같게 수정 완료 |
| [schema-team1-mysql.sql](schema-team1-mysql.sql) | members에 `daily_transfer_limit`, `once_transfer_limit` 내가 추가함 (커밋 안 함) |

담당 테이블 6개: `financial_institution_code`, `account`, `account_transfer`, `transaction_history`, `account_favorite`, `asset_report`

## 3. 확정한 결정 사항

1. **이체한도는 회원 단위**: 계좌가 아니라 `members.daily_transfer_limit`, `members.once_transfer_limit`에 둔다.
2. **당일 누적 이체 금액은 컬럼으로 두지 않음**: `today_transfer_amount`를 삭제했다. 그 회원의 모든 계좌에서 오늘 나간 `COMPLETED` 이체 합계를 `account_transfer`에서 계산한다.
3. **금액은 모두 BIGINT(원 단위)**: 팀1·팀3과 통일. 엔티티는 `Long`. 이자율은 팀3과 같은 `DECIMAL(7,4)`.
4. **계좌 개설일은 account에 둔다**: `signup_date`를 `opened_date DATE NOT NULL`로 바꿨다. 모든 계좌에 공통이다.
5. **예적금·대출 계약 컬럼 10개는 당분간 유지**: `expired_date`, `interest_rate`, `signup_period`, `monthly_payment`, `loan_amount`, `loan_balance`, `payment_day`, `repayment_method`, `current_payment_round`, `total_payment_rounds`. 팀3 테이블과 정보가 겹치지만 팀3과 협의한 뒤 정리한다. 대응표는 데이터 사전 1장에 있다.
6. **팀3 파일은 건드리지 않는다**: `PRODUCT_SUBSCRIPTION.SUBSCRIBED_AT`은 개설일과 같은 정보지만 그대로 둔다. `LOAN.STARTED_AT`은 대출 실행일이라 다른 정보다.

## 4. SQL에 반영한 변경 (원본 대비)

- `account`: `password_fail_count` 추가(5회면 LOCKED), `uk_account_number`, `fk_account_member`, 유형 CHECK(`CHECKING/DEPOSIT/SAVINGS/LOAN`), 상태 CHECK(`PENDING/ACTIVE/FROZEN/LOCKED/CLOSED/REJECTED`), `balance >= 0`
- `account_transfer`: `to_account_id`(당행이면 값 있음, 타행 NULL) 추가, `transfer_status`(`COMPLETED/SCHEDULED/FAILED/CANCELLED`) 추가, `UNIQUE(transaction_sqno)`, 자기 계좌 이체 금지 CHECK, 인덱스 `(account_id, transfer_at)`
- `transaction_history`: `account_transfer_id`, `counterpart_account_number`, `counterpart_name` 추가, `UNIQUE(account_id, transaction_sqno)`, 인덱스 `(account_id, transaction_at)`
- 생성 순서: `financial_institution_code` → `account` → `account_transfer` → `transaction_history` → `account_favorite` → `asset_report`
- 금액 컬럼 DECIMAL을 BIGINT로 변경, DROP 블록과 변경 이력 섹션 추가

## 5. 할 일 (순서대로)

- [ ] **팀1에 전달**
  - members 한도 컬럼 2개에 `NOT NULL DEFAULT` 추가 (예: 1일 5,000,000 / 1회 1,000,000) + `CHECK (once_transfer_limit <= daily_transfer_limit)`
  - `transfer_limit_request`가 아직 **계좌 기준**(`account_id NOT NULL`, 계좌당 대기 1건)이다. 한도가 회원 기준이 됐으므로 회원 기준으로 바꿔야 한다. 그러면 `fk_tlr_account` ALTER도 필요 없어진다
  - 공용 DB에 members가 이미 있으면 변경 이력에 `ALTER TABLE members ADD COLUMN ...` 기록
- [ ] **팀3과 협의**: 계약 컬럼 10개, `SUBSCRIBED_AT` 정리 방법. 대출 계좌를 따로 만들지도 정하기 (`LOAN`에는 대출 계좌를 가리키는 컬럼이 없음. `REPAYMENT_ACCOUNT_ID`는 상환금을 빼 갈 입출금 계좌)
- [ ] **팀3에 알리기**: [onbank_loan.sql](../project/onbank/src/main/resources/db/onbank_loan.sql)의 테이블 이름이 대문자(`LOAN`)라 리눅스(AWS) MySQL에서는 엔티티의 `@Table(name="loan")`과 맞지 않음
- [ ] **로컬 MySQL에서 실행 확인**: 팀1 DDL → 팀2 DDL 순서. ⚠️ `application-local.properties`는 **팀 공용 DB(cafe24)** 를 가리키므로 테스트용으로 쓰지 말 것
- [ ] 시드 데이터 `data-team2-mysql.sql` 작성: 금융기관 코드(우리 은행 OnBank 포함) + 테스트 회원 계좌 2~3개(비밀번호는 BCrypt)
- [ ] PR 리뷰를 받은 뒤 공용 DB에 실행
- [ ] **엔티티 작성**: `Account` → `AccountTransfer` → `TransactionHistory`. `ddl-auto=validate`가 통과하는지 확인
- [ ] **출금·이체 서비스 구현** (아래 6장)
- [ ] 동시 이체 테스트 작성

## 6. 이체·출금 구현 설계 (합의된 방향)

패키지: `org.doit.onbank.account` 아래에 `entity / repository / service / controller / dto / exception`

- **잔액은 `AccountService.debit()`, `credit()` 두 메서드에서만 바꾼다.** 둘 다 잔액 갱신과 `transaction_history` INSERT를 같이 한다. 팀3의 대출금 입금도 이 메서드를 재사용한다.
- **계좌 비밀번호 검증은 별도 Bean(`AccountPasswordVerifier`)에서 `REQUIRES_NEW`로** 한다. 같은 클래스 안에서 자기 메서드를 호출하면 프록시를 거치지 않아 `REQUIRES_NEW`가 무시된다.
- **이체 순서**
  1. 입력 검증 (금액 > 0, 출금계좌 ≠ 입금계좌, 출금계좌가 내 계좌인지, 1회 한도)
  2. 계좌 비밀번호 검증 (REQUIRES_NEW)
  3. OTP 검증: `otpService.verify(memberId, otpCode)` (팀1 제공 예정. 나오기 전까지는 stub으로)
  4. **`members` 행을 먼저 `FOR UPDATE`로 잠금** (회원 단위 1일 한도의 동시성 보장)
  5. 계좌 2개를 **ID 오름차순으로** `PESSIMISTIC_WRITE` 잠금 (데드락 방지)
  6. 상태 검사: 출금은 ACTIVE만 허용, 입금은 ACTIVE/FROZEN/LOCKED 허용
  7. 잔액 검사, 1일 한도 검사 (오늘 COMPLETED 이체 합계 + 이번 금액 ≤ 1일 한도)
  8. debit/credit → `transaction_history` 2행 + `account_transfer` 1행
  9. 커밋 후 이벤트 발행 (`@TransactionalEventListener(AFTER_COMMIT)`, 팀4 알림용)
- **출금**은 ATM 출금 mock으로 처리한다. 계좌 비밀번호만 요구하고(OTP 없음) 계좌 1개만 잠근다.
- **엔티티 주의**: `transaction_at`, `transfer_at`은 DB DEFAULT가 동작하지 않으므로(Hibernate가 NULL을 넣음) 엔티티에서 직접 값을 넣는다. 로그인 회원은 `@AuthenticationPrincipal LoginMember me`로 받는다.
- **화면 흐름**: 입력 → 확인(fetch로 계좌주 이름 확인) → 비밀번호와 OTP 입력 → 결과/영수증. 버튼 중복 클릭 방지와 PRG 패턴 적용
- **타행 이체**는 심화 기능. 핵심 기능은 당행 이체만 구현한다.

## 7. 팀원과 합의가 필요한 것

- 팀1: OTP를 등록하지 않은 회원의 이체 정책 (전부 막을지, 소액만 허용할지)
- 팀3: 대출금 입금 메서드 시그니처. 예: `accountService.deposit(accountId, amount, "대출금 입금")`
- 팀4: 이체 완료 이벤트 클래스 이름과 필드
