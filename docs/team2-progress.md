# 팀원2 작업 진행 기록 (계좌/이체/자산관리)

> Claude에게 이어서 작업을 요청할 때 이 파일을 먼저 읽게 하세요.
> 예: "docs/team2-progress.md 읽고 이어서 작업하자"

마지막 정리: 2026-10-20

---

## 1. 내 역할

- 팀원2. 담당: 계좌, 입출금, 이체, 거래내역, 자산관리 (기획서 [README.md](README.md) 12장)
- 현재 상태 (10-20): 입출금·당행 즉시이체·계좌 개설·즐겨찾기·이체 OTP·자산 리포트 완료(main 머지). **10-20 예약이체(5.4) 완료 — 커밋했지만 푸시 전, 브라우저 확인 전.** 다음: **정기이체(5.5)** (handoff 2-3). 후보: 사기계좌 확인 (팀4 답 대기). 6.1 거래내역 페이징·필터는 사용자가 건너뛰기로 함
- 세션 인수인계: [team2-handoff.md](team2-handoff.md) (최근 작업 상태, 다음에 할 일, 주의할 점). **10-16 이후 결정(계좌 유형 값, 계좌번호 규칙, 한도 기본값, 즐겨찾기 등)은 handoff 1-1이 기준**
- 처리 규칙의 기준은 기획서 11-1장 (비밀번호 검증 → 비관적 락 → 검증 → 잔액 갱신 → 원장 기록 → 커밋 후 이벤트)

## 2. 내 파일

| 파일 | 상태 |
|---|---|
| [03_account_ddl.sql](../project/onbank/src/main/resources/db/03_account_ddl.sql) | 팀2 DDL (2026-10-16에 docs/schema-team2-mysql.sql 에서 옮김). 공용 DB(cafe24)에 테이블 생성 완료 |
| [schema-team2-dictionary.md](schema-team2-dictionary.md) | SQL과 같게 정리 완료 |
| 엔티티 6개 (`org.doit.onbank.account.entity`) | 커밋·main 머지 완료. 공용 DB에서 `ddl-auto=validate` 통과 확인 |
| [schema-team1-mysql.sql](schema-team1-mysql.sql) | members에 `daily_transfer_limit`, `once_transfer_limit` 추가. 공용 DB에 ALTER 완료, 팀1과 협의 완료 |
| [data-team2-mysql.sql](data-team2-mysql.sql) | 화면 확인용 테스트 계좌 3개 (`TEST-0001`~`0003`, 계좌 비밀번호 1234). 삭제 SQL 포함. 아이디 줄은 PC마다 바꿔 쓰고 커밋하지 않음 |
| 입출금 코드·화면 (`org.doit.onbank.account` 아래 `entity / repository / service / controller / dto`, `templates/account/list, detail, deposit, withdraw.html`, `static/js/account.js`) | 10-15 작성, main 머지됨, 브라우저 확인 완료 |
| 이체 코드·화면 (`AccountTransferService`, `TransferPageController`, `transfer.html`, `transfer-result.html`, `static/js/transfer.js`) | 10-16 작성, main 머지됨, 브라우저 확인 완료 |
| 계좌 개설·상태 변경 (`AccountOpenService`, `AccountManageService`, `AccountNumberGenerator`, `open.html`, `open-result.html`) | 10-16 작성, main 머지됨 (PR #33), 브라우저 확인 전 |
| 즐겨찾기 (`AccountFavoriteService`, `FavoritePageController`, `favorites.html`, `favorite-edit.html`, `favorite-toggle.html`) | 10-16 작성, 10-17 내 계좌 / 상대계좌 구분 추가. main 머지됨 (PR #33, #36), **브라우저 확인 완료 (10-19)** |
| 이체 OTP 연결 (`AccountTransferService`, `OtpRequiredException`, `TransferForm`, `TransferPageController`, `transfer.html`, `transfer.js`) | 10-19 작성, main 머지됨 (#53). OTP 키 입력함 (10-20). 브라우저 확인 전 |
| 자산 리포트 (`AssetReportService`, `AssetReportRepository`, `AssetReportPageController`, `AssetReportView`, `assets.html`, `asset.css`, `asset.js`) | 10-19 작성, 10-20 대출(`loan_account`)·지난달 저장값 유지로 수정. main 머지됨 (#53, #60). 브라우저 확인 전 |
| enum 전환·하이픈·OTP 복귀·입력값 복원 (`AccountStatus`, `AccountType`, `AccountNumberGenerator.normalize`, `account.js`, `transfer.js`, `otp.html`) | 10-20 작성, main 머지됨 (#60). 브라우저 확인 전 |
| **예약이체** (`ScheduledTransfer`, `ScheduleType`, `ScheduledTransferStatus`, `ScheduledTransferRepository`, `ScheduledTransferService`, `ScheduledTransferRunner`, `ScheduledTransferScheduler`, `ScheduledTransferPageController`, `scheduled-transfers.html`) | 10-20 작성, 커밋 `9c7aead` (**푸시 전**). 브라우저 확인 전 |
| ~~team2-account-number-rule.md~~ | 10-20 사용자가 삭제 |
| 테스트 | **전체 328개 통과** (10-20, main 합친 뒤. `OnbankApplicationTests` 포함) |

담당 테이블 7개: `financial_institution_code`, `account`, `account_transfer`, `transaction_history`, `account_favorite`, `asset_report`, **`scheduled_transfer`(10-20)**

엔티티 7개: `FinancialInstitution`, `Account`, `AccountTransfer`, `TransactionHistory`, `AccountFavorite`, `AssetReport`, **`ScheduledTransfer`(10-20)**

## 3. 확정한 결정 사항

1. **테이블 기준은 팀원2 데이터 사전 원본**: 원본에 없는 컬럼은 추가하지 않는다. 유일한 예외는 `account.password_fail_count`(`INT NOT NULL DEFAULT 0`).
2. **다른 팀 파일은 수정하지 않는다** (팀1/3/4).
3. **이체한도는 회원 단위**: `members.daily_transfer_limit`, `members.once_transfer_limit`에 둔다.
4. **당일 누적 이체 금액은 `account.today_transfer_amount`에 둔다**: 한도 비교는 계좌별로 한다. 그 계좌의 `today_transfer_amount`(누적)에 이번 금액을 더해 `members.daily_transfer_limit`(개인 한도 명시)를 넘는지 확인한다. 날짜가 바뀌면 `Account.touchTransaction(now)`에서 0으로 초기화한다.
   - `members` 한도 컬럼: 개인의 한도를 **명시**하는 값
   - `account.today_transfer_amount`: 그 계좌에서 오늘 이체한 금액이 **누적**되는 값
5. **금액은 모두 BIGINT(원 단위)** ↔ 엔티티 `Long`. 이자율은 `DECIMAL(7,4)` ↔ `BigDecimal`.
6. **가입일은 `signup_date` 그대로** 사용한다.
7. **예적금·대출 계약 컬럼 10개는 삭제하지 않고 팀2에서 독립적으로 사용**: `expired_date`, `interest_rate`, `signup_period`, `monthly_payment`, `loan_amount`, `loan_balance`, `payment_day`, `repayment_method`, `current_payment_round`, `total_payment_rounds`. 팀3과 겹치는 대응표는 데이터 사전 1장에 있다.
8. **유형·상태 컬럼은 `String`**: 확정된 값은 상수 클래스(`TransactionType`, `TransactionStatus`, `TransferType`)로 참조해 오타를 막는다. **10-20 사용자 결정으로 `AccountStatus`·`AccountType`은 enum으로 바꿨다** (DB 값 그대로, 팀3 비교 코드도 함께 수정). `account_type`, `transfer_type` 값은 10-16 확정 (handoff 1-1).
9. **다른 테이블 참조는 연관관계 없이 `Long` ID 필드**로 둔다.
10. **제약 조건은 최소한만**: 모든 테이블 PK, `account.member_id NOT NULL`, `account.password_fail_count NOT NULL DEFAULT 0`, `account.account_number UNIQUE`. FK, CHECK, 인덱스는 두지 않는다.
11. **DB 운영**: `ddl-auto=validate`라 서버는 테이블을 검사만 한다. 테이블 생성·변경은 워크벤치에서 SQL을 직접 실행한다. 컬럼을 바꾸면 SQL 변경 이력에 ALTER를 기록하고 엔티티도 함께 바꾼다.
12. **예약이체·정기이체는 팀2 담당** (기획서 12장 원래 배정. 한때 팀3 담당으로 바꿨다가 10-20 사용자 결정으로 되돌림): 화면·테이블·스케줄러·실행 모두 팀2가 구현한다.
13. **계좌 상태 `account_status` 4개** (2026-10-15 확정)

    | 값 | 뜻 | 입금 | 출금 |
    |---|---|---|---|
    | `ACTIVE` | 정상 | ○ | ○ (비밀번호 오류 5회면 ✕) |
    | `PENDING` | 개설대기 (본인확인 심사 중) | ✕ | ✕ |
    | `FROZEN` | 동결 (관리자 정지, 해제 가능) | ○ | ✕ |
    | `CLOSED` | 해지 (개설 반려 포함, 되돌리지 않음) | ✕ | ✕ |

    - 기획서의 `LOCKED`는 상태로 두지 않고 `password_fail_count >= 5`로 판단한다. 관리자 잠금 해제는 `resetPasswordFailCount()`
    - 기획서의 `REJECTED`는 `CLOSED`로 대신한다
    - 해지와 동결을 나눈 이유: 해지는 되돌릴 수 없고 잔액이 0이며 입금도 막는다. 동결은 해제할 수 있고 잔액이 남아 있으며 입금은 허용한다
    - 개설 승인(`PENDING → ACTIVE`), 동결·해제, 해지 메서드는 팀2가 만들고 관리자 쪽(팀1 심사, 팀4 화면)이 호출한다
14. **거래 유형 `transaction_type`**: `DEPOSIT` 입금 / `WITHDRAWAL` 출금 / `TRANSFER_OUT` 이체 보냄 / `TRANSFER_IN` 이체 받음
15. **거래 상태 `transaction_status`**: 지금은 `COMPLETED` 하나. 실패한 거래는 롤백되어 행이 남지 않는다. 거래 취소 기능이 생기면 값을 추가한다
16. **거래 번호 `transaction_sqno`**: `yyyyMMddHHmmss-난수6자리` (예: `20261015143012-482915`). 두 번 클릭으로 인한 중복 처리는 이 번호가 아니라 버튼 중복 클릭 방지 + PRG 패턴으로 막는다
17. **현금 출금은 이체 한도와 별개**: `today_transfer_amount`에는 이체(`TRANSFER_OUT`)만 누적한다. 입금·출금은 `touchTransaction`으로 날짜만 갱신한다
18. **(10-19) 이체 인증**: 10만 원 이하 계좌 비밀번호만 / 초과 계좌 비밀번호 + OTP(팀1). 본인 계좌끼리는 OTP 생략. PIN은 만들지 않고 계좌 비밀번호가 간편 인증 역할. OTP 미등록 회원도 10만 원 이하는 이체 가능. 판단은 모두 서버에서 (handoff 1-1)
19. **(10-19) 자산 리포트**: 조회할 때 계산 + 최근 6개월 `asset_report` 저장(회원·기준년월당 1행, `account_id`에 member_id). 공과금·관리비는 거래 메모 키워드, 투자는 출처 없음. 대출은 팀3 승인 건 기준 → **10-20 실행된 대출(`loan_account`) 기준으로 수정** (handoff 2-000)
20. **(10-20) 예약·정기이체 설계**: 한 테이블 `scheduled_transfer`(유형 `SCHEDULED`/`RECURRING`). 등록할 때 비밀번호(·OTP) 확인, 실행은 스케줄러가 인증 없이 `AccountTransferService.transferPreAuthorized()` 호출(잔액·한도·상태는 실행 시점에 다시 검사). 실행 일시는 날짜+시각(`next_execute_at`). 같은 건 중복 실행은 행 잠금(`findByIdForUpdate`) 후 상태를 다시 보고 막는다. 진행: 1단계 테이블·엔티티, 2단계 `transfer()` 분리, 3단계 등록(이체 화면 [예약이체일지정])·목록·취소(`/mybank/transfer/scheduled`), 4단계 스케줄러(`ScheduledTransferScheduler` 매분 0초, 끄기 `onbank.scheduled-transfer.enabled=false`)·[지금 실행](시연용) 완료 / 5단계 정기이체 남음. 실행 성공은 이체+완료 표시를 한 트랜잭션으로, 실패는 롤백 후 새 트랜잭션에서 기록 (`ScheduledTransferRunner`)

## 4. Account 엔티티 메서드

| 메서드 | 동작 |
|---|---|
| `touchTransaction(now)` | 모든 거래에서 호출. 최종 거래일과 날짜가 다르면 `today_transfer_amount`를 0으로 초기화한 뒤 `last_transaction_at` 갱신. 입금만 있던 날에도 날짜가 갱신되므로 어제 이체액이 합산되지 않음 |
| `increasePasswordFailCount()` | 비밀번호 오류 시 1 증가 (최대 5) |
| `resetPasswordFailCount()` | 비밀번호 성공 시 0으로 초기화 |
| `isPasswordFailLimitReached()` | 5회 도달 여부 |
| `deposit(amount, now)` | 입금. 금액 > 0, `ACTIVE`·`FROZEN`만 허용. `touchTransaction` 후 잔액 증가 |
| `withdraw(amount, now)` | 출금. 금액 > 0, `ACTIVE`만, 비밀번호 오류 5회면 불가, 잔액 부족이면 불가. `touchTransaction` 후 잔액 감소. `today_transfer_amount`에는 누적하지 않음 |
| `canDeposit()` / `canWithdraw()` | 상태로 입금·출금 가능 여부 확인 |

- 5회 도달 시 계좌 상태는 바꾸지 않는다. `withdraw()`가 오류 횟수로 직접 막는다.
- 규칙에 어긋나면 금액 오류는 `IllegalArgumentException`, 상태·잔액 오류는 `IllegalStateException`을 던진다.

## 5. 할 일 (순서대로)

- [ ] **팀1에 전달**
  - members 한도 컬럼 2개에 `NOT NULL DEFAULT` 추가 (예: 1일 5,000,000 / 1회 1,000,000) + `CHECK (once_transfer_limit <= daily_transfer_limit)`
  - `transfer_limit_request`가 아직 **계좌 기준**(`account_id NOT NULL`, 계좌당 대기 1건)이다. 한도가 회원 기준이 됐으므로 회원 기준으로 바꿔야 한다. 그러면 `fk_tlr_account` ALTER도 필요 없어진다
  - 공용 DB에 members가 이미 있으면 변경 이력에 `ALTER TABLE members ADD COLUMN ...` 기록

**계좌 입출금 기능 (10-15 완료)**
- [x] 0단계: 상태·유형 값 확정 (3장 13~17번)
- [x] 1단계: `Account.deposit()`, `withdraw()` 추가, 상수 클래스 3개 추가
- [x] 2단계: Repository 작성: `AccountRepository`(비관적 락 조회 포함), `TransactionHistoryRepository`
- [x] 3단계: `AccountService.deposit()`, `withdraw()` + `AccountPasswordVerifier`(REQUIRES_NEW) + `TransactionSqnoGenerator`
- [x] 4단계-1: 단위 테스트 (DB 없이 실행, 29개 통과). `AccountTests`, `AccountServiceTests`, `AccountPasswordVerifierTests`, `TransactionSqnoGeneratorTests`
- [ ] 4단계-2: DB 통합 테스트. 단위 테스트로 확인할 수 없는 실제 잠금(FOR UPDATE), REQUIRES_NEW 커밋, SQL 확인. **방법 결정 필요** (아래 참고)
  - 출금은 롤백 방식(`@Transactional` 테스트)으로 테스트할 수 없다. 비밀번호 확인(REQUIRES_NEW)이 별도 DB 연결을 쓰는데, 커밋되지 않은 테스트 계좌 행의 잠금을 기다리다 실패한다 (MySQL 기본 50초)
  - ① 입금만 롤백 테스트 + 출금은 화면 확인으로 대신 (제안) / ② 출금까지 커밋 테스트 후 코드에서 삭제
- [x] 5단계: Controller·화면 코드 작성. `/mybank/accounts` 목록, `/{id}` 거래내역, `/{id}/deposit`, `/{id}/withdraw`. 화면 테스트(`AccountPageControllerTests`, DB 없이 15개) 통과
- [x] 서버 실행 확인 (2026-10-15): `contextLoads` 통과. 팀3 `loan_repayment_schedule` 문제는 해결됨
- [x] 5단계 확인: 브라우저에서 실제 화면 확인 (테스트 계좌는 [data-team2-mysql.sql](data-team2-mysql.sql), 계좌 비밀번호 1234)
- 예전 목업 주소 `AccountController`(`/account/list`, `/account/transfer`)는 새 화면으로 redirect. `my-accounts.html`은 삭제하고 사용자 원본 `list.html` 구성을 사용

**이체·계좌 개설·즐겨찾기 (10-16 ~ 10-17)**
- [x] 1일 한도 값 읽기: `AccountRepository.findTransferLimit()`로 `members` 두 컬럼을 SQL로 직접 조회. NULL이면 기본 1일 5,000,000 / 1회 1,000,000
- [x] `account_type`, `transfer_type` 값 정하기 (handoff 1-1)
- [x] 이체 Service (아래 6장, OTP는 건너뜀), `AccountTransferRepository`
- [x] 이체 Controller·화면 (목업 `transfer.html` 구성 유지 + 확인 단계). 브라우저 확인 완료
- [x] 계좌 개설 (입출금, 본인확인 없이 즉시 `ACTIVE`), 상태 변경 메서드 (`AccountManageService`). 브라우저 확인 전
- [x] 즐겨찾기 (관리 화면, 전계좌조회 ☆/★, 이체 화면 선택 창, 이체 완료 [즐겨찾기 추가]). 브라우저 확인 전
- [x] (10-17) 이체 화면 [내 계좌 즐겨찾기](출금계좌) / [상대계좌 즐겨찾기](입금계좌) 구분. 브라우저 확인 전

**이체 OTP·자산 리포트 (10-19)**
- [x] 이체 OTP 연결 (팀1 `OtpService.verify`, 10만 원 초과 타인 이체). 화면: 한글 금액, 금액 즉시 안내, [확인] 비활성, OTP 입력칸
- [x] 자산 리포트 `/mybank/assets` (요약 카드, 자산 구성 도넛, 6개월 추이, 항목별 현황, 대출·예적금 진행률)
- [x] 즐겨찾기 브라우저 확인 (사용자)
- [x] ~~팀3 공용 DB 타입 문제~~ 10-20 해결 확인 (서버 시작 OK)
- [x] 커밋·PR (#53)
- [ ] 브라우저 확인: OTP 이체, 자산 리포트, 계좌 개설

**10-20**
- [x] 자산 리포트 대출을 `loan_account` 기준으로, 상환액은 출금에 포함, 지난달은 저장값 유지
- [x] `AccountStatus`·`AccountType` enum 전환 (팀3 문자열 비교 포함)
- [x] 계좌번호 하이픈 자동 입력, OTP 화면 [이체 화면으로 돌아가기], OTP 등록 후 이체 입력값 복원
- [x] 예약이체 1단계 테이블·엔티티 (공용 DB `scheduled_transfer` 사용자 생성)
- [x] 예약이체 2단계 `transfer()` → `authorize()` / `execute()` 분리 + `transferPreAuthorized()`
- [x] 예약이체 3단계 등록(이체 화면 [예약이체일지정])·조회·취소 화면
- [x] 예약이체 4단계 스케줄러(매분 0초)·실패 기록·시연용 [지금 실행]
- [x] 전계좌조회 상단 큰 바로가기 버튼
- [x] `origin/main` merge (팀1 `LOCKED` 반영) + 팀3 새 코드 문자열 비교 2곳 enum으로 수정
- [ ] 브라우저 확인: 예약이체 (handoff 2-0000-2)
- [ ] 남은 변경 커밋·푸시·PR (사용자 지시 후)
- [ ] **정기이체 5단계 (다음 세션)**: 정할 것 4가지는 handoff 2-3

**그 다음**
- [ ] 사기계좌 확인 (팀4 `fds_transfer_block` 협의 후)
- [ ] 다건이체 (사용자 검토 중)
- [ ] ~~6.1 거래내역 페이징·필터~~ 10-19 사용자가 건너뛰기로 함
- [ ] 시드 데이터 `data-team2-mysql.sql`에 금융기관 코드(우리 은행 OnBank 포함) 추가. 테스트 계좌 3개는 작성 완료
- [ ] 동시 이체 테스트 작성
- [ ] 이체 완료 이벤트 (팀4 알림용, 아래 6장 8번)

**구현할 때 지킬 것**
- 계좌를 개설할 때 `balance`, `today_transfer_amount`를 0으로 넣는다 (NULL이면 계산 오류)
- `transaction_at`, `transfer_at`, `favorite_created_at`은 코드에서 직접 값을 넣는다 (DB DEFAULT 없음)
- `transaction_sqno` 중복 방지는 코드에서 처리한다 (UNIQUE 없음)

## 6. 이체·출금 구현 설계 (합의된 방향)

패키지: `org.doit.onbank.account` 아래에 `entity / repository / service / controller / dto / exception`

- **잔액은 `AccountService`의 메서드에서만 바꾼다.** 지금은 `deposit()`, `withdraw()`가 구현되어 있고, 잔액 갱신(엔티티 메서드 안에서 `touchTransaction(now)` 호출)과 `transaction_history` INSERT를 같은 트랜잭션에서 한다. 이체도 같은 엔티티 메서드를 재사용하고 거래 유형만 `TRANSFER_OUT`/`TRANSFER_IN`으로 기록한다. 팀3의 대출금 입금도 `deposit()`을 재사용한다.
- **계좌 비밀번호 검증은 별도 Bean(`AccountPasswordVerifier`)에서 `REQUIRES_NEW`로** 한다. 이체가 롤백돼도 `password_fail_count`가 남게 하기 위해서다. 같은 클래스 안에서 자기 메서드를 호출하면 프록시를 거치지 않아 `REQUIRES_NEW`가 무시된다.
- **이체 순서**
  1. 입력 검증 (금액 > 0, 출금계좌 ≠ 입금계좌, 출금계좌가 내 계좌인지, 1회 한도)
  2. 계좌 비밀번호 검증 (REQUIRES_NEW)
  3. OTP 검증: `otpService.verify(memberId, otpCode)` (10-19 연결. 10만 원 초과 타인 이체만, 계좌 잠금 전)
  4. 계좌 2개를 **ID 오름차순으로** `PESSIMISTIC_WRITE` 잠금 (데드락 방지)
  5. 상태 검사: 출금 계좌는 `ACTIVE`, 입금 계좌는 `ACTIVE`·`FROZEN` (`Account.withdraw()`, `deposit()` 안에서 확인)
  6. 잔액 검사, 1일 한도 검사: `touchTransaction(now)` 후 출금 계좌의 `today_transfer_amount + 이번 금액 ≤ members.daily_transfer_limit`
  7. debit/credit → `transaction_history` 2행 + `account_transfer` 1행, 출금 계좌 `today_transfer_amount` 누적
  8. 커밋 후 이벤트 발행 (`@TransactionalEventListener(AFTER_COMMIT)`, 팀4 알림용)
- **(10-20) 코드 구조**: 1~3은 `authorize()`, 4~7은 `execute()`(private). 즉시이체 `transfer()` = authorize + execute. 예약 등록은 authorize만 하고 저장, 예약 실행은 `transferPreAuthorized()` = 1의 입력 검증 + execute (인증 없음, 잔액·한도·상태는 실행 시점에 다시 검사)
- **출금**은 ATM 출금 mock으로 처리한다. 계좌 비밀번호만 요구하고(OTP 없음) 계좌 1개만 잠근다.
- **로그인 회원**은 `@AuthenticationPrincipal LoginMember me`로 받는다.
- **화면 흐름**: 입력 → 확인(fetch로 계좌주 이름 확인) → 비밀번호와 OTP 입력 → 결과/영수증. 버튼 중복 클릭 방지와 PRG 패턴 적용
- **타행 이체**는 심화 기능. 핵심 기능은 당행 이체만 구현한다.

## 7. 팀원과 합의가 필요한 것

- ~~팀1: OTP를 등록하지 않은 회원의 이체 정책~~ → 10-19 사용자 결정: 10만 원 이하는 허용, 초과는 OTP 등록 안내 (팀원 공유 예정)
- ~~팀1: OTP 암호화 키 공유~~ → 10-20 키 받아 입력함
- ~~팀3: 공용 DB `loan_repayment_schedule` 금액 컬럼 타입 불일치~~ → 10-20 해결 확인
- 팀4: `fds_transfer_block` 사기계좌 검사를 이체에서 호출할지
- ~~팀3: 대출금 입금 메서드 시그니처~~ → 팀3가 `AccountTransferService.disburseLoan()`으로 구현
- 팀3 (10-20): enum 전환 알림, merge 후 고친 문자열 비교 2곳, 공용 `SchedulingConfig`를 팀2도 씀, "정기이체" 화면 이름 겹침 (handoff 10장)
- 팀1 (10-20): `security/otp.html`에 [이체 화면으로 돌아가기] 추가 알림
- ~~팀3: 예약이체·정기이체에서 호출할 이체 메서드 시그니처~~ → 10-20 팀2가 직접 구현하기로 해 협의 불필요
- 팀4: 동결·해제 화면에서 호출할 메서드 (`FROZEN` 상태 사용)
- 팀4: 이체 완료 이벤트 클래스 이름과 필드
