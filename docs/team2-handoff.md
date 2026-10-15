# 팀원2 작업 인수인계 (2026-10-15 회사 → 집)

> 새 세션에서 Claude에게 이렇게 요청하세요.
> **"docs/team2-handoff.md 와 docs/team2-progress.md 읽고 이어서 작업하자"**
>
> - 이 파일: 최근 작업 상태, 바로 다음에 할 일, 주의할 점
> - [team2-progress.md](team2-progress.md): 확정한 결정 사항 전체(3장), 엔티티 메서드, 이체 설계
> - [schema-team2-dictionary.md](schema-team2-dictionary.md): 테이블 컬럼 설명

---

## 0. 집에서 시작하기 전에 확인할 것

1. **2026-10-15 작업은 `account` 브랜치에 커밋·푸시했다** (main 머지 전). 집 PC에서 받기:
   ```
   git fetch origin
   git checkout account
   git pull origin account
   ```
   - 확인: `project/onbank/src/main/java/org/doit/onbank/account/service/AccountService.java` 가 있으면 받아진 것
2. 브랜치: `account`. `origin/main`(PR #17까지)을 포함한 상태에서 작업했다. 아직 main에 PR을 올리지 않았다
3. 회사 PC에만 있는 것 (Git에 없음)
   - `CLAUDE.local.md`: 개인 작업 원칙 (`.git/info/exclude`로 제외)
   - Claude 메모리: "커밋·푸시는 명시적 지시가 있을 때만"
   - 집 PC에서도 같은 규칙을 원하면 아래 "1. 작업 규칙"을 Claude에게 알려 주면 된다

## 1. 작업 규칙 (사용자 지시)

- **커밋·푸시는 사용자가 "커밋해 줘", "푸시해 줘"라고 할 때만 한다.** merge, reset 등 이력을 바꾸는 git 작업도 같다
- 다른 팀(팀1/3/4) 파일은 수정하지 않는다. 문제가 있으면 사용자에게 알리고, 팀에 보낼 문구를 제안한다
- 테이블 기준은 팀원2 데이터 사전 원본. 원본에 없는 컬럼은 추가하지 않는다 (예외: `account.password_fail_count`)
- 확인한 사실, 추정, 제안을 구분해서 말한다. 실행하지 않은 것을 완료했다고 하지 않는다
- 사소한 선택은 판단해서 진행하되 가정을 밝히고, 결과를 크게 바꾸는 것만 질문한다

## 2. 현재 상태 한눈에

| 단계 | 상태 |
|---|---|
| 테이블·엔티티 6개 | 완료 (main 머지, 공용 DB 생성, validate 통과) |
| 상태·유형 값 확정 | 완료 (progress 3장 13~17번) |
| 입출금 엔티티·Repository·Service | 완료 |
| 단위 테스트 29개 + 화면 테스트 15개 | **44개 통과** |
| 입출금 화면 (Controller + Thymeleaf) | 코드 완료, 서버 실행(`contextLoads`) 통과 |
| **브라우저에서 화면 확인** | **아직 안 함 ← 다음 할 일** |
| DB 통합 테스트 | 방법 결정 필요 |
| 이체 | 시작 전 |

## 3. 바로 다음에 할 일

### 3-1. 브라우저에서 입출금 화면 확인 (사용자가 직접)

1. 워크벤치에서 [data-team2-mysql.sql](data-team2-mysql.sql) 실행
   - 15번째 줄 `'여기에_로그인_아이디'` → 실제 로그인 아이디로 바꿈
   - 첫 `SELECT @member_id`가 NULL이면 아이디가 틀린 것
2. 서버 실행: VS Code에서 `OnbankApplication` Run, 또는 `cd project/onbank && ./gradlew bootRun`
3. `http://localhost/login` 로그인 → `http://localhost/mybank/accounts` (포트 80이라 번호 없음)
4. 확인 목록

   | 확인할 것 | 기대 결과 |
   |---|---|
   | 목록 | 계좌 3개, 총잔액 150,000원 |
   | `TEST-0001` 입금 1만 원 | 영수증 표시, 잔액 110,000원 |
   | `TEST-0001` 출금, 비밀번호 `0000` | "비밀번호가 일치하지 않습니다" + "비밀번호 오류 1회" |
   | `TEST-0001` 출금, 비밀번호 `1234` | 성공, 오류 횟수 0으로 초기화 |
   | 잔액보다 큰 금액 출금 | "잔액이 부족합니다" |
   | `TEST-0002` (동결) | 입금 버튼만 있음 |
   | `TEST-0003` (개설대기) | 입금·출금 버튼 없음 |

5. 끝나면 같은 SQL 파일 아래쪽 삭제 SQL로 정리

### 3-2. 사용자에게 결정받을 것

| 항목 | 선택지 | 제안 |
|---|---|---|
| DB 통합 테스트 방법 | ① 입금만 롤백 테스트 / ② 출금까지 커밋 후 삭제 | ① (출금은 화면 확인으로 대신) |
| 입금·출금 1회 금액 상한 | 지금은 1원 이상이면 모두 받음 | 사용자 결정 |
| 기존 목업 정리 | `AccountController`(`/account/list`, `/account/transfer`), `list.html` | 새 화면 확인 후 결정 |
| 헤더에 "내 계좌" 링크 | `fragments/header.html`은 공용 파일 | 위치를 사용자에게 확인 |
| `account_type`, `transfer_type` 값 | 미정 | 이체 시작 전에 정하기 |

### 3-3. 그 다음 큰 작업: 이체

- 설계는 [team2-progress.md](team2-progress.md) 6장
- 1일 한도: 출금 계좌의 `today_transfer_amount + 이번 금액 ≤ members.daily_transfer_limit` (계좌별 누적, 회원별 한도)
- 먼저 정할 것: `members` 한도 컬럼을 어떻게 읽을지 (`Member` 엔티티에 필드가 없음, 팀1 파일)
- 목업 [transfer.html](../project/onbank/src/main/resources/templates/account/transfer.html) 디자인 활용

## 4. 2026-10-15에 추가·수정한 파일 (`account` 브랜치에 푸시, main 머지 전)

**수정**
- `docs/schema-team2-dictionary.md`, `docs/team2-progress.md`
- `account/entity/Account.java` (입출금 메서드, 주석)
- `account/entity/TransactionHistory.java` (주석)
- `static/css/account.css` (맨 아래 입출금 스타일 추가)

**새 파일**
- `docs/data-team2-mysql.sql`, `docs/team2-handoff.md`
- `account/entity/AccountStatus.java`, `TransactionType.java`, `TransactionStatus.java`
- `account/repository/AccountRepository.java`, `TransactionHistoryRepository.java`
- `account/service/AccountService.java`, `AccountPasswordVerifier.java`, `TransactionSqnoGenerator.java`
- `account/controller/AccountPageController.java`
- `account/dto/DepositForm.java`, `WithdrawForm.java`, `TransactionReceipt.java`
- `templates/account/my-accounts.html`, `detail.html`, `deposit.html`, `withdraw.html`
- `static/js/account.js`
- `src/test/java/org/doit/onbank/account/` 아래 테스트 5개

(Java 경로 앞부분: `project/onbank/src/main/java/org/doit/onbank/`, 화면 경로 앞부분: `project/onbank/src/main/resources/`)

## 5. 구현 구조 요약

```
[화면] /mybank/accounts/**  (SecurityConfig 의 /mybank/** = ROLE_USER 규칙으로 로그인 보호)
   AccountPageController
     GET  /mybank/accounts                 → my-accounts.html  (목록, 총잔액은 해지 제외)
     GET  /mybank/accounts/{id}            → detail.html       (거래내역 + 영수증)
     GET/POST /mybank/accounts/{id}/deposit   → deposit.html
     GET/POST /mybank/accounts/{id}/withdraw  → withdraw.html
     성공 → redirect + flash("receipt")  (PRG)
     남의/없는 계좌 → NoSuchElementException → 목록으로 redirect

[Service] AccountService
     deposit(accountId, amount, content)                  본인 확인 없음 (남이 넣어 주는 돈, 대출금 입금)
     withdraw(memberId, accountId, amount, rawPassword, content)
       ① AccountPasswordVerifier.verify()  ← REQUIRES_NEW, 계좌 잠금보다 먼저
       ② findByIdForUpdate (비관적 락)
       ③ Account.withdraw()  (상태·비밀번호 5회·잔액 검사)
       ④ transaction_history 1행 저장 (거래번호 TransactionSqnoGenerator)
     getMyAccounts / getMyAccount / getMyHistories       (읽기 전용)

[Entity] Account.deposit() / withdraw() / canDeposit() / canWithdraw() / touchTransaction()
```

## 6. 주의할 점 (작업하다 알게 된 것)

- **비밀번호 확인은 계좌 잠금보다 먼저.** 순서가 바뀌면 출금 트랜잭션이 잡은 잠금을 REQUIRES_NEW 트랜잭션이 기다리며 멈춘다
- **`AccountPasswordVerifier.verify()`는 비밀번호가 틀리면 예외 대신 `false`를 돌려준다.** 그 메서드 안에서 예외를 던지면 REQUIRES_NEW 트랜잭션이 롤백되어 증가한 오류 횟수가 사라진다
- **출금은 `@Transactional` 롤백 테스트로 DB 테스트를 할 수 없다** (위 3-2 참고)
- **`ddl-auto=validate`는 모든 팀의 엔티티를 검사한다.** 다른 팀 테이블 하나만 어긋나도 서버 전체가 뜨지 않는다. 오늘 팀3 `loan_repayment_schedule`(`loan_id` 컬럼 없음)로 한동안 서버가 안 떴다가 해결됨
- **`application-local.properties`는 팀 공용 DB(cafe24)를 가리킨다.** 테스트가 커밋하면 공용 DB에 남는다
- **화면 테스트(`@WebMvcTest`)에는 `@MockitoBean JpaMetamodelMappingContext`가 필요하다.** `OnbankApplication`의 `@EnableJpaAuditing` 때문
- 로그인 후 리다이렉트 주소는 `/login` (테스트에서 패턴 `**/login`은 안 맞음)
- 회사 PC에는 mysql 명령줄 도구가 없어서 Claude가 DB를 직접 조회하지 못했다. DB 확인은 워크벤치로

## 7. 자주 쓰는 명령 (`project/onbank` 폴더에서)

```
./gradlew compileJava                                   # 컴파일
./gradlew test --tests "org.doit.onbank.account.*"      # 팀2 테스트 44개 (DB 불필요)
./gradlew test --tests "org.doit.onbank.OnbankApplicationTests"   # 서버가 뜨는지 (공용 DB 연결, 읽기만)
./gradlew bootRun                                       # 서버 실행 (포트 80)
```

## 8. 팀에 전달·협의할 것 (진행 상황)

| 대상 | 내용 | 상태 |
|---|---|---|
| 팀1 | members 이체한도 컬럼 ALTER | 완료 |
| 팀1 | progress 5장 "팀1에 전달" 3개 항목 (NOT NULL DEFAULT, `transfer_limit_request` 회원 기준 등) | 남음 |
| 팀3 | `loan_repayment_schedule` `loan_id` 누락 | 해결됨 |
| 팀3 | 예약·정기이체가 호출할 이체 메서드 시그니처, 대출금 입금 메서드 | 이체 구현 후 |
| 팀4 | 동결·해제 화면에서 호출할 메서드, 이체 완료 이벤트 | 이체 구현 후 |
| 팀4 | 동결(`FROZEN`) 기능을 만들 예정인지 | 확인 필요 |
