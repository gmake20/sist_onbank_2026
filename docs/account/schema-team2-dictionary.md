# 팀원2 테이블 데이터 사전

[03_account_ddl.sql](../../project/onbank/src/main/resources/db/03_account_ddl.sql)에 정의된 테이블 7개의 컬럼 설명입니다. (10-20 `scheduled_transfer` 추가)
기준은 팀원2 데이터 사전 원본이며, 원본에 없는 컬럼은 `account.password_fail_count` 하나만 추가했습니다. SQL 파일을 수정하면 이 문서와 엔티티(`org.doit.onbank.account.entity`)도 함께 고칩니다.

- 금액은 모두 `BIGINT`(원 단위)이며 엔티티에서는 `Long`으로 매핑합니다. 이자율은 `DECIMAL(7,4)` ↔ `BigDecimal`입니다.
- 이체한도는 쓰임이 다른 두 컬럼으로 나뉩니다.
  - **명시**: `members.daily_transfer_limit`, `members.once_transfer_limit`(팀원1 테이블)는 개인의 한도 금액을 정해 두는 값입니다.
  - **누적**: `account.today_transfer_amount`는 그 계좌에서 오늘 이체한 금액이 쌓이는 값이며, 날짜가 바뀌면 0으로 초기화합니다.
  - 1일 한도 비교는 계좌별로 합니다: `today_transfer_amount + 이번 이체 금액 ≤ daily_transfer_limit`
- 유형·상태 컬럼은 엔티티에서 `String`으로 두고, 확정된 값은 상수 클래스(`TransactionType`, `TransactionStatus`)로 참조합니다. 단 `account_status`, `account_type`은 10-20부터 enum(`AccountStatus`, `AccountType`)으로 매핑합니다 (저장값은 같음). `account_type`, `transfer_type` 값은 아직 미정입니다.
- 현금 출금은 이체 한도와 별개입니다. `today_transfer_amount`에는 이체만 누적합니다.
- 다른 테이블을 가리키는 컬럼은 연관관계 없이 `Long` ID 필드로 매핑합니다.

**제약 조건은 아래만 둡니다.** FK, CHECK, 인덱스는 두지 않습니다.

| 테이블 | 제약 |
|---|---|
| 모든 테이블 | PK (`AUTO_INCREMENT`) |
| `account` | `member_id NOT NULL`, `password_fail_count NOT NULL DEFAULT 0`, `account_number UNIQUE` |

| 구분 | 테이블 |
|---|---|
| 계좌 | [account](#1-account--계좌), [account_favorite](#4-account_favorite--계좌-즐겨찾기), [asset_report](#5-asset_report--자산-리포트) |
| 거래/이체 | [transaction_history](#2-transaction_history--거래-내역), [account_transfer](#3-account_transfer--계좌-이체), [scheduled_transfer](#7-scheduled_transfer--예약정기이체) |
| 공통코드 | [financial_institution_code](#6-financial_institution_code--금융기관-코드) |

---

## 1. account — 계좌
고객이 개설한 입출금, 예적금, 대출 등의 계좌 정보를 통합하여 관리합니다.

| 컬럼 | 타입 / 제약 | 설명 |
|---|---|---|
| `account_id` | BIGINT, PK, 자동 증가 | 계좌 번호 (내부 식별자) |
| `member_id` | BIGINT, NOT NULL | 계좌를 소유한 회원 번호 (`members` 참조, FK 없음) |
| `account_number` | VARCHAR(30), UNIQUE | 실제 계좌번호 |
| `account_type` | VARCHAR(30) | 계좌 유형 (입출금, 예적금, 대출 등). 값 목록 미정 |
| `balance` | BIGINT | 현재 잔액(원). 계좌 개설 때 0을 넣음 |
| `account_status` | VARCHAR(20) | 계좌 상태: `ACTIVE` 정상 / `PENDING` 개설대기 / `FROZEN` 동결 / `CLOSED` 해지. 입금은 `ACTIVE`·`FROZEN`, 출금은 `ACTIVE`만 가능 |
| `password_hash` | VARCHAR(255) | 계좌 비밀번호 (해시) |
| `password_fail_count` | INT, NOT NULL, DEFAULT 0 | 연속 비밀번호 오류 횟수. 최대 5회, 성공 시 0. **원본에 없던 컬럼 (유일한 추가)** |
| `last_transaction_at`| DATETIME(6) | 최종 거래 일시 |
| `signup_date` | DATE | 가입일 (예적금/대출용) |
| `expired_date` | DATE | 만기일 (예적금/대출용) |
| `interest_rate` | DECIMAL(7,4) | 적용 이자율 |
| `signup_period` | INT | 가입 기간(개월) |
| `monthly_payment` | BIGINT | 월 납입 금액 |
| `loan_amount` | BIGINT | 대출 원금 |
| `loan_balance` | BIGINT | 대출 잔액 |
| `payment_day` | INT | 매월 납입/결제일 |
| `repayment_method` | VARCHAR(30) | 대출 상환 방식 |
| `current_payment_round`| INT | 현재 납입 회차 |
| `total_payment_rounds`| INT | 총 납입 회차 |
| `today_transfer_amount`| BIGINT | 당일 누적 이체 금액 (1일 한도 체크용). 계좌 개설 때 0을 넣음 |

> **`password_fail_count` 처리 규칙** (엔티티 `Account`)
> - `increasePasswordFailCount()`: 오류 시 1 증가 (5에서 멈춤)
> - `resetPasswordFailCount()`: 성공 시 0으로 초기화
> - `isPasswordFailLimitReached()`: 5회 도달 여부
> - 5회가 되어도 `account_status`는 바꾸지 않습니다. 잠금 상태를 따로 두지 않고, `withdraw()`가 오류 횟수로 출금을 막습니다. 관리자 잠금 해제는 `resetPasswordFailCount()`입니다.

> **`today_transfer_amount` 처리 규칙** (엔티티 `Account.touchTransaction(now)`)
> - 입금·출금·이체 등 **모든 거래**에서 호출합니다.
> - `last_transaction_at`의 날짜가 `now`와 다르면 `today_transfer_amount`를 0으로 초기화한 뒤 `last_transaction_at`을 갱신합니다.
> - 이체할 때는 날짜가 같으면 누적 금액에 이번 금액을 더해 `members.daily_transfer_limit`를 넘는지 확인합니다.
> - 입금만 있었던 날에도 날짜를 함께 갱신하므로, 어제 이체액이 오늘 한도에 합산되지 않습니다.

> **팀원3 테이블과 겹치는 컬럼**
> `signup_date`~`total_payment_rounds`는 예적금·대출 **계약** 정보로 팀원3 테이블과 겹치지만, 삭제하지 않고 팀원2에서 독립적으로 사용합니다. 아래는 참고용 대응표입니다.
>
> | account 컬럼 | 팀원3 예적금 | 팀원3 대출 |
> |---|---|---|
> | `signup_date` | `PRODUCT_SUBSCRIPTION.SUBSCRIBED_AT` | 해당 없음 (`LOAN.STARTED_AT`은 대출 실행일로 다른 정보) |
> | `expired_date` | `PRODUCT_SUBSCRIPTION.MATURITY_DATE` | `LOAN.MATURITY_DATE` |
> | `interest_rate` | `PRODUCT.BASE_INTEREST_RATE` | `LOAN.ANNUAL_INTEREST_RATE` |
> | `signup_period` | `PRODUCT.TERM_MONTHS` | `LOAN.TERM_MONTHS` |
> | `monthly_payment` | `PRODUCT_SUBSCRIPTION.MONTHLY_PAYMENT` | `LOAN_REPAYMENT_SCHEDULE.TOTAL_AMOUNT` |
> | `loan_amount` | - | `LOAN.APPROVED_AMOUNT` |
> | `loan_balance` | - | 계산: `APPROVED_AMOUNT` − 상환 완료 회차 원금 합계 |
> | `payment_day` | `SUBSCRIBED_AT`의 일(day) | `DUE_DATE`의 일(day) |
> | `repayment_method` | - | `LOAN.REPAYMENT_METHOD` |
> | `current_payment_round` | `transaction_history.payment_round`로 계산 | 상환 완료된 최대 `INSTALLMENT_NO` |
> | `total_payment_rounds` | `PRODUCT.TERM_MONTHS` | `LOAN.TERM_MONTHS` |

## 2. transaction_history — 거래 내역
계좌에서 발생한 입금, 출금, 이체 등의 모든 거래 내역을 기록하는 거래 원장입니다. 한 번 기록한 행은 수정하거나 삭제하지 않습니다.

| 컬럼 | 타입 / 제약 | 설명 |
|---|---|---|
| `transaction_history_id`| BIGINT, PK, 자동 증가| 거래 내역 식별자 |
| `account_id` | BIGINT | 대상 계좌 ID (`account` 참조, FK 없음) |
| `transaction_type` | VARCHAR(30) | 거래 유형: `DEPOSIT` 입금 / `WITHDRAWAL` 출금 / `TRANSFER_OUT` 이체 보냄 / `TRANSFER_IN` 이체 받음 |
| `transaction_amount` | BIGINT | 거래 금액 |
| `balance_after_transaction`| BIGINT | 거래 후 잔액 |
| `transaction_content`| VARCHAR(255) | 거래 내용 (적요) |
| `transaction_at` | DATETIME(6) | 거래 일시 (코드에서 직접 값을 넣음) |
| `transaction_status` | VARCHAR(20) | 거래 상태: `COMPLETED` 완료 (실패한 거래는 롤백되어 행이 남지 않음) |
| `payment_round` | INT | 납입 회차 (예적금/대출인 경우) |
| `transaction_sqno` | VARCHAR(100) | 거래 고유 번호. 형식 `yyyyMMddHHmmss-난수6자리` (예: `20261015143012-482915`). UNIQUE가 없으므로 중복 방지는 코드에서 처리 |

## 3. account_transfer — 계좌 이체
계좌 간 이체 발생 시 송금 및 수취 정보를 상세하게 기록합니다.

| 컬럼 | 타입 / 제약 | 설명 |
|---|---|---|
| `account_transfer_id` | BIGINT, PK, 자동 증가 | 이체 식별자 |
| `account_id` | BIGINT | 출금 계좌 ID (`account` 참조, FK 없음) |
| `withdrawal_account_number`| VARCHAR(30) | 출금 계좌번호 |
| `financial_institution_id`| BIGINT | 입금 금융기관 ID (`financial_institution_code` 참조, FK 없음) |
| `deposit_account_number` | VARCHAR(30) | 입금 계좌번호 |
| `transfer_amount` | BIGINT | 이체 금액 |
| `sender_memo` | VARCHAR(100) | 보내는 분 통장 표시 내용 |
| `receiver_memo` | VARCHAR(100) | 받는 분 통장 표시 내용 |
| `transfer_at` | DATETIME(6) | 이체 일시 (코드에서 직접 값을 넣음) |
| `transfer_type` | VARCHAR(30) | 이체 유형 (즉시이체, 예약이체 등). 값 목록 미정 |
| `transaction_sqno` | VARCHAR(100) | 거래 고유 번호 |

## 4. account_favorite — 계좌 즐겨찾기
고객이 자주 쓰는 이체 대상 계좌를 등록해두는 기능입니다.

| 컬럼 | 타입 / 제약 | 설명 |
|---|---|---|
| `account_favorite_id` | BIGINT, PK, 자동 증가 | 즐겨찾기 식별자 |
| `account_id` | BIGINT | 회원(또는 본인 계좌) ID |
| `financial_institution_id`| BIGINT | 상대방 금융기관 ID |
| `recipient_account_number`| VARCHAR(30) | 상대방 계좌번호 |
| `recipient_account_name` | VARCHAR(100) | 상대방 예금주명 |
| `memo` | VARCHAR(255) | 메모 |
| `favorite_alias` | VARCHAR(100) | 즐겨찾기 별칭 |
| `favorite_created_at` | DATETIME(6) | 등록 일시 (코드에서 직접 값을 넣음) |
| `sort_order` | INT | 정렬 순서 |

## 5. asset_report — 자산 리포트
특정 기준년월의 자산 증감 및 항목별 리포트 데이터입니다.

| 컬럼 | 타입 / 제약 | 설명 |
|---|---|---|
| `asset_report_id` | BIGINT, PK, 자동 증가 | 자산 리포트 식별자 |
| `account_id` | BIGINT | 기준 계좌 (또는 회원). **자산 리포트는 회원 단위라 `member_id`를 저장한다 (10-19)** |
| `recent_deposit_amount` | BIGINT | 최근 입금액 |
| `recent_withdrawal_amount`| BIGINT | 최근 출금액 |
| `savings_amount` | BIGINT | 저축금액 |
| `loan_interest_repayment_amount`| BIGINT | 대출 이자/상환액 |
| `investment_amount` | BIGINT | 투자 금액 |
| `utility_bill_amount` | BIGINT | 공과금 지출액 |
| `maintenance_fee_amount` | BIGINT | 관리비 지출액 |
| `standard_year_month` | CHAR(6) | 기준 년월 (예: '202610') |

## 6. financial_institution_code — 금융기관 코드
타행 이체 등을 위한 금융기관 목록입니다.

| 컬럼 | 타입 / 제약 | 설명 |
|---|---|---|
| `financial_institution_id` | BIGINT, PK, 자동 증가 | 기관 식별자 |
| `financial_institution_name`| VARCHAR(100) | 기관명 (예: 신한은행, 국민은행) |
| `financial_institution_category_code`| VARCHAR(20) | 기관 분류 코드 (은행, 증권 등) |

## 7. scheduled_transfer — 예약·정기이체
예약이체(지정 일시 1회)와 정기이체(매월 반복)를 등록해 두는 테이블입니다. (10-20 추가)
등록할 때 계좌 비밀번호(10만 원 초과 타인 이체면 OTP도)를 확인하고, 실행은 스케줄러가 인증 없이 합니다.
실행된 이체는 `account_transfer`·`transaction_history`에 `transfer_type` = `SCHEDULED`/`RECURRING`으로 기록합니다.

| 컬럼 | 타입 / 제약 | 설명 |
|---|---|---|
| `scheduled_transfer_id` | BIGINT, PK, 자동 증가 | 예약·정기이체 식별자 |
| `member_id` | BIGINT | 등록한 회원 ID (`members` 참조) |
| `account_id` | BIGINT | 출금 계좌 ID (`account` 참조) |
| `deposit_account_number` | VARCHAR(30) | 입금 계좌번호 |
| `transfer_amount` | BIGINT | 이체 금액 |
| `sender_memo` | VARCHAR(100) | 보내는 분 통장 표시 내용 |
| `receiver_memo` | VARCHAR(100) | 받는 분 통장 표시 내용 |
| `schedule_type` | VARCHAR(20) | `SCHEDULED` 예약 / `RECURRING` 정기 (enum `ScheduleType`) |
| `next_execute_at` | DATETIME(6) | 다음 실행 일시. 예약은 실행 일시, 정기는 다음 회차 일시 (실행할 때마다 다음 달로 바뀜) |
| `transfer_day` | INT | 정기: 매월 실행일 1~31. 그 달에 없는 날(예: 2월 30일)이면 말일에 실행 |
| `end_date` | DATE | 정기: 종료일. NULL이면 해지할 때까지 |
| `status` | VARCHAR(20) | `WAITING` 대기 / `COMPLETED` 완료 / `FAILED` 실패 / `CANCELLED` 취소 / `PAUSED` 정기 중지 (enum `ScheduledTransferStatus`). 정기이체는 한 회차가 실패해도 `WAITING`을 유지하고 `last_result`에 사유를 남긴다 |
| `last_executed_at` | DATETIME(6) | 마지막 실행 일시 |
| `last_result` | VARCHAR(200) | 마지막 실행 결과 (실패 사유 등) |
| `created_at` | DATETIME(6) | 등록 일시 |
| `updated_at` | DATETIME(6) | 수정 일시 |
