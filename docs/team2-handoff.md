# 팀원2 작업 인수인계 (2026-10-16 갱신)

> 새 세션에서 Claude에게 이렇게 요청하세요.
> **"docs/team2-handoff.md 와 docs/team2-progress.md 읽고 이어서 작업하자"**
>
> - 이 파일: 최근 작업 상태, 바로 다음에 할 일, 주의할 점
> - [team2-progress.md](team2-progress.md): 확정한 결정 사항(3장), 엔티티 메서드, 이체 설계. **10-15 기준이라 아래 1-1의 새 결정은 이 파일이 최신**
> - [schema-team2-dictionary.md](schema-team2-dictionary.md): 테이블 컬럼 설명

---

## 0. 시작하기 전에 확인할 것

1. 2026-10-16까지의 작업은 `account` 브랜치에 커밋·푸시했다 (main PR 전). 다른 PC에서 받기:
   ```
   git fetch origin
   git checkout account
   git pull origin account
   ```
   - 확인: `project/onbank/src/main/java/org/doit/onbank/account/service/AccountTransferService.java` 가 있으면 받아진 것
   - 커밋하지 않은 파일이 있으면 pull 이 막힌다. 먼저 `git status` 로 확인
2. PC마다 따로 있는 것 (Git에 없음)
   - `CLAUDE.local.md`: 개인 작업 원칙 (`.git/info/exclude`로 제외)
   - `application-local.properties`: 공용 DB(cafe24) 접속 정보
3. 집 PC에는 mysql 명령줄 도구가 있다 (`C:\Program Files\MySQL\MySQL Server 8.4\bin\mysql.exe`). Claude가 공용 DB를 **조회만** 할 수 있다. 데이터 변경(INSERT/UPDATE/DELETE)은 사용자가 워크벤치에서 직접 한다

## 1. 작업 규칙 (사용자 지시)

- **커밋·푸시는 사용자가 "커밋해 줘", "푸시해 줘"라고 할 때만 한다.** merge, reset 등 이력을 바꾸는 git 작업도 같다
- 다른 팀(팀1/3/4) 파일은 수정하지 않는다. 문제가 있으면 사용자에게 알리고, 팀에 보낼 문구를 제안한다
- 테이블 기준은 팀원2 데이터 사전 원본. 원본에 없는 컬럼은 추가하지 않는다 (예외: `account.password_fail_count`)
- **사용자가 만든 화면(템플릿)의 구성·컬럼·문구는 유지하고 기능만 붙인다.** 화면 구조를 바꿔야 하면 먼저 묻는다
- 확인한 사실, 추정, 제안을 구분해서 말한다. 실행하지 않은 것을 완료했다고 하지 않는다
- 사소한 선택은 판단해서 진행하되 가정을 밝히고, 결과를 크게 바꾸는 것만 질문한다

### 1-1. 2026-10-16에 새로 확정한 결정

| 항목 | 결정 |
|---|---|
| `account_type` 값 | `CHECKING` 입출금 / `DEPOSIT` 예금 / `SAVINGS` 적금 / `LOAN` 대출 (상수 클래스 `AccountType`) |
| `transfer_type` 값 | `IMMEDIATE` 즉시 / `SCHEDULED` 예약 / `RECURRING` 정기 (상수 클래스 `TransferType`). 예약·정기는 팀3이 호출할 때 사용 |
| 당행·타행 구분 | `financial_institution_id` NULL = 당행(ONBANK) |
| 이체한도 읽기 | 팀2 `AccountRepository.findTransferLimit()` 에서 `members` 두 컬럼을 SQL로 직접 조회 (팀1 `Member` 엔티티는 수정 안 함) |
| 한도 기본값 | `members` 한도가 NULL이면 **1일 5,000,000원 / 1회 1,000,000원** (`AccountTransferService.DEFAULT_*`) |
| OTP | 이체에서 건너뜀. 팀1 OTP가 나오면 연결 |
| 메인 링크 | `index.html` 퀵메뉴 "조회" → `/mybank/accounts`, "이체" → `/mybank/transfer` (팀3 "대출" 링크는 그대로) |
| 전계좌조회 화면 | 사용자 원본 `list.html` 구성 유지(입출금/예금·신탁/적금 3영역, 컬럼 5개). `account_type` NULL 계좌는 입출금으로 표시. `my-accounts.html`은 삭제 |

## 2. 현재 상태 한눈에

| 단계 | 상태 |
|---|---|
| 테이블·엔티티 6개 | 완료 (공용 DB 생성, validate 통과) |
| 입출금 (엔티티·Service·화면) | 완료, 브라우저 확인 완료 |
| **이체 (당행 즉시이체)** | **완료, 브라우저 확인 완료 (10-16)** |
| **전계좌조회 화면 (`list.html` 원본 구성)** | **완료, 브라우저 확인 완료 (10-16)** |
| 팀2 테스트 | **74개 통과** (DB 없이) + `contextLoads` 통과 |
| **계좌 개설** | **시작 전 ← 다음 할 일** (계획은 아래 3장) |
| 즐겨찾기 | 시작 전 (계획은 아래 4장) |
| DB 통합 테스트 | 방법 결정 필요 (progress 5장) |

## 3. 바로 다음에 할 일: 계좌 개설 (기획서 5-2장, 핵심)

### 3-1. 시작 전에 사용자에게 받을 결정

| # | 항목 | 제안 (아직 확정 아님) |
|---|---|---|
| 1 | 계좌번호 형식 | 고정 앞자리 + 난수 (예: `1002-XXX-XXXXXX`), 중복이면 재시도 (`account_number` UNIQUE) |
| 2 | 입출금 계좌 개설일 | `signup_date`에 저장 (개설일을 둘 다른 컬럼이 없음) |
| 3 | 반려된 대기 계좌 | `CLOSED` (상태 값 4개 결정과 같음) |
| 4 | 20영업일 내 1개 제한 | 핵심 끝난 뒤 여유 있으면 ([선택]) |
| 5 | 예금·적금 계좌 생성 | 팀3 상품 가입에서 팀2 메서드 호출. 시그니처 협의 필요 |

### 3-2. 흐름

```
[회원] /mybank/accounts/open → 입출금(ON 통장) + 계좌 비밀번호 4자리(확인 포함)
   ├─ members.verification_status = VERIFIED   → 즉시 개설 ACTIVE
   ├─ NONE / REJECTED → 신분증 정보·1원 인증 mock 입력 → 팀1 request() → 계좌 PENDING (계좌번호 발급)
   └─ PENDING(심사중) → 개설 불가 안내
[관리자] 승인 → 팀2 activatePending() → ACTIVE / 반려 → 팀2 rejectPending() → CLOSED
```

### 3-3. 단계

1. `AccountNumberGenerator` (규칙 생성 + 중복 재시도)
2. `Account` 메서드: 개설 초기값(잔액 0, `today_transfer_amount` 0, 오류 0), `activate()`, `reject()`, `freeze()`/`unfreeze()`, `close()`(잔액 0일 때만)
3. Service: `openChecking(...)` + 다른 팀이 호출할 `activatePending()`, `rejectPending()`, 동결/해제, 해지, 잠금 해제
4. 화면: `/mybank/accounts/open` 입력 → 결과("개설 완료" / "개설대기(심사중)")
5. 전계좌조회 빈 목록 아래에 [계좌 개설] 버튼
6. 테스트: 즉시 개설 / 대기 개설 / 심사중 거부 / BCrypt 저장 / 계좌번호 재시도

### 3-4. 팀1 본인확인 기능이 아직 없음 (10-16 확인)

- `VerificationService.request()/approve()`, `memberService.requireVerified()` 는 **코드에 없다**. `members.verification_status`와 `identity_verification` 테이블만 있다
- 진행 방법 (제안): 본인확인 여부는 `Member.verificationStatus`를 **읽기만** 한다. `request()` 호출은 팀2 쪽 작은 인터페이스(예: `IdentityVerificationClient`) + 아무것도 안 하는 임시 구현으로 두고, 팀1 기능이 나오면 교체
- 승인 화면이 없는 동안은 워크벤치에서 상태를 직접 바꿔 확인

## 4. 그 다음: 즐겨찾기 (기획서 4.5 + 5.6, 심화)

- `account_favorite` 테이블 하나로 "계좌 즐겨찾기"와 "이체 받는사람 즐겨찾기"를 처리
- 결정 필요 (제안): ① 회원 단위로 조회 (`account_id`는 등록 시 내 계좌, 조회는 내 모든 계좌의 즐겨찾기) ② 당행 `financial_institution_id` NULL ③ 예금주명은 가린 이름 저장 ④ 최대 20개
- 단계: Repository → Service(등록 시 `AccountTransferService.findRecipient()` 재사용, 중복·본인 확인) → `/mybank/favorites` 관리 화면 → 이체 화면 버튼 연결("⭐즐겨찾는이체정보", "자주쓰는 입금계좌", "최근입금계좌"(`account_transfer`에서 조회), "본인계좌") → 이체 결과 화면 [즐겨찾기 추가] → 테스트

## 5. 구현 구조 요약 (10-16 기준)

```
[화면] /mybank/** (SecurityConfig: ROLE_USER 로그인 필요)
  AccountPageController  /mybank/accounts
     GET  /mybank/accounts                    → account/list.html (전계좌조회: 입출금/예금·신탁/적금)
     GET  /mybank/accounts/{id}               → detail.html (거래내역 + 입출금 영수증)
     GET/POST /mybank/accounts/{id}/deposit   → deposit.html
     GET/POST /mybank/accounts/{id}/withdraw  → withdraw.html
  TransferPageController  /mybank/transfer
     GET  /mybank/transfer[?from={id}]        → transfer.html (사용자 목업 구성 유지 + 확인 단계)
     POST /mybank/transfer                    → 성공 시 redirect /mybank/transfer/result (PRG, flash "receipt")
     GET  /mybank/transfer/result             → transfer-result.html (영수증 없으면 입력 화면으로)
     GET  /mybank/transfer/recipient?accountNumber=  → JSON 받는 분 이름(가림, 예: 김*수)
     GET  /mybank/transfer/limit?accountId=          → JSON 1회/1일 한도, 오늘 이체액, 남은 한도
  AccountController (예전 목업 주소) /account/list, /account/transfer → 새 화면으로 redirect

[Service]
  AccountService          deposit / withdraw / getMyAccounts / getMyAccount / getMyHistories
  AccountTransferService  transfer / findRecipient / getLimit
     transfer 순서: 입금계좌 확인 → 같은 계좌 금지 → 1회 한도 → 비밀번호(REQUIRES_NEW)
                  → (OTP 건너뜀) → 두 계좌 ID 오름차순 잠금 → 본인 계좌 확인 → 1일 한도
                  → transferOut/transferIn → account_transfer 1행 + transaction_history 2행 (같은 거래번호)
     통장 표시 비우면: 내 통장 = 받는 분 이름, 받는 분 통장 = 내 이름
  AccountPasswordVerifier (REQUIRES_NEW), TransactionSqnoGenerator

[Entity] Account: deposit / withdraw / transferOut(오늘 이체 누적) / transferIn
                  / getTodayTransferAmountOn(date)(조회만) / touchTransaction / 비밀번호 오류 메서드
         상수: AccountStatus, AccountType, TransactionType, TransactionStatus, TransferType

[화면 스크립트] static/js/account.js (금액 버튼, 중복 제출 방지), static/js/transfer.js (잔액·오류횟수·한도 조회, 받는 분 확인, 확인 단계, Enter 제출 막기)
```

## 6. 주의할 점 (작업하다 알게 된 것)

- **팀2 DDL은 `project/onbank/src/main/resources/db/03_account_ddl.sql` 하나다** (팀원 형식: 01 팀1, 02 팀4). `docs/schema-team2-mysql.sql`은 10-16에 삭제했다. 테이블을 바꾸면 이 파일(맨 아래 변경 이력에 ALTER 기록) + 데이터 사전 + 엔티티를 함께 고친다
- **실행 순서는 01 → 03 → 02.** 번호는 03이지만 팀4 `02_admin_ddl.sql`의 FK가 팀2 테이블을 참조하므로 02보다 먼저 실행해야 한다
- **비밀번호 확인은 계좌 잠금보다 먼저.** 순서가 바뀌면 이체·출금 트랜잭션이 잡은 잠금을 REQUIRES_NEW 트랜잭션이 기다리며 멈춘다
- **`AccountPasswordVerifier.verify()`는 비밀번호가 틀리면 예외 대신 `false`.** 안에서 예외를 던지면 증가한 오류 횟수가 롤백된다
- **`ddl-auto=validate`는 모든 팀의 엔티티를 검사한다.** 다른 팀 테이블 하나만 어긋나도 서버 전체가 뜨지 않는다
- **`application-local.properties`는 팀 공용 DB(cafe24)를 가리킨다.** 테스트가 커밋하면 공용 DB에 남는다
- **목업 HTML의 `<button>`을 form 안에 넣을 때는 `type="button"` 필수.** 없으면 누를 때마다 form이 제출된다
- **화면 테스트(`@WebMvcTest`)에는 `@MockitoBean JpaMetamodelMappingContext`가 필요하다** (`@EnableJpaAuditing` 때문)
- Bash heredoc으로 Java 파일을 만들면 `"\\d"`가 `"\d"`로 바뀌어 컴파일 오류가 난다. 정규식이 있는 파일은 Write/Edit로 작성
- 일부 기존 파일은 줄바꿈이 CRLF라 sed/perl 여러 줄 치환이 안 먹는다. Edit 도구 사용
- 서버 코드를 바꾼 뒤에는 서버를 **재시작**해야 브라우저에 반영된다 (템플릿은 `thymeleaf.cache=false`라 새로고침만으로 반영되지만 Java는 아님)

## 7. 공용 DB 현재 데이터 (10-16 조회 기준)

- 회원: `user1`(member_id 1), `user2`, `user3`
- 테스트 계좌 (`user1` 소유, 비밀번호 1234): `TEST-0001` ACTIVE / `TEST-0002` FROZEN / `TEST-0003` PENDING. 모두 `account_type` NULL
- `members` 이체한도 컬럼: `user1`은 NULL → 기본 한도 적용
- 브라우저 확인으로 생긴 입금·출금·이체 거래가 남아 있다. 정리는 [data-team2-mysql.sql](data-team2-mysql.sql) 아래쪽 삭제 SQL
- `data-team2-mysql.sql` 15번째 줄 아이디는 각자 PC에서 바꿔 쓰고 **커밋하지 않는다**

## 8. 자주 쓰는 명령 (`project/onbank` 폴더에서)

```
./gradlew compileJava                                   # 컴파일
./gradlew test --tests "org.doit.onbank.account.*"      # 팀2 테스트 74개 (DB 불필요)
./gradlew test --tests "org.doit.onbank.OnbankApplicationTests"   # 서버가 뜨는지 (공용 DB 연결, 읽기만)
./gradlew bootRun                                       # 서버 실행 (포트 80), PowerShell 은 .\gradlew.bat bootRun
```

- 로그인 http://localhost/login → 전계좌조회 http://localhost/mybank/accounts → 이체 http://localhost/mybank/transfer

## 9. 팀에 전달·협의할 것

| 대상 | 내용 | 상태 |
|---|---|---|
| 팀1 | members 이체한도 컬럼 ALTER | 완료 |
| 팀1 | progress 5장 "팀1에 전달" 항목 (한도 NOT NULL DEFAULT, `transfer_limit_request` 회원 기준 등) | 남음 |
| 팀1 | **본인확인 `request()/approve()/requireVerified()` 제공 시기, approve 때 팀2 `activatePending(memberId)` 호출 요청** | 계좌 개설 시작 전에 |
| 팀1 | OTP 기능 제공 시기 (이체에서 지금은 건너뜀) | 남음 |
| 팀3 | 예약·정기이체가 호출할 이체 메서드 시그니처 (`TransferType.SCHEDULED/RECURRING`) | 남음 |
| 팀3 | 예금·적금 가입 시 계좌 생성 메서드, 대출금 입금(`AccountService.deposit()`) | 남음 |
| 팀4 | 동결·해제·잠금 해제 화면에서 호출할 메서드 | 계좌 개설 작업 때 만듦 |
| 팀4 | 이체 완료 이벤트 클래스 이름과 필드 (`AccountTransferService` 8번 자리) | 남음 |
| 팀4 | `02_admin_ddl.sql` 참조 테이블 이름 | 해결됨 (v0.7에서 `account`, `account_transfer`, `transaction_history`로 수정) |
