# 팀원2 테이블 데이터 사전

[schema-team2-mysql.sql](./schema-team2-mysql.sql)에 정의된 테이블 6개의 컬럼 설명입니다.
DDL을 최종 기준으로 삼았으며(ERD 수정 반영), SQL 파일을 수정하면 이 문서도 함께 고칩니다.

- 금액은 모두 `BIGINT`(원 단위)입니다. 팀원1·팀원3과 같은 형식이며 엔티티에서는 `Long`으로 매핑합니다.
- 1일/1회 이체한도는 계좌가 아니라 `members.daily_transfer_limit`, `members.once_transfer_limit`(팀원1)에 있습니다. 당일 누적 이체 금액은 컬럼으로 두지 않고, `account_transfer`에서 그 회원 계좌들의 오늘 `COMPLETED` 이체 합계로 계산합니다.

| 구분 | 테이블 |
|---|---|
| 계좌 | [account](#1-account--계좌), [account_favorite](#4-account_favorite--계좌-즐겨찾기), [asset_report](#5-asset_report--자산-리포트) |
| 거래/이체 | [transaction_history](#2-transaction_history--거래-내역), [account_transfer](#3-account_transfer--계좌-이체) |
| 공통코드 | [financial_institution_code](#6-financial_institution_code--금융기관-코드) |

---

## 1. account — 계좌
고객이 개설한 입출금, 예적금, 대출 등의 계좌 정보를 통합하여 관리합니다.

| 컬럼 | 타입 / 제약 | 설명 |
|---|---|---|
| `account_id` | BIGINT, PK, 자동 증가 | 계좌 번호 (내부 식별자) |
| `member_id` | BIGINT, NOT NULL, FK | 계좌를 소유한 회원 번호 (`members` 참조) |
| `account_number` | VARCHAR(30), UNIQUE | 실제 계좌번호 |
| `account_type` | VARCHAR(30), CHECK | 계좌 유형: `CHECKING` 입출금 / `DEPOSIT` 예금 / `SAVINGS` 적금 / `LOAN` 대출 |
| `balance` | BIGINT, 0 이상 | 현재 잔액(원) |
| `account_status` | VARCHAR(20), CHECK | `PENDING` 개설대기 / `ACTIVE` 정상 / `FROZEN` 동결 / `LOCKED` 비밀번호 잠금 / `CLOSED` 해지 / `REJECTED` 개설거절. FROZEN·LOCKED는 출금/이체만 막고 입금은 허용 |
| `password_hash` | VARCHAR(255) | 계좌 비밀번호 (BCrypt 해시) |
| `password_fail_count` | INT, 0~5 | 연속 비밀번호 오류 횟수. 5회면 `LOCKED`, 성공 시 0. 이체가 롤백돼도 남도록 별도 트랜잭션(`REQUIRES_NEW`)으로 저장 |
| `last_transaction_at`| DATETIME(6) | 최종 거래 일시 |
| `opened_date` | DATE, NOT NULL | 계좌 개설일. 입출금 계좌를 포함한 모든 계좌에 있음. 예적금은 상품 가입일과 같음 |
| `expired_date` | DATE | 만기일 (예적금/대출용) |
| `interest_rate` | DECIMAL(7,4) | 적용 이자율 (팀원3과 같은 형식) |
| `signup_period` | INT | 가입 기간(개월) |
| `monthly_payment` | BIGINT | 월 납입 금액 |
| `loan_amount` | BIGINT | 대출 원금 |
| `loan_balance` | BIGINT | 대출 잔액 |
| `payment_day` | INT | 매월 납입/결제일 |
| `repayment_method` | VARCHAR(30) | 대출 상환 방식 |
| `current_payment_round`| INT | 현재 납입 회차 |
| `total_payment_rounds`| INT | 총 납입 회차 |

> **팀원3 테이블과 겹치는 컬럼 (협의 후 정리 예정)**
> `expired_date`~`total_payment_rounds`는 예적금·대출 **계약** 정보로, 팀원3 테이블에 같은 정보가 있거나 계산으로 구할 수 있습니다. 당분간 유지하고 팀원3과 협의한 뒤 정리합니다. 계좌 개설일(`opened_date`)은 모든 계좌에 공통이므로 account에 둡니다.
>
> | account 컬럼 | 팀원3 예적금 | 팀원3 대출 |
> |---|---|---|
> | `opened_date` | `PRODUCT_SUBSCRIPTION.SUBSCRIBED_AT` (같은 정보, 팀원3 쪽 정리 검토) | 해당 없음 (`LOAN.STARTED_AT`은 대출 실행일로 다른 정보) |
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
계좌에서 발생한 입금, 출금, 이체 등의 모든 거래 내역을 기록하는 거래 원장입니다. 한 번 기록한 행은 수정하거나 삭제하지 않습니다. 이체 1건은 출금 계좌 1행과 입금 계좌 1행으로 기록합니다.

| 컬럼 | 타입 / 제약 | 설명 |
|---|---|---|
| `transaction_history_id`| BIGINT, PK, 자동 증가| 거래 내역 식별자 |
| `account_id` | BIGINT, FK | 대상 계좌 ID (`account` 참조) |
| `account_transfer_id` | BIGINT, FK, NULL 허용 | 이체로 생긴 거래면 `account_transfer` 참조. 단순 입출금은 NULL |
| `transaction_type` | VARCHAR(30) | 거래 유형 (입금, 출금, 이체 등) |
| `transaction_amount` | BIGINT, 0보다 큼 | 거래 금액 |
| `balance_after_transaction`| BIGINT, 0 이상 | 거래 후 잔액 |
| `counterpart_account_number` | VARCHAR(30) | 상대 계좌번호 |
| `counterpart_name` | VARCHAR(100) | 상대 예금주명 |
| `transaction_content`| VARCHAR(255) | 거래 내용 (적요) |
| `transaction_at` | DATETIME(6) | 거래 일시 (엔티티에서 직접 값을 넣음) |
| `transaction_status` | VARCHAR(20) | 거래 상태 (성공, 실패 등) |
| `payment_round` | INT | 납입 회차 (예적금/대출인 경우) |
| `transaction_sqno` | VARCHAR(100), UNIQUE(`account_id`, `transaction_sqno`) | 거래 고유 번호 (중복 방지 식별키). 이체 1건의 출금·입금 2행은 같은 번호 |

## 3. account_transfer — 계좌 이체
계좌 간 이체 발생 시 송금 및 수취 정보를 상세하게 기록합니다.

| 컬럼 | 타입 / 제약 | 설명 |
|---|---|---|
| `account_transfer_id` | BIGINT, PK, 자동 증가 | 이체 식별자 |
| `account_id` | BIGINT, FK | 출금 계좌 ID |
| `to_account_id` | BIGINT, FK, NULL 허용 | 입금 계좌 ID. 당행 이체만 값이 있고 타행은 NULL. 출금 계좌와 같을 수 없음 |
| `withdrawal_account_number`| VARCHAR(30) | 출금 계좌번호 |
| `financial_institution_id`| BIGINT | 입금 금융기관 ID (`financial_institution_code` 참조) |
| `deposit_account_number` | VARCHAR(30) | 입금 계좌번호 |
| `transfer_amount` | BIGINT, 0보다 큼 | 이체 금액 |
| `sender_memo` | VARCHAR(100) | 보내는 분 통장 표시 내용 |
| `receiver_memo` | VARCHAR(100) | 받는 분 통장 표시 내용 |
| `transfer_at` | DATETIME(6) | 이체 일시 (엔티티에서 직접 값을 넣음) |
| `transfer_type` | VARCHAR(30) | 이체 유형 (즉시이체, 예약이체 등) |
| `transfer_status` | VARCHAR(20), CHECK | `COMPLETED` 완료 / `SCHEDULED` 예약대기 / `FAILED` 실패 / `CANCELLED` 취소 |
| `transaction_sqno` | VARCHAR(100), UNIQUE | 거래 고유 번호 (같은 이체 중복 실행 방지) |

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
| `favorite_created_at` | DATETIME(6) | 등록 일시 |
| `sort_order` | INT | 정렬 순서 |

## 5. asset_report — 자산 리포트
특정 기준년월의 자산 증감 및 항목별 리포트 데이터입니다.

| 컬럼 | 타입 / 제약 | 설명 |
|---|---|---|
| `asset_report_id` | BIGINT, PK, 자동 증가 | 자산 리포트 식별자 |
| `account_id` | BIGINT | 기준 계좌 (또는 회원) |
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
