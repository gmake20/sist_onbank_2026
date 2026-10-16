# 계좌번호 생성 규칙과 예적금 계좌 만들기 (팀2 → 팀3 공유)

작성: 팀2 (2026-10-16)
대상: 팀3 예적금 상품 가입 기능

예금·적금에 가입할 때 `account` 테이블에 계좌 1행을 만들고, 그 `account_id`를 `product_subscription.account_id`에 넣어 주세요. 아래는 그때 지켜야 할 규칙입니다.

---

## 1. 계좌번호 형식

```
앞자리4 - 난수3 - 난수6        예) 2002-482-915307
```

| 계좌 유형 | `account_type` 값 | 앞자리 |
|---|---|---|
| 입출금 | `CHECKING` | `1002` |
| 예금 | `DEPOSIT` | `2002` |
| 적금 | `SAVINGS` | `3002` |
| 대출 | `LOAN` | `4002` |

- 하이픈(`-`)까지 포함해 저장합니다 (총 15자, 컬럼은 `VARCHAR(30)`)
- `account_number`는 **UNIQUE**입니다. 만든 번호가 이미 있으면 새로 만들어야 합니다 (팀2는 5번까지 다시 만들고, 그래도 겹치면 오류)
- `account_type` 값은 팀2 상수 클래스 `org.doit.onbank.account.entity.AccountType`을 사용해 주세요. 문자열을 직접 쓰면 오타가 나도 알 수 없습니다

## 2. 번호는 팀2 클래스로 만들어 주세요 (권장)

번호 생성 규칙을 팀3 코드에 따로 만들면, 규칙이 바뀔 때 두 곳을 고쳐야 하고 중복 확인을 빠뜨리기 쉽습니다. 팀2의 `AccountNumberGenerator`를 주입받아 쓰면 형식과 중복 확인이 함께 처리됩니다.

```java
import org.doit.onbank.account.entity.AccountType;
import org.doit.onbank.account.service.AccountNumberGenerator;

private final AccountNumberGenerator accountNumberGenerator;   // 생성자 주입

String accountNumber = accountNumberGenerator.generate(AccountType.DEPOSIT);   // 적금은 AccountType.SAVINGS
```

- 알 수 없는 유형을 넘기면 `IllegalArgumentException`, 번호를 만들지 못하면 `IllegalStateException`

## 3. 계좌 행을 만들 때 넣어야 하는 값

`account` 행은 팀2 엔티티 `org.doit.onbank.account.entity.Account`의 빌더로 만들고 `AccountRepository.save()`로 저장합니다.

| 컬럼 | 넣을 값 | 이유 |
|---|---|---|
| `member_id` | 가입한 회원 ID | 필수 (NOT NULL) |
| `account_number` | 2장에서 만든 번호 | |
| `account_type` | `AccountType.DEPOSIT` 또는 `AccountType.SAVINGS` | 전계좌조회에서 예금/적금 영역으로 나눠 보여 줍니다 |
| `balance` | **`0L`** | NULL이면 입금·이체 계산에서 오류가 납니다 |
| `today_transfer_amount` | **`0L`** | 같은 이유 |
| `account_status` | `AccountStatus.ACTIVE` | 정상 계좌. 상태 값은 `AccountStatus` 상수 사용 |
| `signup_date` | 가입일 (오늘) | |
| `expired_date` | 만기일 (가입일 + 기간 개월) | |
| `interest_rate` | 적용 이자율 (`BigDecimal`, `DECIMAL(7,4)`) | |
| `signup_period` | 가입 기간 (개월) | |
| `monthly_payment` | 적금 월 납입액 (예금은 비움) | |
| `password_hash` | **협의 필요** (5장) | |

```java
Account account = Account.builder()
        .memberId(memberId)
        .accountNumber(accountNumberGenerator.generate(AccountType.DEPOSIT))
        .accountType(AccountType.DEPOSIT)
        .balance(0L)
        .todayTransferAmount(0L)
        .accountStatus(AccountStatus.ACTIVE)
        .signupDate(today)
        .expiredDate(today.plusMonths(termMonths))
        .interestRate(product.getBaseInterestRate())
        .signupPeriod(termMonths)
        .build();
Long accountId = accountRepository.save(account).getAccountId();   // → product_subscription.account_id
```

## 4. 가입 금액을 옮길 때

가입 금액(예금 원금, 적금 1회차)은 계좌를 만든 뒤 **팀2 이체 메서드**로 회원의 입출금 계좌에서 새 계좌로 옮겨 주세요. `balance`를 직접 바꾸면 거래내역(`transaction_history`)이 남지 않습니다.

```java
accountTransferService.transfer(memberId, 출금할_입출금계좌_ID, 계좌비밀번호, 새_계좌번호, 금액, 내_통장_표시, 받는_통장_표시);
```

- 비밀번호 확인, 잔액·한도 확인, 거래내역 기록이 이 안에서 처리됩니다
- 계좌 생성·이체·`product_subscription` 저장을 **하나의 `@Transactional` 메서드** 안에서 호출하면, 이체가 실패(잔액 부족, 비밀번호 오류)할 때 계좌 생성까지 함께 취소됩니다

## 5. 함께 정해야 할 것

| # | 항목 | 팀2 의견 |
|---|---|---|
| 1 | `product.product_type` 값 | `AccountType`과 같은 `DEPOSIT` / `SAVINGS`로 맞추면 변환 코드가 필요 없습니다 |
| 2 | 예적금 계좌 비밀번호 | 따로 받지 않고 출금하는 입출금 계좌 비밀번호로 확인. `password_hash`는 비워 둠 (예적금 계좌에서 직접 출금하지 않으므로) |
| 3 | 만기일·이자율 | `account`와 `product_subscription`에 비슷한 컬럼이 둘 다 있습니다 (`expired_date` ↔ `maturity_date` 등). 같은 값을 넣도록 계산 기준(가입일 + 기간 개월)을 맞춰 주세요 |
| 4 | 대출 계좌(`4002`) | 지금 대출은 기존 입출금 계좌(`repaymentAccountId`)로 처리하고 있어서, 대출 계좌를 따로 만들지 여부를 정해야 합니다 |

## 참고

- `Account`, `AccountType`, `AccountStatus`, `AccountRepository`, `AccountTransferService`는 이미 main에 있습니다. `AccountNumberGenerator`는 팀2가 커밋해서 main에 머지한 뒤 사용할 수 있습니다 (머지되면 따로 알려 드리겠습니다)
- 컬럼 설명: [schema-team2-dictionary.md](schema-team2-dictionary.md), DDL: `project/onbank/src/main/resources/db/03_account_ddl.sql`
