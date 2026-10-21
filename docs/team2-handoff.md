# 팀원2 작업 인수인계 (2026-10-21 오후 정리)

> 새 세션에서 Claude에게 이렇게 요청하세요.
> **"docs/team2-handoff.md 와 docs/team2-progress.md 읽고 이어서 작업하자"**
>
> - 이 파일: 최근 작업 상태, 바로 다음에 할 일, 주의할 점 (**최신**)
> - [team2-progress.md](team2-progress.md): 기본 결정 사항(3장), 엔티티 메서드, 이체 설계, 전체 할 일 목록. **10-16 이후 결정은 이 파일 1-1이 기준**
> - [schema-team2-dictionary.md](schema-team2-dictionary.md): 테이블 컬럼 설명
> - ~~team2-account-number-rule.md~~: 10-20 사용자가 삭제 (팀3 예적금이 main에 들어와 더 필요 없음)

---

## 0. 시작하기 전에 확인할 것

1. **브랜치 상태 (10-21)**
   - **main에 머지된 팀2 PR**: #33(10-16), #36(10-17), #53(10-19 이체 OTP·자산 리포트), #60(10-20 `f3bdcf2` 자산 리포트 대출 수정·enum 전환·이체 편의 기능), **#64(`9c7aead` 예약이체 + `3e4de02` merge 후 팀3 enum 비교·스케줄링 정리·문서)** → 아래 10-20 "푸시 전" 항목은 #64로 모두 main에 들어감
   - **10-21 정기이체(5.5) 커밋 → `origin/account` 푸시 완료** (사용자 지시). main PR은 아직 (사용자가 GitHub에서)
   - **(10-21 오후)** `git pull origin main`(952903c, #78까지) 후 전계좌조회·자산 리포트 예적금·대출 연동 커밋 → `origin/account` 푸시 (사용자 지시). main PR은 사용자가 GitHub에서. 그사이 `origin/main`이 d099dd2(#86)로 더 나아감 (다른 팀이 팀2 `AccountTransferService`, `TransactionHistoryService/Repository`를 수정함 → 다음 작업 전 `git pull origin main` 후 변경 내용 확인)
   - `git stash` 1개: `application-local.properties.example` 로컬 수정(OTP 안내 2줄 삭제)을 pull 전에 보관 (10-21). 필요 없으면 `git stash drop`
   - **10-20 로컬 `account`에만 있는 커밋 (푸시 전)**
     - `9c7aead` 예약이체 등록·조회·취소·자동 실행 (기획서 5.4)
     - merge 커밋: `origin/main`(#62까지)을 `account`에 합침 → 팀1 `MemberStatus.LOCKED`·`EmployeeStatus.LOCKED`, 팀3 예적금·자동납입·대출 상환 코드가 들어옴
   - **커밋하지 않은 파일 (10-20)**: merge 후 고친 팀3 문자열 비교 2곳(`LoanRepaymentService`, `SavingsProductDetailController`), `ScheduledTransferScheduler`(`@EnableScheduling` 제거), 문서 3개(이 파일, progress). 사용자가 지시하면 커밋 → 푸시 → main PR (사용자가 GitHub에서. 이 PC에는 `gh` 없음)
   - `application-local.properties.example`은 10-20 현재 OTP 안내 2줄이 지워진 상태로 수정돼 있다 → **커밋하지 않는다** (팀1 견본 파일. 되돌릴지는 사용자 결정)
   - 다른 PC에서 받기:
     ```
     git fetch origin
     git checkout account
     git pull origin account
     ```
   - 받아졌는지 확인: `project/onbank/src/main/java/org/doit/onbank/account/service/ScheduledTransferService.java` 가 있으면 10-20 작업까지 받아진 것
   - 커밋하지 않은 파일이 있으면 pull 이 막힌다. 먼저 `git status` 로 확인 (`docs/data-team2-mysql.sql` 아이디 줄은 PC마다 다르므로 커밋하지 않는다)
   - PR이 머지된 뒤 새 작업을 시작할 때는 `git pull origin main`으로 `account`를 main과 맞춘다
2. PC마다 따로 있는 것 (Git에 없음)
   - `CLAUDE.local.md`: 개인 작업 원칙 (`.git/info/exclude`로 제외)
   - `application-local.properties`: 공용 DB(cafe24) 접속 정보 + **`onbank.otp.seed-key=팀1 공유 키`** (없으면 OTP 발급·검증 시 "OTP 암호화 키가 설정되지 않았습니다" 오류. **10-20 이 PC에 입력함**. 한 줄에 `onbank.otp.seed-key=`가 두 번 들어가지 않게 주의). 선택: `onbank.scheduled-transfer.enabled=false`로 이 PC의 예약이체 스케줄러만 끌 수 있다
   - 키 같은 비밀값은 `application.properties`나 `.example`(Git에 올라감)에 넣지 않는다
   - `docs/private/team2-portfolio-log.md`: 포트폴리오 정리 누적 파일 (`.git/info/exclude`로 제외)
3. 집 PC에는 mysql 명령줄 도구가 있다 (`C:\Program Files\MySQL\MySQL Server 8.4\bin\mysql.exe`). Claude가 공용 DB를 **조회만** 할 수 있다. 데이터 변경(INSERT/UPDATE/DELETE)은 사용자가 워크벤치에서 직접 한다

## 1. 작업 규칙 (사용자 지시)

- **커밋·푸시는 사용자가 "커밋해 줘", "푸시해 줘"라고 할 때만 한다.** merge, reset 등 이력을 바꾸는 git 작업도 같다
- 다른 팀(팀1/3/4) 파일은 수정하지 않는다. 문제가 있으면 사용자에게 알리고, 팀에 보낼 문구를 제안한다
- 테이블 기준은 팀원2 데이터 사전 원본. 원본에 없는 컬럼은 추가하지 않는다 (예외: `account.password_fail_count`)
- **사용자가 만든 화면(템플릿)의 구성·컬럼·문구는 유지하고 기능만 붙인다.** 화면 구조를 바꿔야 하면 먼저 묻는다
- 확인한 사실, 추정, 제안을 구분해서 말한다. 실행하지 않은 것을 완료했다고 하지 않는다
- 사소한 선택은 판단해서 진행하되 가정을 밝히고, 결과를 크게 바꾸는 것만 질문한다
- **(10-16 추가) 기획서 [심화]·[선택] 기능은 판단해서 바로 구현하지 않는다.** 범위·결정할 것·작업량을 짧게 정리해 구현할지 먼저 묻는다. 구현하기로 한 기능도 화면·버튼 추가처럼 범위를 넓힐 때는 다시 묻는다
- `index.html`, `index.css`, `SecurityConfig` 등 공용 파일을 바꿀 때는 사용자에게 알리고, 커밋 전에 다른 팀 변경과 겹치는지 확인한다
- **(10-20 추가) 수정 보고 방식**: 파일을 고치거나 새로 만들면 "완료했습니다"로 끝내지 않는다. **파일마다 무엇을 / 어떻게(비포·애프터 코드) / 왜** 고쳤는지 쓰고, 동작 흐름(입력 → 처리 → 결과)과 확인 방법까지 설명한다. 표로 요약만 하지 않는다. 사용자가 전체 코드(3천 줄 이상)를 직접 이해하고 발표·유지보수할 수 있게 하기 위함
- **(10-17 추가) 포트폴리오 정리**: 작업이 끝난 뒤 사용자가 **"포트폴리오 정리해 줘"**(또는 "포폴 정리해 줘")라고 하면, 그 작업에서 발표·포트폴리오에 쓸 트러블슈팅·설계 결정을 `[분류] 제목 / 한 줄 요약 / 배경 → 문제 → 원인 → 선택지(표) → 해결 → 결과 → 배운 점 / 관련 파일·커밋` 형식으로 정리해 **답변에 복사할 수 있는 텍스트로 바로 제공**하고, 같은 내용을 Git 제외 폴더 `docs/private/team2-portfolio-log.md`에 이어서 추가한다 (`.git/info/exclude`로 제외되어 이 PC에만 있다). 확인한 사실과 수치만 쓰고 사용자의 결정은 표시하며, **이 내용은 저장소에 커밋하지 않는다** (사용자가 개인 저장소에 따로 보관)

### 1-1. 2026-10-16 이후 확정한 결정

| 항목 | 결정 |
|---|---|
| `account_type` 값 | `CHECKING` 입출금 / `DEPOSIT` 예금 / `SAVINGS` 적금 / `LOAN` 대출 (**10-20부터 enum `AccountType`**, `@Enumerated(STRING)`이라 DB 값은 같음. 비교는 `==`) |
| `transfer_type` 값 | `IMMEDIATE` 즉시 / `SCHEDULED` 예약 / `RECURRING` 정기 (상수 클래스 `TransferType`). 예약·정기는 팀2 예약·정기이체 실행에서 사용 (10-20 팀2 담당으로 되돌림) |
| 당행·타행 구분 | `financial_institution_id` NULL = 당행(ONBANK) |
| 이체한도 읽기 | 팀2 `AccountRepository.findTransferLimit()` 에서 `members` 두 컬럼을 SQL로 직접 조회 (팀1 `Member` 엔티티는 수정 안 함) |
| 한도 기본값 | `members` 한도가 NULL이면 **1일 10,000,000원 / 1회 3,000,000원** (`AccountTransferService.DEFAULT_*`, **10-20 사용자가 500만/100만에서 올림**). 테스트는 숫자 대신 이 상수로 계산하게 바꿈 |
| OTP | 10-19 이체에 연결 완료 (아래 "이체 인증") |
| 메인 링크 | `index.html` 퀵메뉴 "조회" → `/mybank/accounts`, "이체" → `/mybank/transfer` (팀3 "대출" 링크는 그대로) |
| 전계좌조회 화면 | 사용자 원본 `list.html` 구성 유지(입출금/예금·신탁/적금 3영역, 컬럼 5개). `account_type` NULL 계좌는 입출금으로 표시. `my-accounts.html`은 삭제 |
| 계좌번호 형식 | 유형별 앞자리 `앞자리4-난수3-난수6`. 입출금 `1002` / 예금 `2002` / 적금 `3002` / 대출 `4002` (`AccountNumberGenerator`, 중복이면 5회까지 재시도) |
| 입출금 계좌 개설일 | `signup_date`에 저장 |
| 본인확인 | 팀1 기능이 나올 때까지 **본인확인 없이 즉시 개설(ACTIVE)**. PENDING 흐름은 팀1 기능이 나온 뒤 붙임 |
| 반려된 개설대기 계좌 | `CLOSED` |
| 계좌 개설 진입점 | 전계좌조회 입출금 **빈 목록에만** [계좌 개설] (계좌가 있으면 안 보임, 추가 버튼 두지 않음) + `index.html` 퀵메뉴 "빠른메뉴 >" 펼침 목록의 [계좌 개설] |
| 예적금 계좌 생성 | 팀3 상품 가입에서 만든다. 방식(팀2 메서드 호출 / 팀3이 직접 생성)은 팀3 기능이 나온 뒤 정함 |
| 본인확인·승인 흐름 | 팀원 기능이 구현되면 그때 합칠지 검토 (10-16 사용자 결정). 지금은 손대지 않음 |
| 즐겨찾기 | 회원 단위 조회, 당행 `financial_institution_id` NULL, 예금주명은 가린 이름 저장, 최대 20개, 중복 등록 불가. **본인 계좌도 등록 가능** (와이어프레임 9번 "본인 계좌와 타인 계좌 모두 등록 가능"). 등록·수정(별칭·메모)·삭제·순서 이동 |
| 즐겨찾기 진입점 | 전계좌조회 예금명 옆 ☆/★ (사용자 요청), 이체 완료 화면 [⭐ 즐겨찾기 추가] (받는 분 계좌, 본인 계좌 포함), 이체 화면 선택 창 "즐겨찾기 관리 ›", 메인 "빠른메뉴 >" 펼침 목록 [즐겨찾기 목록 조회] → `/mybank/favorites` |
| 심화 기능 진행 방식 | 구현 전에 사용자에게 먼저 묻는다 (1장 작업 규칙) |
| **(10-17) 내 계좌 / 상대계좌 즐겨찾기 구분** | 컬럼은 추가하지 않는다. 즐겨찾기 계좌번호가 **내 계좌 번호 중 하나면 내 계좌, 아니면 상대계좌** (`AccountFavoriteService.getMyFavoriteGroups`). 내 계좌 즐겨찾기는 전계좌조회 ☆로 등록 |
| **(10-17) 이체 화면 즐겨찾기 버튼 2개** | 출금계좌번호 옆 [내 계좌 즐겨찾기] → 출금계좌만 바꿈 (출금 가능한 계좌만 표시) / 입금계좌 [상대계좌 즐겨찾기] (원래 "자주쓰는 입금계좌") → 입금계좌만 채움. "⭐ 즐겨찾는이체정보"도 상대계좌 즐겨찾기만 보여 줌 (내 계좌로 보낼 때는 [본인계좌]) |
| **(10-17) 출금계좌 기본 선택** | 출금계좌 목록에서 즐겨찾기한 내 계좌를 즐겨찾기 순서대로 맨 위에 둔다 → `?from=` 없이 들어오면 첫 번째 즐겨찾기 계좌가 선택됨 |
| **(10-19) 이체 인증** | 기준 금액 **10만 원 이하 = 계좌 비밀번호만**, **초과 = 계좌 비밀번호 + OTP 6자리**. ONBANK 본인 계좌끼리는 금액과 관계없이 OTP 생략. 기준 금액은 팀2 상수 `AccountTransferService.OTP_REQUIRED_AMOUNT` |
| **(10-19) PIN** | PIN 6자리는 만들지 않는다. 기존 계좌 비밀번호 4자리(BCrypt, 5회 잠금)가 간편 인증 역할 (auth.md "간편비밀번호 미구현" 결정과 맞춤) |
| **(10-19) OTP 미등록 회원** | 전부 막지 않는다. 기준 금액 이하는 이체 가능, 초과는 "OTP 등록이 필요해요" + [OTP 등록하기](`/mybank/security/otp`). OTP 발급·검증·오류 10회 정지는 팀1 `OtpService` 그대로 사용 (팀2는 호출만) |
| **(10-19) 이체 화면** | 사용자 목업 구성 유지, 필요한 것만 추가: 한글 금액, 금액 입력 즉시 안내(잔액 초과 / 1회 한도 초과 / OTP 미등록), 입금계좌번호·금액 입력 전 [확인] 비활성, 확인 영역에 OTP 입력칸(필요할 때만) |
| **(10-20) 계좌 상태·유형** | `AccountStatus`, `AccountType`을 String 상수 → **enum**으로 (사용자 결정). DB 값은 그대로. `AccountStatus`는 `label`·입금/출금 가능 여부를 가진다. **비교는 `==`. `"CHECKING".equals(enum)`은 컴파일되지만 항상 false** (7장) |
| **(10-20) 계좌번호 입력** | 이체·즐겨찾기 화면에서 숫자만 치면 4-3-6 하이픈 자동 입력 (`account.js` `.js-account-number`). 서버도 13자리 숫자면 하이픈을 넣어 찾는다 (`AccountNumberGenerator.normalize`) |
| **(10-20) OTP 등록 후 이체 복귀** | OTP 화면(팀1 `security/otp.html`) 사용중 상태에 [이체 화면으로 돌아가기]. [OTP 등록하기]를 누를 때 이체 입력값을 `sessionStorage`에 저장(비밀번호·OTP 제외, 30분, 한 번 복원하면 삭제)했다가 복원 |
| **(10-20) 자산 리포트 저장** | 이번 달만 매번 갱신, **지난달은 저장된 행을 그대로** (지난 기록은 수정하지 않음, 사용자 결정). 대출 상환은 출금에 포함, 대출금 지급은 입금에서 제외 |
| **(10-20) 예약·정기이체 담당** | **팀2** (기획서 원래 배정으로 되돌림) |
| **(10-20) 예약이체 설계** | 테이블 `scheduled_transfer` 하나로 예약(`SCHEDULED`)·정기(`RECURRING`). 실행 일시는 **날짜+시각(분 단위)**. **등록할 때 비밀번호(·OTP) 확인**, 실행은 인증 없이 `transferPreAuthorized()`(잔액·한도·계좌 상태는 실행 시점에 다시 검사). 1년 이내, 출금·입금·금액·일시가 같은 대기 건은 중복 등록 불가 |
| **(10-20) 예약이체 화면** | 등록은 **이체 화면** [예약이체일지정] (사용자 선택 A). 조회·취소는 `/mybank/transfer/scheduled`. 진입: 전계좌조회 상단 큰 버튼(사용자 선택 B), 이체 화면 버튼, 등록 직후 이동. 시연용 [지금 실행] |
| **(10-20) 예약이체 실행** | 스케줄러 매분 0초. 성공은 이체+완료 표시를 **한 트랜잭션**, 실패는 롤백 뒤 **새 트랜잭션**에서 사유 기록. 행 잠금 후 상태를 다시 봐서 여러 서버가 동시에 돌아도 중복 이체 없음. 서버가 꺼져 있던 동안의 건은 켜진 뒤 첫 분에 실행 |
| **(10-20) 버튼 크기** | 자주 가는 화면 버튼은 `btn-sm`이 작아서 `.btn-quick`(16px, 굵게)을 새로 둠 (사용자 요청) |
| **(10-21) 정기이체 등록 위치** | 이체 화면 이체실행일자 줄을 **[즉시] [예약] [정기] 라디오**로 (사용자 선택). 정기 = 매월 N일(1~31) 시:분 + 종료일(선택, 비우면 해지할 때까지). 기존 [예약이체일지정] 버튼은 [예약] 라디오로 바뀜 |
| **(10-21) 정기이체 첫 회차** | 지금 이후 가장 가까운 "매월 N일 시:분". 그 달에 없는 날이면 말일 (31일 → 11월 30일, 12월은 다시 31일) |
| **(10-21) 정기 회차 실패** | 그 회차만 사유 기록(`실패 : 사유`), 상태는 대기 유지, 다음 달 다시 실행. 재시도 없음 (사용자 선택). 종료일을 넘으면 완료 |
| **(10-21) 정기이체 관리** | 목록에서 **[해지]만** (사용자 선택. 중지·재개·수정 없음, `PAUSED`는 쓰지 않음). 시연용 [지금 실행]은 예약이체와 같이 둠 (다음 회차를 앞당겨 실행 → 그다음 달로) |
| **(10-21) 화면 이름** | 기획서 용어 그대로 **"정기이체"** (사용자: 팀3 `/savings/auto-transfers` "정기이체 현황"과 따로 작업하므로 겹쳐도 됨). 팀2 목록 화면 이름은 "예약·정기이체 조회·취소" |
| **(10-21) 밀린 회차** | 서버가 몇 달 꺼져 있었으면 켜진 뒤 1회만 실행하고 지금 이후 회차로 넘어간다 (밀린 달을 몰아서 이체하지 않음, 판단해서 정함) |

## 2. 작업 요약

### 2-000000. 2026-10-21 오후 : 전계좌조회·자산 리포트에 팀3 예적금·대출 연동

**확인한 사실**: 팀3은 예적금을 `account`(DEPOSIT/SAVINGS)가 아니라 **`savings_account` + `savings_product`**에 저장한다 (공용 DB `account`에 예금·적금 계좌 0건, `product_subscription` 0건). 그래서 전계좌조회 예금/적금 영역과 자산 리포트 예적금이 항상 비어 있었다. 대출은 `loan_account`

| 파일 | 내용 |
|---|---|
| `AccountPageController` | 예적금: `SavingsAccountRepository.findByMemberIdOrderByIdDesc` → 진행 중(ACTIVE)만, 상품 유형으로 예금/적금 분리, 원금 합계 (**for문·합계는 사용자가 직접 작성**). 대출: `AssetReportRepository.findActiveLoans` 재사용 (실패하면 `loanError` 안내, 화면은 열림). 연결계좌 번호(`accounts`로 만든 ID→번호 Map), 최종거래일(`findSavingsLastPaidOn`). `depositAccounts`/`savingsAccounts`/`ofType()` 삭제 (account 에 예적금이 생기지 않으므로) |
| `list.html` | 예금/적금 영역을 팀3 데이터로: 예금명(상품명 + 금리·만기, 적금은 월 납입액) / 계좌번호 칸 = **연결계좌 번호 + "연결계좌"** / 최종거래일 = `savings_payment` 마지막 납입일(없으면 가입일) / 잔액 = 원금(적금은 넣은 금액) / [조회] `/savings/{id}`. 빈 목록 [가입하기] → `/savings/products`. **대출 영역 추가** (대출명·금리 / 대출 계좌번호 / 만기일 / 대출잔액 / [상환] `/loan/repayment?accountId=`, 빈 목록 [대출 알아보기] `/loan`). 표 칸 5개 구성은 유지 |
| `AssetReportRepository` | `findActiveSubscriptions` : `product_subscription` → `savings_account`+`savings_product` (ACTIVE, 예상 이자 NULL). `findActiveLoans`에 `LOAN_ACCOUNT_NUMBER` 추가. `findSavingsLastPaidOn` 새로 (예적금별 `MAX(paid_on)`) |
| `AssetReportService` | 자산 구성(도넛)·총자산에 예적금 원금 포함 (가입 때 입출금 계좌에서 빠져나가므로 안 더하면 총자산이 줄어 보임, user1 약 1,015만 원). 유형 라벨 `FIXED_DEPOSIT`/`INSTALLMENT_SAVINGS`, 적금 금액 라벨 "납입 금액" |
| `LoanSummary`, `SubscriptionSummary`, 새 `SavingsLastPaid` | 조회 결과 인터페이스 |
| 테스트 | 전계좌조회 예적금·대출 표시, 대출 조회 실패 시에도 열림, 자산 구성에 예적금 포함, 정기이체 통장 표시내용 전달. **팀2 233개 통과** |

- `assets.html`은 예적금·대출 영역이 이미 있어 고치지 않음. `git status`의 `assets.html` 변경은 IDE 자동 정렬(기능 변화 없음) → **커밋에서 뺌**
- 팀3 파일은 수정하지 않음 (Repository·SQL 조회만)

#### 2-000000-1. 같은 날 확인·답변한 것

| 항목 | 결과 |
|---|---|
| 이체한도 | 회원별로 `members` 두 컬럼을 이미 읽고 있다 (NULL일 때만 상수 기본값). 공용 DB 회원 5명 모두 NULL이라 모두 같은 한도로 보였던 것. 회원별 UPDATE SQL은 사용자에게 제공(실행 여부는 사용자). 실제 은행 기준(보안매체 등급, 한도제한계좌, 고객 신청)과 "OTP 등록 여부로 기본 한도 나누기", 13.5 한도 변경 신청 흐름은 **제안만 하고 구현 안 함** (사용자 결정 대기) |
| 이메일 인증 오류 | `Name for argument of type [java.lang.String] not specified` : 이클립스가 `-parameters` 없이 컴파일 (Gradle은 자동). `project/onbank/.settings/org.eclipse.jdt.core.prefs`에 `codegen.methodParameters=generate` 추가 (Git 제외 파일) → Project Clean + 서버 재시작 필요. 팀원 중 이클립스 사용자도 같은 설정 필요 (7장) |
| 정기이체 통장 표시내용 | 브라우저(헤드리스 크롬, 즉시/예약/정기)와 서버(테스트)에서 **정상 전송·저장 확인, 재현 안 됨**. 16:50 예약 1건은 표시내용이 NULL로 저장됐고, 비어 있으면 설계대로 예금주명이 찍힌다 (내 통장 = 받는 분 이름, 받는 분 통장 = 내 이름). 사용자가 다시 재현하면 누른 순서 받기 |
| 예적금 연결계좌 문제점 | 연결계좌 해지(팀2 `close()`는 잔액만 검사) / 동결(팀3 `OnbankAccountMoneyAdapter`는 ACTIVE만 허용) 시 납입·만기 지급 불가. 대안 A(해지 전 검사) / B(예적금 고유 번호) / C(account 에 계좌 생성) 비교 → **A 필수, 여유 있으면 B 제안**. 팀3 전달 문구 작성 (10장) |

### 2-00000. 2026-10-21 한 일 : 정기이체 (기획서 5.5)

| 파일 | 내용 |
|---|---|
| `entity/ScheduledTransfer` | `subscribe()`(정기 등록), `nextOccurrence()`(회차 계산, 말일 처리), `isRecurring()`, `isLastSucceeded()`. `markSucceeded/markFailed`가 정기면 상태를 대기로 두고 `moveToNextRound()`로 다음 회차 (종료일 넘으면 완료) |
| `service/ScheduledTransferService` | `subscribe()` 추가. 예약·정기 공통 검사(계좌 상태 → 중복 → 인증)를 `checkAndAuthorize()`로 묶음. `recordFailure(..., force)` : 스케줄러 실행이면 `isDue`일 때만 기록 (다른 서버가 이 회차를 성공시켜 다음 달로 넘어갔으면 한 달을 더 건너뛰지 않게) |
| `service/ScheduledTransferRunner` | `recordFailure`에 `force` 전달, 문구 "대기 중인 이체만" |
| `controller/TransferPageController` | `form.isRecurring()`이면 `subscribe()` → 목록으로, 문구 "정기이체를 등록했습니다. 매월 25일 50,000원이 이체되며, 첫 이체는 2026.10.25 09:00입니다." |
| `controller/ScheduledTransferPageController` | 해지 문구 "정기이체를 해지했습니다.", [지금 실행] 성공 판단을 상태 대신 `isLastSucceeded()` |
| `dto/TransferForm` | `transferDay`(1~31), `recurringTime`(HH:mm), `endDate`, `isRecurring()` |
| `transfer.html`, `transfer.js` | 라디오 [즉시][예약][정기] → `setScheduleMode('now'/'scheduled'/'recurring')`. 정기 기본값 매월 오늘 날짜 09:00. 확인 영역 "매월 25일 09:00 (첫 이체 2026.10.25, 해지할 때까지)", 버튼 [정기이체등록]. 종료일 검사, OTP 등록 복귀 시 정기 입력값도 복원 |
| `scheduled-transfers.html` | 제목 "예약·정기이체 조회·취소", 실행 일시 칸에 [예약]/[정기] 표시, 정기는 "매월 N일 시:분 / 다음 yyyy.MM.dd / ~ 종료일", 대기 상태를 "진행중"으로, 버튼 [해지], 안내 3줄 추가 |
| `list.html` | 상단 버튼 "📅 예약·정기이체 조회" |
| `account.css` | `.schedule-mode`(라디오), `.schedule-kind`(예약/정기 표시) |
| 테스트 | 엔티티 13개, Service 6개, 화면 7개 추가. 기존 `recordFailure` 호출·문구 수정. **팀2 230개 / 전체 354개 통과** (10-21) |

- 테이블·DDL·데이터 사전은 바꾸지 않음 (10-20에 `transfer_day`, `end_date` 준비돼 있었음). 정기이체의 시각은 `next_execute_at`의 시각을 그대로 쓴다
- **(10-21) 파비콘 추가 (공용, 사용자 요청)**: `static/favicon.ico` (남색 둥근 사각형 + 흰 "ON", 16·32·48px). 브라우저가 `/favicon.ico`를 자동 요청하므로 템플릿은 수정하지 않음. 안 보이면 브라우저 캐시 → 시크릿 창으로 확인. 팀에 "파비콘 넣었다" 알리기

#### 2-00000-1. 브라우저 확인할 것 (서버 재시작 후)

1. 이체 화면 이체실행일자 : [즉시] 선택 상태로 열림 → [예약] 누르면 기존처럼 날짜·시각 → [정기] 누르면 "매월 (오늘 날짜)일 09:00, 종료일" 칸
2. **정기 등록**: [정기] → 매월 오늘 날짜, 시각을 2~3분 뒤로 → [확인] → 확인 영역 "매월 21일 HH:mm (첫 이체 2026.10.21, 해지할 때까지)", 버튼 [정기이체등록] → 목록 "정기 / 매월 21일 HH:mm / 다음 2026.10.21 / 해지할 때까지 / 진행중"
3. **자동 실행**: 시각이 지나고 새로고침 → 결과 "이체 완료", 다음 "2026.11.21", 상태는 진행중 그대로, 잔액 감소
4. **[지금 실행]**: 다음 회차가 한 달 뒤로 넘어가는지 (예: 다음 11.21 → 12.21)
5. **실패**: 잔액보다 큰 금액 정기 등록 → [지금 실행] → "정기이체를 실행하지 못했습니다. 실패 : 잔액이 부족합니다.", 상태 진행중, 다음 회차는 다음 달
6. **종료일**: 종료일을 첫 회차보다 앞으로 → "종료일은 첫 이체일(...) 이후로 선택하세요." / 종료일 = 첫 회차 날짜로 등록 → [지금 실행] 한 번 → 상태 "완료"
7. **31일**: 매월 31일 선택 → 확인 영역 첫 이체가 이번 달/다음 달 말일로 나오는지 (10월은 31일, 11월이면 30일)
8. **해지**: [해지] → 확인 창 → "정기이체를 해지했습니다.", 상태 "취소", 버튼 사라짐
9. 10만 원 초과 타인 계좌 정기 등록 → OTP 칸, [OTP 등록하기] 갔다 오면 정기 입력값 복원
10. 확인으로 생긴 정기이체는 매월 계속 실행되므로 **확인 후 꼭 [해지]** (또는 `DELETE FROM scheduled_transfer WHERE member_id = 회원ID`)

### 2-0000. 2026-10-20 한 일

| 작업 | 내용 | 확인 |
|---|---|---|
| 자산 리포트 대출 수정 | 대출 잔액을 `loan_account.REMAINING_BALANCE`로, 월 상환액을 `account_loan_transaction`으로 (팀3가 대출 실행 기능을 넣으며 구조가 바뀜). 상환액은 출금에 포함. 지난달은 저장값 유지, 같은 달 중복 행은 id 큰 행 | 테스트, SQL 공용 DB 조회 확인 (user1 총자산 37,204,850 / 대출 잔액 36,918,183) |
| enum 전환 | `AccountStatus`, `AccountType` → enum. 팀2 코드·화면·테스트, 팀3 문자열 비교(대출 신청·실행·지급, merge 후 대출 상환·예적금 상세)까지 수정 | 전체 테스트 |
| 이체 편의 | 계좌번호 하이픈 자동 입력 + 서버 보정, OTP 화면 [이체 화면으로 돌아가기], OTP 등록 후 이체 입력값 복원 | 테스트, **브라우저 확인 전** |
| **예약이체 (5.4)** | 1단계 테이블·엔티티 → 2단계 `transfer()`를 `authorize()`/`execute()`로 분리 + `transferPreAuthorized()` → 3단계 등록·조회·취소 화면 → 4단계 스케줄러·[지금 실행] (2-0000-1) | 테스트, **브라우저 확인 전** |
| 바로가기 버튼 | 전계좌조회 상단 [이체하기] [📅 예약이체 조회·취소] (`.btn-quick`) | **브라우저 확인 전** |
| main 합치기 | `origin/main`(#62까지) merge. 팀1 `LOCKED` enum이 없어 나던 `No enum constant ...MemberStatus.LOCKED`가 해결됨 (DB는 고치지 않음) | 전체 **328개 통과** |
| 보고 규칙 | 1장 "수정 보고 방식" 추가 | |

#### 2-0000-1. 예약이체 구조

```
[등록] 이체 화면 [예약이체일지정] → 날짜·시각 입력 → [확인] → [예약등록]
       POST /mybank/transfer (executeDate, executeTime 있으면 예약)
       → ScheduledTransferService.reserve()
           ① 일시 : 분 단위, 지금 이후 ~ 365일
           ② 출금계좌 : 본인 것, 출금 가능 상태 (동결이면 거절)
           ③ 중복 : 같은 출금·입금·금액·일시의 대기 건
           ④ AccountTransferService.authorize() : 금액·받는 계좌·1회 한도·비밀번호·OTP (즉시이체와 같은 코드)
           ⑤ 저장 (WAITING)
       ①~③을 ④보다 먼저 : 어차피 안 되는 요청으로 비밀번호 오류 횟수·OTP가 소모되지 않게
[조회] GET /mybank/transfer/scheduled  : 목록 (대기 건에만 [지금 실행] [취소])
[취소] POST /mybank/transfer/scheduled/{id}/cancel : 본인 것 + 대기·중지만
[실행] ScheduledTransferScheduler (매분 0초) → ScheduledTransferRunner.runDue()   ← 트랜잭션 없음, 1건씩
         → ScheduledTransferService.execute(id)   ← 트랜잭션 : 행 잠금 → 다시 확인 → transferPreAuthorized → 완료
         → 실패하면 예외로 전체 롤백 → recordFailure(id, 사유)   ← 새 트랜잭션 : 대기일 때만 실패 기록
       [지금 실행] POST /{id}/run → runner.runNow() → 같은 execute(force=true)
```

- 새 파일: `entity/ScheduledTransfer`, `ScheduleType`, `ScheduledTransferStatus`, `repository/ScheduledTransferRepository`, `service/ScheduledTransferService`, `ScheduledTransferRunner`, `scheduler/ScheduledTransferScheduler`, `controller/ScheduledTransferPageController`, `templates/account/scheduled-transfers.html`, 테스트 4개 (`ScheduledTransferTests`, `ScheduledTransferServiceTests`, `ScheduledTransferRunnerTests`, `ScheduledTransferPageControllerTests`)
- 수정: `AccountTransferService`, `TransferForm`(`executeDate`, `executeTime`), `TransferPageController`, `transfer.html`, `transfer.js`, `account.js`(`data-confirm`), `account.css`(`.btn-quick`), `list.html`, `03_account_ddl.sql`, 데이터 사전
- 공용 DB에 `scheduled_transfer` 생성 완료 (10-20 사용자, `OnbankApplicationTests` validate 통과)
- 정기이체(5.5)는 **다음 작업**: 테이블·`transferDay`·`endDate`·`PAUSED`는 이미 준비. 정할 것은 2-3 참고

#### 2-0000-2. 브라우저 확인할 것 (서버 재시작 후)

1. **예약 자동 실행**: 이체 화면 이체실행일자 [예약] (10-21부터 버튼 대신 라디오) → 기본값(지금+10분, 10분 단위)이 들어가는지 → 2~3분 뒤로 바꿔 [확인] → 확인 영역 "이체실행일자: 2026.10.20 15:40 예약", 버튼 [예약등록] → 목록 "대기" → 시각이 지나고 새로고침 → "완료 / 이체 완료 / 10.20 15:40 실행", 잔액 감소, 서버 콘솔 `예약이체 1건 실행`
2. **[지금 실행]**: 내일로 예약 → [지금 실행] → 확인 창 → "50,000원을 이체했습니다", 상태 완료
3. **실패**: 잔액보다 큰 금액 예약 → [지금 실행] → "실행하지 못했습니다. 잔액이 부족합니다.", 빨간 "실패", 잔액 그대로
4. **중복·취소**: 같은 내용 다시 등록 → "같은 내용의 예약이체가 이미 등록되어 있습니다." / [취소] → 확인 창에서 취소를 누르면 그대로, 확인을 누르면 "취소"
5. **10만 원 초과 타인 계좌 예약** → 확인 영역에 OTP 칸, 등록할 때 OTP 검증
6. **등록 실패 후 다시 그린 화면**(예: 비밀번호 틀림) → 예약 모드가 열린 채 일시가 남아 있는지
7. 전계좌조회 상단 큰 버튼 2개, 이체 화면 [📅 예약이체 조회·취소], 계좌번호 하이픈 자동 입력, OTP 등록 후 [이체 화면으로 돌아가기] → 입력값 복원
8. 확인으로 생긴 예약은 공용 DB `scheduled_transfer`에 남는다 (정리: `DELETE FROM scheduled_transfer WHERE member_id = 회원ID`)

### 2-00. 2026-10-19 한 일

| 작업 | 내용 | 확인 |
|---|---|---|
| 10-17 작업 main 머지 | PR #36 머지 확인. `account` = `origin/main` | |
| 즐겨찾기 브라우저 확인 | 사용자 확인 완료 | 완료 |
| **이체 OTP 연결** | 1-1 (10-19) 결정대로 구현. 순서: 입력 검증 → (OTP 필요 시) 등록 여부·번호 형식 확인 → 계좌 비밀번호(REQUIRES_NEW) → `otpService.verify()`(팀1, 오류 횟수는 팀1이 별도 트랜잭션) → 계좌 잠금 | 테스트 통과, **브라우저 확인 전** |
| 테스트 | 팀2 **158개 통과** (146 → 158) + `contextLoads` 통과 | |

- 수정: `AccountTransferService`(`OTP_REQUIRED_AMOUNT`, `transfer(...)`에 `otpCode` 인자 추가, `requiresOtp`, `isOtpReady`), `TransferForm`(`otpCode`), `TransferPageController`, `transfer.html`, `transfer.js`, `account.js`(금액 버튼이 `input` 이벤트를 보냄), `account.css`, `AccountTransferServiceTests`, `TransferPageControllerTests`
- 새 파일: `service/OtpRequiredException`
- **`transfer()` 시그니처가 바뀜**: `transfer(memberId, fromAccountId, rawPassword, otpCode, toAccountNumber, amount, senderMemo, receiverMemo)`. (예약·정기이체는 10-20부터 팀2 담당이라 팀3 전달 불필요)
- 브라우저 확인할 것 (서버 재시작 후)
  - 10만 원 이하 타인 이체 → OTP 칸 없이 이체
  - OTP 없는 회원: 금액 10만 1원 이상 입력 → 금액 아래 "100,000원을 넘는 이체는 OTP 등록이 필요해요" + [OTP 등록하기], [확인] 누르면 확인 단계로 가지 않음
  - 보안설정에서 OTP 발급·사용 등록 → 10만 원 초과 이체 → 확인 영역에 OTP 칸 → 앱 번호로 이체 / 틀린 번호면 "OTP 번호가 올바르지 않습니다. (오류 n/10회)"
  - 본인 계좌로 10만 원 초과 → OTP 칸 없음
  - 한글 금액, 잔액 초과·1회 한도 초과 문구, 입금계좌번호·금액을 지우면 [확인] 비활성

### 2-000. 2026-10-19 자산 리포트 (기획서 7. 자산관리, 사용자 요청으로 진행)

| 결정 | 내용 |
|---|---|
| 진입 | 헤더 "자산관리"(`/asset`, 공용 헤더 수정 안 함) → `/mybank/assets` 로 redirect (로그인 필요) |
| 저장 | 화면을 열 때 계산하고 최근 6개월을 `asset_report`에 회원·기준년월당 1행 저장. **(10-20) 이번 달만 매번 갱신하고, 지난달은 저장된 행이 있으면 그대로 쓴다 (마감된 기록, 사용자 결정). 같은 달 행이 여러 개면 id가 가장 큰 행 사용**. `account_id`에 **member_id** 저장 (데이터 사전에 기록) |
| 기간 | 기본 이번 달, 최근 12개월 중 선택 (`?month=yyyy-MM`) |
| 입금·출금 | 본인 계좌끼리 이체(같은 거래 번호의 상대 행도 내 계좌)는 제외 |
| 저축액 | 예금·적금 계좌로 들어온 금액 (본인 계좌에서 옮긴 것 포함) |
| 공과금·관리비 | 출금·이체 메모 키워드 (`관리비` / `공과금·전기·가스·수도·통신·세금`). 화면에 한계 안내 |
| 투자 | 출처 없음 → "준비 중", `investment_amount` NULL |
| 대출 | **(10-20 수정)** 팀3 `loan_account`(CLOSED 제외)의 `REMAINING_BALANCE`를 대출 잔액으로 사용. 다음 납부일은 `loan_repayment_schedule`(`LOAN_ACCOUNT_ID`, PAID 아닌 회차), 월별 상환액은 `account_loan_transaction`(`LOAN_REPAYMENT`, `TRANSACTED_AT`). 스케줄의 `PAID_AT`은 팀3 엔티티에 없어 채워지지 않는다. 대출금은 지급 계좌 잔액(총자산)에 들어가므로 순자산 = 총자산 − 대출 잔액. 대출 상환액은 월별 출금에 더하고, 대출금 지급은 소득이 아니라 입금에 넣지 않는다 (10-20 사용자 결정). 월 예상 이자 = 남은 원금 × 연 금리 ÷ 12 |
| 예적금·카드 | 팀3 `product_subscription`+`product`, `productcard`+`card_usage_history` 읽기만 (지금 데이터 없음) |
| 팀3 조회 실패 | 화면은 열리고 해당 부분만 "불러오지 못했습니다". 서비스에 `@Transactional`을 붙이지 않은 이유: 팀3 조회 예외가 바깥 트랜잭션을 rollback-only 로 만들어 저장까지 실패하므로 |
| 그래프 | 외부 라이브러리 없이 SVG(도넛)·CSS(막대). 계열 색 `#2a78d6/#eb6834/#1baf7a`(색각 검사 통과), 단일 계열은 네이비. 마우스 안내(`asset.js`), "표로 보기" |

- 새 파일: `AssetReportPageController`, `AssetReportService`, `AssetReportRepository`, `MonthlyCashFlow`, `MonthlyAmount`, `LoanSummary`, `SubscriptionSummary`, `dto/AssetReportView`, `templates/account/assets.html`, `asset-diff.html`, `css/asset.css`, `js/asset.js`, 테스트 2개 (`AssetReportServiceTests` 6개, `AssetReportPageControllerTests` 6개)
- 수정: `AssetReport.update(...)`, `schema-team2-dictionary.md`
- 테스트: 팀2 **170개 통과**. 집계 SQL 5개는 공용 DB에서 mysql 조회로 확인 (user1: 10월 입금 200,000 / 출금 60,000, 승인 대출 4건)
- ~~(10-19) 공용 DB로는 서버가 뜨지 않는다 (팀3 `loan_repayment_schedule` 금액 타입)~~ → **10-20 해결됨** (팀3 엔티티가 `BigDecimal`로 바뀌어 `OnbankApplicationTests` 통과)
- 브라우저 확인 전: 헤더 "자산관리" → 리포트, 월 선택, 도넛·막대 마우스 안내, 표로 보기, 대출 3건 표시 (10-20 user1 기준)

### 2-0. 2026-10-17 한 일

| 작업 | 내용 | 확인 |
|---|---|---|
| 내 계좌 / 상대계좌 즐겨찾기 (사용자 요청) | 이체 화면 출금계좌번호 옆 [내 계좌 즐겨찾기] 추가, 입금계좌 버튼을 [상대계좌 즐겨찾기]로 변경, 즐겨찾기한 내 계좌를 출금계좌 목록 맨 위·기본 선택 (1-1, 5장) | 테스트 통과, **브라우저 확인 전** |
| 테스트 환경 수정 | 공용 `SecurityConfig`에 로그인 실패 5회 잠금이 들어와 실패하던 팀2 화면 테스트 57개를 고침 (7장) | 통과 |
| 테스트 | 팀2 **146개 통과** (143 → 146) + `contextLoads` 통과 | |

- 수정: `AccountFavoriteService`, `TransferPageController`, `transfer.html`, `transfer.js`, 화면 테스트 4개(`AccountOpenPageControllerTests`, `AccountPageControllerTests`, `FavoritePageControllerTests`, `TransferPageControllerTests`), `AccountFavoriteServiceTests`, `docs/team2-handoff.md`, `docs/team2-progress.md`
- 새 파일: `dto/FavoriteGroups`
- 정하지 않은 것: 즐겨찾기 관리 화면(`/mybank/favorites`)도 내 계좌·상대계좌로 나눠 보여 줄지 (지금은 한 목록)

### 2-1. 2026-10-16 한 일

| 작업 | 내용 | 확인 |
|---|---|---|
| 계좌 개설 (입출금 ON 통장, 핵심) | `/mybank/accounts/open` → 비밀번호 4자리·확인·약관 → 결과. 계좌번호 `1002-XXX-XXXXXX`, 즉시 `ACTIVE` (4장) | 테스트 통과, **브라우저 확인 전** |
| 계좌 상태 변경 메서드 | `AccountManageService` 승인·반려·동결·해제·해지·잠금 해제 (팀4가 호출할 것) | 테스트 통과 |
| 계좌 개설 진입점 | 전계좌조회 입출금 빈 목록의 [계좌 개설], 메인 `index.html` "빠른메뉴 >" 펼침 목록의 [계좌 개설] | **브라우저 확인 전** (서버 재시작 필요) |
| 즐겨찾기 (심화, 사용자 요청으로 진행) | 관리 화면(등록·수정·삭제·순서 이동), 전계좌조회 ☆/★, 이체 화면 버튼 4개 연결, 이체 완료 [즐겨찾기 추가] (5장) | 테스트 통과, **브라우저 확인 전** |
| 테스트 환경 수정 | 공용 `SecurityConfig` 변경으로 실패하던 팀2 화면 테스트 35개를 고침 (7장) | 통과 |
| 팀3 공유 문서 | [team2-account-number-rule.md](team2-account-number-rule.md): 계좌번호 규칙, 예적금 계좌 생성 예시 코드, 협의할 것 4개 | 사용자가 팀3에 전달 |
| 테스트 | 팀2 **143개 통과** (74 → 143) + `contextLoads` 통과 | |

### 2-2. 10-16 커밋에 들어간 파일 (PR #33으로 main 머지됨)

- 수정: `AccountPageController`, `TransferPageController`, `TransferReceipt`, `Account`, `AccountFavorite`, `AccountRepository`, `AccountTransferRepository`, `AccountTransferService`, `account.css`, **`index.css`(공용)**, `transfer.js`, `list.html`, `transfer-result.html`, `transfer.html`, **`index.html`(공용)**, `AccountPageControllerTests`, `TransferPageControllerTests`, `docs/team2-handoff.md`
- 새 파일: `AccountOpenPageController`, `FavoritePageController`, `FavoriteEditForm`, `FavoriteForm`, `OpenAccountForm`, `OpenAccountReceipt`, `AccountFavoriteRepository`, `RecentDepositAccount`, `AccountFavoriteService`, `AccountManageService`, `AccountNumberGenerator`, `AccountOpenService`, 템플릿 `favorite-edit`, `favorite-toggle`, `favorites`, `open-result`, `open`, 테스트 8개, `docs/team2-account-number-rule.md`
- 커밋 메시지: `계좌 개설·상태 변경·즐겨찾기 기능 구현 및 팀3 계좌번호 규칙 문서 추가`

### 2-3. 다음에 할 일 (순서대로, 10-20 기준)

00. **(10-22) 브라우저 확인 (10-21 오후 작업)**: 서버 재시작 후 user1 전계좌조회 → 예금 10,000,000 / 적금 150,000 / 대출 4건 잔액 합계 56,084,850, 해지 예적금 2건 안 보임, 연결계좌 `1002-376-992742`, [조회]·[상환] 이동. 자산관리 → 도넛에 예금·적금 조각, 총자산 +10,150,000, 예적금 현황 2건
000. **팀3 답 받은 뒤**: 연결계좌 해지 막기(대안 A) 구현 여부 결정. 이체한도 기본값 규칙(OTP 등록 여부)·13.5 한도 변경 신청 진행 여부 사용자 결정
0. **(10-22 시작할 때) 정기이체 코드 이해하기 (사용자 요청)**: 10-21에 문답 시작 → 질문 1(`nextOccurrence` 결과 2개) 답 받기 전에 다른 작업으로 넘어감. 이어서 할 때 질문 1부터. 같은 날 전계좌조회 예적금 for문을 사용자가 직접 써 봄 (enum 이름·import·`model.addAttribute` 같은 이름 덮어쓰기에서 막힘 → 기초 문법은 빈칸 채우기 방식이 맞았음). 10-21 사용자가 "코드를 Claude가 다 써서 내가 이해해야 한다"고 함. Claude가 설명을 길게 하지 말고 **질문 → 사용자가 답 → 맞은 점·빠진 점만 짚기** 방식으로 진행 (방식은 시작할 때 사용자에게 다시 확인). 대상: `ScheduledTransfer.java`(회차 계산·성공/실패 처리) → `ScheduledTransferService.java`(등록 순서·`recordFailure`의 `isDue`) → `ScheduledTransferRunner.java`(트랜잭션을 따로 여는 이유). 질문 예시는 `docs/private/team2-portfolio-log.md` 10-21 항목 "직접 설명할 수 있어야 하는 것". 이후 작은 수정(기본 시각 09:00 → 10:00 등)을 사용자가 직접 해 보도록 제안
1. **브라우저 확인 (사용자)**: 정기이체 2-00000-1, 예약이체 2-0000-2, 이체 OTP 2-00, 자산 리포트 2-000, 계좌 개설 4-1
2. 확인 결과 문제가 있으면 수정 → **사용자가 지시하면** 커밋 → 푸시. main PR은 사용자가 GitHub에서
3. ~~정기이체 (5.5)~~ → **10-21 구현 완료** (2-00000, 결정은 1-1 "(10-21)" 행)
4. 팀원 공유 문구 전달 (사용자가 직접): 10장 "남음" 항목
5. **남은 작업 후보 — 사용자에게 먼저 묻는다**
   - **사기계좌 확인** (10-19 사용자가 정한 남은 작업): 팀4 `fds_transfer_block` 답을 받은 뒤 이체 Service에 검사 추가
   - 다건이체: 사용자 검토 중 (10-19 대화. 설계 방향: 이체 전 합계로 잔액·한도 검사 → 건별 트랜잭션 실행 → 건별 결과. OTP는 처음 한 번만 검증하고 인증 없는 내부 이체 메서드로 실행해야 함 — 팀1 OTP가 같은 번호 재사용을 막으므로)
   - 6.1 거래내역 페이징·필터: 10-19 사용자가 **건너뛰기로 함**
   - 즐겨찾기 관리 화면을 내 계좌·상대계좌로 나눌지 (2-0)
   - [심화] 4.4 계좌 별명, 4.6 계좌 해지, 4.7 계좌 비밀번호 변경, ~~5.4 예약이체~~(10-20 완료), 6.4 CSV 다운로드 / [선택] 계좌 개설 20영업일 제한, ~~5.5 정기이체~~(10-21 완료)
   - 예약이체: 서버가 오래 꺼져 있다 켜졌을 때 너무 지난 건을 실패 처리할지 (지금은 늦게라도 실행. 정기이체는 1회만 실행하고 지금 이후 회차로 넘어감)
   - 정기이체 [중지]/[재개]·수정: 10-21 사용자가 [해지]만 선택. 필요해지면 `PAUSED` 상태가 이미 있어 엔티티 메서드·버튼만 추가하면 됨

## 3. 현재 상태 한눈에

| 단계 | 상태 |
|---|---|
| 테이블·엔티티 7개 | 완료 (공용 DB 생성, validate 통과). 10-20 `scheduled_transfer` 추가 |
| 입출금 (엔티티·Service·화면) | 완료, 브라우저 확인 완료 |
| **이체 (당행 즉시이체)** | **완료, 브라우저 확인 완료 (10-16)** |
| **전계좌조회 화면 (`list.html` 원본 구성)** | **완료, 브라우저 확인 완료 (10-16)** |
| 테스트 | **전체 369개 통과, 1개 skip** (10-21 오후, `OnbankApplicationTests` 포함. 팀2 233개) |
| **전계좌조회·자산 리포트 예적금·대출 (팀3 연동)** | 완료 (10-21 오후, `account` 푸시). **브라우저 확인 전** (2-3장 00번) |
| **이체 OTP 연결 (10만 원 초과)** | 완료, main 머지됨 (#53). OTP 키 입력함 (10-20). **브라우저 확인 전** (2-00) |
| **자산 리포트 (`/mybank/assets`)** | 완료, main 머지됨 (#53, #60). 10-20 대출·지난달 저장 수정. **브라우저 확인 전** (2-000) |
| **enum 전환·하이픈·OTP 복귀·입력값 복원** | 완료, main 머지됨 (#60, 10-20). **브라우저 확인 전** |
| **예약이체 (5.4)** | 완료, main 머지됨 (#64). **브라우저 확인 전** (2-0000-2) |
| **정기이체 (5.5)** | 완료 (10-21, `account` 푸시, main PR 전). **브라우저 확인 전** (2-00000-1) |
| **계좌 개설 (입출금, 즉시 개설)** | 코드·테스트 완료, main 머지됨 (10-16). **브라우저 확인 전** (아래 4장) |
| 상태 변경 메서드 (승인·반려·동결·해제·해지·잠금 해제) | 코드·테스트 완료, main 머지됨 (10-16). 호출하는 화면은 팀4 |
| **즐겨찾기** | 완료, main 머지됨, **브라우저 확인 완료 (10-19 사용자)** |
| **내 계좌 / 상대계좌 즐겨찾기 (이체 화면)** | 완료, main 머지됨 (PR #36), **브라우저 확인 완료 (10-19 사용자)** |
| DB 통합 테스트 | 방법 결정 필요 (progress 5장) |

## 4. 계좌 개설 (기획서 5-2장, 핵심): 10-16 구현 완료, 확인 남음

### 4-1. 브라우저 확인할 것

서버를 재시작한 뒤 확인한다.

- 메인 화면 "빠른메뉴 >" → 펼침 목록 [계좌 개설] → 로그인 안 했으면 로그인 후 개설 화면으로
- http://localhost/mybank/accounts/open → 비밀번호 4자리 2번 + 약관 동의 → 결과 화면에 `1002-XXX-XXXXXX` 계좌번호
- 결과 화면 새로고침 → 개설 화면으로 돌아가는지 (계좌가 또 생기지 않는지)
- 비밀번호 불일치, 숫자 아닌 값, 약관 미동의 → 오류 문구
- 전계좌조회에 새 계좌(ON 통장, 잔액 0) → [입금] → [이체]에서 새 비밀번호로 출금되는지
- 확인으로 생긴 계좌는 공용 DB에 남는다. `account_number LIKE '1002-%'`로 찾아 워크벤치에서 정리

### 4-2. 구현한 것

| 파일 | 내용 |
|---|---|
| `Account.open(...)` | 개설 초기값: 잔액 0, `today_transfer_amount` 0, 오류 0, `ACTIVE`, `signup_date` |
| `Account` 상태 메서드 | `activate()` PENDING→ACTIVE / `reject()` PENDING→CLOSED / `freeze()` ACTIVE→FROZEN / `unfreeze()` FROZEN→ACTIVE / `close()` ACTIVE·FROZEN이고 잔액 0일 때만 → CLOSED. 규칙에 어긋나면 `IllegalStateException` |
| `AccountNumberGenerator` | 유형별 앞자리 + 난수, `existsByAccountNumber`로 중복 확인 후 5회까지 재시도 |
| `AccountOpenService.openChecking(memberId, rawPassword)` | 비밀번호 숫자 4자리 검사 → BCrypt 저장 → 즉시 `ACTIVE` |
| `AccountManageService` | 관리자 쪽이 호출: `activatePending`, `rejectPending`, `freeze`, `unfreeze`, `close`, `unlockPassword` (모두 `accountId` 하나, 계좌를 잠그고 변경). 권한 확인·관리자 로그는 호출하는 쪽에서 |
| `AccountOpenPageController` | `GET/POST /mybank/accounts/open`, `GET /mybank/accounts/open/result` (PRG, flash `receipt`) |
| 화면 | `account/open.html`, `account/open-result.html` (출금 화면과 같은 구성), `list.html` 입출금 빈 목록에 [계좌 개설] 링크, `account.css`에 `a.btn-join` |
| 테스트 | `AccountStatusChangeTests`, `AccountNumberGeneratorTests`, `AccountOpenServiceTests`, `AccountManageServiceTests`, `AccountOpenPageControllerTests` (30개 추가) |

### 4-3. 나중에 할 것

- 계좌가 이미 있는 회원은 메인 화면 "빠른메뉴 >" → [계좌 개설]로 들어간다 (전계좌조회에는 추가 버튼을 두지 않기로 함)
- **팀1 본인확인이 나오면** (사용자가 합칠지 결정한 뒤) PENDING 흐름 추가: `VERIFIED` → 즉시 개설 / `NONE`·`REJECTED` → 신분증·1원 인증 mock → 팀1 `request()` → 계좌 `PENDING` / 심사중 → 개설 불가. 승인·반려는 `AccountManageService.activatePending/rejectPending` 재사용. 결과 화면은 상태가 `PENDING`이면 "심사중" 문구가 이미 나오게 해 두었다
- 20영업일 내 1개 제한 ([선택])
- 예금·적금 계좌 생성: 팀3 상품 가입에서 만든다. 규칙과 예시 코드는 [team2-account-number-rule.md](team2-account-number-rule.md)로 공유 (팀3이 `AccountNumberGenerator`를 주입받아 쓰는 방식 권장). 팀2 전용 메서드(`openProductAccount` 등)를 만들지는 팀3 기능이 나온 뒤 결정

## 5. 즐겨찾기 (기획서 4.5 + 5.6, 심화): 10-16 구현 완료, 확인 남음

### 5-1. 브라우저 확인 (서버 재시작 필요)

- 전계좌조회 예금명 옆 ☆ → ★로 바뀌고 "즐겨찾기에 등록했습니다." / ★ → ☆, "즐겨찾기를 삭제했습니다." (해지·개설대기 계좌는 ☆ 없음)
- 이체 완료 화면 [⭐ 즐겨찾기 추가] → `/mybank/favorites`에 "즐겨찾기에 등록했습니다." + 목록에 받는 분 계좌 (예금주 가림). 이미 등록된 계좌로 이체하면 "★ 즐겨찾기 등록됨"
- `/mybank/favorites` 등록 폼: 없는 계좌번호 → 오류 문구, 같은 계좌 다시 등록 → "이미 즐겨찾기에 등록된 계좌입니다." / 별칭·메모 입력 → 목록에 표시
- 목록 ▲▼ → 순서 바뀜 (맨 위 ▲, 맨 아래 ▼ 없음) / [수정] → 별칭·메모 변경 / [이체] → 이체 화면에 출금계좌·입금계좌가 채워져 있는지 / [삭제] → 확인 창 → 삭제
- 본인 계좌 즐겨찾기를 "⭐ 즐겨찾는이체정보"로 고르면 입금계좌만 채워지는지 (출금계좌와 같은 계좌가 되지 않게)
- 이체 화면 버튼 4개: "⭐ 즐겨찾는이체정보"(입금계좌 + 기본 출금계좌), "자주쓰는 입금계좌"(입금계좌만), "최근입금계좌", "본인계좌"(지금 고른 출금계좌는 목록에서 빠짐). 선택 창은 ✕, 바깥 클릭, Esc로 닫힘
- 확인으로 생긴 즐겨찾기는 공용 DB `account_favorite`에 남는다
- **(10-17)** 전계좌조회에서 내 계좌 ☆ → 이체 화면 [내 계좌 즐겨찾기]에 그 계좌가 보이고, 고르면 출금계좌가 바뀌는지 (잔액조회 문구가 지워지고 [전액] 금액도 바뀜). 동결 계좌는 목록에 없는지
- **(10-17)** 즐겨찾기한 내 계좌가 출금계좌 목록 맨 위에 있고 처음부터 선택되어 있는지 / 계좌 화면에서 [이체]로 들어오면(`?from=`) 그 계좌가 선택되는지
- **(10-17)** [상대계좌 즐겨찾기]·"⭐ 즐겨찾는이체정보"에는 다른 사람 계좌만 보이는지. 확인 단계에서 [내 계좌 즐겨찾기]로 출금계좌를 바꾸면 입력 단계로 돌아가는지

### 5-2. 구현한 것

| 파일 | 내용 |
|---|---|
| `AccountFavoriteRepository` | 내 계좌들의 즐겨찾기 조회·개수·중복 확인, 정렬 순서 최댓값 |
| `AccountTransferRepository.findRecentDepositAccounts` | `account_transfer`에서 입금계좌번호별 마지막 이체 일시, 최근 순 (최대 10개). 결과는 `RecentDepositAccount` |
| `AccountFavorite` | `update(alias, memo)`, `changeSortOrder(n)` |
| `AccountFavoriteService` | `getMyFavorites`, `getMyFavoriteIdsByNumber`(계좌번호 → ID), `add`(내 계좌 확인 → `findRecipient()`로 입금 가능 확인 → 중복·20개 검사 → 가린 이름 저장, `sort_order` = 최댓값 + 1), `getMyFavorite`, `update`, `move`(1부터 다시 매긴 뒤 이웃과 교환), `delete`, `getRecentDepositAccounts`. 남의 즐겨찾기는 모두 "없음" |
| `FavoritePageController` | `GET/POST /mybank/favorites`, `GET/POST /{id}/edit`, `POST /{id}/move?direction=up|down`, `POST /{id}/delete`. 등록·삭제는 `returnTo=accounts`면 전계좌조회로 돌아감 (그 외 값은 무시) |
| 전계좌조회 | `AccountPageController`가 `favoriteIds` 전달, `account/favorite-toggle.html` 조각을 세 영역 예금명 칸에 넣음 (컬럼 구성은 그대로) |
| 이체 화면 | `TransferPageController`가 즐겨찾기·최근입금계좌·입금 가능한 내 계좌를 화면에 넣고, `?to=`로 입금계좌를 미리 채움. `transfer.html` 기존 버튼 4개에 기능만 연결 + 선택 창(`picker-layer`). `transfer.js` 하단에 선택 창 동작 |
| **(10-17) 내 계좌 / 상대계좌 구분** | `AccountFavoriteService.getMyFavoriteGroups(memberId)` → `FavoriteGroups(myAccounts, otherAccounts)`. `TransferPageController`가 내 계좌 즐겨찾기 중 출금 가능한 것만 `myAccountFavorites`로, 나머지를 `otherFavorites`로 넣고 출금계좌 목록 순서를 정함. `transfer.html` 선택 창에 `my-favorite` 목록 추가, `transfer.js`의 `.js-pick-from`이 출금계좌 select를 바꿈 (option의 `data-account-number`로 찾음) |
| 이체 결과 | `TransferReceipt`에 `fromAccountId` 추가, `transfer-result.html`에 [⭐ 즐겨찾기 추가] |
| 화면 | `account/favorites.html` (전계좌조회와 같은 표 구성 + 출금 화면과 같은 입력 표), `account.css`에 `alert-info`, `inline-form`, `picker-*` |
| 테스트 | `AccountFavoriteServiceTests` 18개, `FavoritePageControllerTests` 17개, `TransferPageControllerTests` 3개, `AccountPageControllerTests` 1개 추가 |

### 5-3. 나중에 할 것

- 즐겨찾기 관리 화면(`/mybank/favorites`) 진입점: 메인 "빠른메뉴 >" [즐겨찾기 목록 조회], 이체 화면 선택 창 "즐겨찾기 관리 ›", 등록 후 이동 (10-16 사용자 요청으로 메인 링크 추가)
- 이체 완료 화면에서 **출금계좌(내 계좌)**도 즐겨찾기할지 사용자 확인 필요 (지금은 받는 분 계좌만)

## 6. 구현 구조 요약 (10-20 기준)

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
                                                (10-20) executeDate·executeTime 이 있으면 예약 등록 → redirect /mybank/transfer/scheduled
     GET  /mybank/transfer/result             → transfer-result.html (영수증 없으면 입력 화면으로)
     GET  /mybank/transfer/recipient?accountNumber=  → JSON 받는 분 이름(가림, 예: 김*수)
     GET  /mybank/transfer/limit?accountId=          → JSON 1회/1일 한도, 오늘 이체액, 남은 한도
  AccountOpenPageController  /mybank/accounts/open
     GET/POST /mybank/accounts/open           → open.html (입출금 ON 통장, 비밀번호 4자리·확인·약관 동의)
     GET  /mybank/accounts/open/result        → open-result.html (PRG, flash "receipt", 없으면 개설 화면으로)
  FavoritePageController  /mybank/favorites
     GET/POST /mybank/favorites               → favorites.html (목록 + 등록, returnTo=accounts면 전계좌조회로)
     GET/POST /mybank/favorites/{id}/edit     → favorite-edit.html (별칭·메모)
     POST /mybank/favorites/{id}/move         → direction=up|down
     POST /mybank/favorites/{id}/delete       → 삭제 후 목록으로 (returnTo=accounts면 전계좌조회로)
  ScheduledTransferPageController (10-20, 10-21 정기 추가)  /mybank/transfer/scheduled
     GET  /mybank/transfer/scheduled          → scheduled-transfers.html (내 예약·정기 목록)
     POST /mybank/transfer/scheduled/{id}/cancel → 취소 / 정기는 해지 (본인 것, 대기·중지만)
     POST /mybank/transfer/scheduled/{id}/run    → [지금 실행] 시연용 (스케줄러와 같은 실행 경로)
  AssetReportPageController (10-19)
     GET  /asset                              → redirect /mybank/assets (헤더 "자산관리" 링크)
     GET  /mybank/assets[?month=yyyy-MM]      → assets.html (자산 리포트, 최근 12개월 중 선택)
  AccountController (예전 목업 주소) /account/list, /account/transfer → 새 화면으로 redirect

[Service]
  AccountService          deposit / withdraw / getMyAccounts / getMyAccount / getMyHistories
  AccountTransferService  transfer / authorize / transferPreAuthorized / findRecipient / getLimit
     transfer(memberId, fromAccountId, rawPassword, otpCode, toAccountNumber, amount, senderMemo, receiverMemo)
     (10-20) transfer = authorize(검증+비밀번호+OTP) + execute(잠금→1일 한도→잔액→기록, private)
             authorize()            : 예약 등록도 같은 인증을 쓴다
             transferPreAuthorized(): 인증 없이 validate + execute (예약 실행용, 화면에 연결하지 않음)
     transfer 순서: 입금계좌 확인 → 같은 계좌 금지 → 1회 한도 → (10만 원 초과 타인 이체면) OTP 등록 여부·번호 형식
                  → 비밀번호(REQUIRES_NEW) → OTP 검증(팀1 OtpService.verify) → 두 계좌 ID 오름차순 잠금
                  → 본인 계좌 확인 → 1일 한도
     requiresOtp / isOtpReady (화면 안내용), OTP_REQUIRED_AMOUNT = 100,000, OTP 미등록이면 OtpRequiredException
                  → transferOut/transferIn → account_transfer 1행 + transaction_history 2행 (같은 거래번호)
     통장 표시 비우면: 내 통장 = 받는 분 이름, 받는 분 통장 = 내 이름
  AccountOpenService      openChecking (즉시 ACTIVE)
  AccountManageService    activatePending / rejectPending / freeze / unfreeze / close / unlockPassword (관리자 쪽 호출용)
  AccountFavoriteService  getMyFavorites / getMyFavoriteIdsByNumber / add / update / move / delete / getRecentDepositAccounts
  AssetReportService      getReport(memberId, month) : 집계 → 최근 6개월 asset_report 저장(갱신) → 화면용 AssetReportView
                          (@Transactional 없음, 팀3 조회 실패는 warnings 로 안내)
  AssetReportRepository   월별 입출금(본인 계좌끼리 제외) + 팀3 대출·상환·예적금·카드 SQL 조회 (읽기만)
  ScheduledTransferService (10-20) reserve / subscribe(10-21 정기) / getMine / cancel / execute(트랜잭션: 잠금→재확인→이체→완료·다음 회차)
                           / recordFailure(새 트랜잭션, force=false 면 isDue 일 때만)
                           reserve·subscribe 공통 : checkAndAuthorize (계좌 상태 → 중복 → authorize)
  ScheduledTransferRunner  (10-20) runDue / runNow / runOne  ← @Transactional 없음 (1건씩 커밋, 실패 기록을 따로 하기 위해)
  ScheduledTransferScheduler (10-20, account/scheduler) @Scheduled 매분 0초 → runner.runDue
                           @EnableScheduling 은 공용 config/SchedulingConfig (팀3). 끄기: onbank.scheduled-transfer.enabled=false
  AccountPasswordVerifier (REQUIRES_NEW), TransactionSqnoGenerator, AccountNumberGenerator(+ normalize: 13자리 숫자 → 4-3-6)

[Entity] Account: open(정적 생성) / activate / reject / freeze / unfreeze / close
                  / deposit / withdraw / transferOut(오늘 이체 누적) / transferIn
                  / getTodayTransferAmountOn(date)(조회만) / touchTransaction / 비밀번호 오류 메서드
         ScheduledTransfer (10-20): reserve(정적 생성) / isDue / cancel / markSucceeded / markFailed(200자로 자름)
                  (10-21) subscribe(정기 정적 생성) / nextOccurrence(회차 계산, 말일) / isRecurring / isLastSucceeded
                          정기면 markSucceeded·markFailed 가 대기 유지 + moveToNextRound (종료일 넘으면 완료)
         enum: AccountStatus(label·입금/출금 가능 여부), AccountType(label), ScheduleType(label·transferType),
               ScheduledTransferStatus(label) (10-20)
         상수: TransactionType, TransactionStatus, TransferType

[화면 스크립트] static/js/account.js (금액 버튼, 중복 제출 방지, 10-20: 계좌번호 하이픈 .js-account-number, 제출 전 확인 창 form[data-confirm])
               static/js/transfer.js (잔액·오류횟수·한도 조회, 받는 분 확인, 확인 단계, Enter 제출 막기, 입금계좌 선택 창,
               10-19: 한글 금액, 금액 즉시 안내, [확인] 비활성, OTP 입력칸,
               10-20: 예약 모드 setScheduleMode (입력칸 disabled 면 전송 안 됨 → 즉시이체), OTP 등록 전 입력값 sessionStorage 저장·복원)
               static/js/asset.js (기준월 선택, 그래프 마우스 안내)
[자산 리포트 화면] templates/account/assets.html + asset-diff.html(지난달 대비 문구 조각), static/css/asset.css (SVG 도넛, CSS 막대)
[공용 화면] index.html 퀵메뉴 "빠른메뉴 >" 펼침 목록(#quickMenuPanel: 계좌 개설, 즐겨찾기 목록 조회) + index.css .quick-menu-panel
            펼침 목록은 #quickMenuPanel 하나만 둔다 (스크립트가 id로 찾으므로 패널을 복사하지 말고 <a>만 추가)
```

## 7. 주의할 점 (작업하다 알게 된 것)

- **팀2 DDL은 `project/onbank/src/main/resources/db/03_account_ddl.sql` 하나다** (팀원 형식: 01 팀1, 02 팀4). `docs/schema-team2-mysql.sql`은 10-16에 삭제했다. 테이블을 바꾸면 이 파일(맨 아래 변경 이력에 ALTER 기록) + 데이터 사전 + 엔티티를 함께 고친다
- **실행 순서는 01 → 03 → 02.** 번호는 03이지만 팀4 `02_admin_ddl.sql`의 FK가 팀2 테이블을 참조하므로 02보다 먼저 실행해야 한다
- **비밀번호 확인은 계좌 잠금보다 먼저.** 순서가 바뀌면 이체·출금 트랜잭션이 잡은 잠금을 REQUIRES_NEW 트랜잭션이 기다리며 멈춘다
- **`AccountPasswordVerifier.verify()`는 비밀번호가 틀리면 예외 대신 `false`.** 안에서 예외를 던지면 증가한 오류 횟수가 롤백된다
- **`ddl-auto=validate`는 모든 팀의 엔티티를 검사한다.** 다른 팀 테이블 하나만 어긋나도 서버 전체가 뜨지 않는다
- **`application-local.properties`는 팀 공용 DB(cafe24)를 가리킨다.** 테스트가 커밋하면 공용 DB에 남는다
- **목업 HTML의 `<button>`을 form 안에 넣을 때는 `type="button"` 필수.** 없으면 누를 때마다 form이 제출된다
- **화면 테스트(`@WebMvcTest`)에는 `@MockitoBean JpaMetamodelMappingContext`가 필요하다** (`@EnableJpaAuditing` 때문)
- **(10-16) 공용 `SecurityConfig`가 회원·관리자·직원 로그인 3개로 나뉘었다.** 화면 테스트에 `@MockitoBean`으로 `MemberDetailsService`, `AdminDetailsService`, `EmployeeDetailsService`를 넣어야 Spring이 뜬다. 새 화면 테스트를 만들 때 기존 테스트에서 복사
- **(10-17) `SecurityConfig`에 로그인 실패 5회 잠금이 들어오면서** 화면 테스트에 `MemberLoginAttemptRecorder`, `AdminLoginAttemptRecorder`, `EmployeeLoginAttemptRecorder`도 `@MockitoBean`으로 넣어야 한다 (팀2 화면 테스트 4개에 추가함). 공용 `SecurityConfig`가 다시 바뀌면 같은 방식으로 화면 테스트가 모두 실패한다
- **(10-16) CSRF 토큰이 없거나 틀리면 403이 아니라 `/login?expired`로 리다이렉트된다** (공용 `SecurityConfig`). 팀2 테스트 2개의 기대값을 여기에 맞췄다
- Bash heredoc으로 Java 파일을 만들면 `"\\d"`가 `"\d"`로 바뀌어 컴파일 오류가 난다. 정규식이 있는 파일은 Write/Edit로 작성
- 일부 기존 파일은 줄바꿈이 CRLF라 sed/perl 여러 줄 치환이 안 먹는다. Edit 도구 사용
- 회사 PC의 `python`은 Microsoft Store 바로가기라 실행되지 않는다. 문서 일괄 치환은 Edit 도구로 한다
- 서버 코드를 바꾼 뒤에는 서버를 **재시작**해야 브라우저에 반영된다 (템플릿은 `thymeleaf.cache=false`라 새로고침만으로 반영되지만 Java는 아님)
- **(10-19) 팀1 OTP 검증(`OtpService.verify`)은 같은 30초 구간 번호를 다시 쓰지 못하게 막는다.** 한 요청에서 이체를 여러 번 실행하는 기능(다건이체 등)은 OTP를 처음 한 번만 검증하는 구조로 만들어야 한다
- **(10-19) 이체 화면 `a.btn-sm`은 `display: inline-block`이라 `hidden` 속성이 먹지 않는다.** 숨길 요소에는 `[hidden] { display: none; }` 규칙을 따로 둔다 (`account.css`)
- **(10-19) 금액 바로입력 버튼(`account.js`)은 값을 바꾼 뒤 `input` 이벤트를 보낸다.** 금액 안내 문구·확인 단계 되돌리기가 이 이벤트로 동작한다
- **(10-19) 팀3 테이블은 DDL 파일(`onbank_loan.sql`)과 공용 DB 실제 구조가 다르다** (예: 대출은 `loan`이 아니라 `loan_account`, 상환 스케줄은 10-20 기준 `LOAN_ACCOUNT_ID`로 연결되고 옛 `APPLICATION_ID` 칼럼은 비어 있음). 팀3 테이블을 SQL로 읽을 때는 `information_schema.columns`로 실제 컬럼을 먼저 확인
- **(10-19) 다른 팀 테이블 조회가 실패할 수 있는 Service에는 바깥 `@Transactional`을 붙이지 않는다.** Repository 안에서 난 예외가 바깥 트랜잭션을 rollback-only 로 만들어, 예외를 잡아도 뒤의 저장이 실패한다 (`AssetReportService`)
- **(10-19) 공용 DB를 조회만 해서 확인하는 방법**: mysql 명령줄로 SELECT 실행 (접속 정보는 `application-local.properties`에서 읽음). Spring 통합 테스트는 `contextLoads`가 실패하면 쓸 수 없다
- **(10-20) enum 비교는 `==`. `"CHECKING".equals(account.getAccountType())`는 컴파일 오류 없이 항상 false.** main을 합칠 때마다 `grep -rn '"CHECKING"\|"ACTIVE"' src/main/java`로 다른 팀의 새 문자열 비교를 찾는다 (10-20 merge에서 팀3 대출 상환·예적금 상세 2곳 발견해 고침). `AccountType.CHECKING.equals(enum)`은 enum끼리라 정상
- **(10-20) `No enum constant ...` 오류는 DB 값이 아니라 브랜치가 뒤처진 경우가 많다.** 다른 팀이 main에서 enum 값(예: `LOCKED`)을 추가하고 그 코드로 DB에 저장하면, 옛 코드는 읽다가 실패한다. DB를 `UPDATE`하지 말고 `origin/main`을 합친다
- **(10-20) 트랜잭션 안에서 난 예외는 잡아도 그 트랜잭션에 저장할 수 없다 (rollback-only).** 예약이체 실패 기록은 실행 트랜잭션이 롤백된 뒤 새 트랜잭션(`recordFailure`)에서 한다. 그래서 순서를 맡는 `ScheduledTransferRunner`에는 `@Transactional`을 붙이지 않는다
- **(10-20) 스케줄러는 팀원마다 서버를 띄우면 여러 개 돈다 (공용 DB).** 실행 전 `findByIdForUpdate`로 행을 잠그고 상태를 다시 봐야 같은 건이 두 번 이체되지 않는다
- **(10-20) Spring Boot 4의 `@WebMvcTest` 패키지는 `org.springframework.boot.webmvc.test.autoconfigure`.** 예전 `...boot.test.autoconfigure.web.servlet`은 없다. 새 화면 테스트는 기존 테스트의 import를 복사
- **(10-20) `<input type="time">` 값은 `09:30`.** `@DateTimeFormat(iso = ISO.TIME)`은 다시 그릴 때 초·시간대까지 붙여 입력칸이 비므로 `pattern = "HH:mm"`
- **(10-20) 테스트에 한도 같은 설정값을 숫자로 쓰지 않는다.** 사용자가 기본 한도를 바꾸자 테스트 2개가 깨졌다 → `DEFAULT_ONCE_LIMIT + 1`처럼 상수로 계산
- **(10-20) 화면에 확인 창이 필요한 폼은 `data-confirm="문구"`만 붙이면 된다** (`account.js`). 중복 제출 방지보다 먼저 등록돼 있어 [취소]를 눌러도 버튼이 잠기지 않는다
- **(10-21) 팀3 예적금은 `account`가 아니라 `savings_account`** (상태 `SavingsStatus`, 원금 `principal`, 납입 `savings_payment`). 돈은 연결계좌(`source_account_id` = 팀2 입출금 계좌)에서 빠지고 만기에 그 계좌로 들어온다 (`OnbankAccountMoneyAdapter`, ACTIVE 계좌만 허용). `account` 기준 합계·목록에는 예적금이 안 잡히므로 따로 더해야 한다
- **(10-21) 이클립스로 서버를 띄우면 `@RequestParam String email`처럼 이름 없는 파라미터가 실패할 수 있다** (`Name for argument ... -parameters`). Project → Properties → Java Compiler → "Store information about method parameters" 체크 (= `.settings/org.eclipse.jdt.core.prefs`의 `codegen.methodParameters=generate`) → Project Clean. 팀2 새 코드는 `@PathVariable("accountId")`처럼 이름을 적는다
- **(10-21) 컨트롤러가 새 Bean(Repository 등)을 주입받으면 그 컨트롤러의 `@WebMvcTest`가 전부 실패한다** (`No qualifying bean`). 테스트에 `@MockitoBean`을 추가 (예: `AccountPageControllerTests`의 `SavingsAccountRepository`, `AssetReportRepository`)

## 8. 공용 DB 현재 데이터 (10-16 조회 기준)

- 회원: `user1`(member_id 1), `user2`, `user3`
- 테스트 계좌 (`user1` 소유, 비밀번호 1234): `TEST-0001` ACTIVE / `TEST-0002` FROZEN / `TEST-0003` PENDING. 모두 `account_type` NULL
- `members` 이체한도 컬럼: `user1`은 NULL → 기본 한도 적용
- 브라우저 확인으로 생긴 입금·출금·이체 거래가 남아 있다. 정리는 [data-team2-mysql.sql](data-team2-mysql.sql) 아래쪽 삭제 SQL
- `data-team2-mysql.sql` 15번째 줄 아이디는 각자 PC에서 바꿔 쓰고 **커밋하지 않는다**
- **(10-19 조회)** 계좌 5개(테스트 3 + 개설 `CHECKING` 2), 거래내역 6건, `asset_report` 0건. 팀3: `loan_application` 11건(user1 승인 4건, 8,100만 원), 예적금·카드·상환 스케줄 0건
- 자산 리포트 화면을 열면 `asset_report`에 회원당 최근 6개월 행이 생긴다 (정리: `DELETE FROM asset_report WHERE account_id = 회원ID`)
- **(10-20 조회)** user1: 입출금 `CHECKING` 1002-376-992742 잔액 36,914,850 (대출금 3,700만 입금 − 상환 85,150) + 테스트 계좌. 팀3 `loan_account` 3건(잔액 합 36,918,183), `account_loan_transaction` 지급 3·상환 1. `asset_report` user1 202605~202609에 **사용자가 넣은 시연 데이터(id 22~26)** 와 앱이 만든 0원 행(id 1~5)이 같은 달에 함께 있음 → 화면은 id 큰 행 사용, 0원 행은 사용자가 지울 예정
- **(10-20)** `members` LOCKED 2명, `employee` LOCKED 1명 (팀1 로그인 잠금 기능 결과. 고치지 않음). `scheduled_transfer` 테이블 생성 (0건)
- **(10-21 조회)** `members` 회원 5명(member_id 1, 2, 3, 42, 278) 모두 `daily_transfer_limit`·`once_transfer_limit` NULL → 기본 한도. user1 팀3 `savings_account` 4건(진행 중: 프리미엄 예금 10,000,000 / 장기적금 150,000, 나머지 2건 CLOSED), 연결계좌 모두 `1002-376-992742`. `loan_account` 4건(ACTIVE, 잔액 합 56,084,850). `scheduled_transfer` 3건 (id 2 user1 정기 "용돈" 진행 중 → 확인 후 [해지] 필요)

## 9. 자주 쓰는 명령 (`project/onbank` 폴더에서)

```
./gradlew compileJava                                   # 컴파일
./gradlew test --tests "org.doit.onbank.account.*"      # 팀2 테스트 (DB 불필요)
./gradlew test                                          # 전체 369개 (10-21 오후, 공용 DB 연결 테스트 포함)
./gradlew test --tests "org.doit.onbank.OnbankApplicationTests"   # 서버가 뜨는지 (공용 DB 연결, 읽기만)
./gradlew bootRun                                       # 서버 실행 (포트 80), PowerShell 은 .\gradlew.bat bootRun
```

- 로그인 http://localhost/login → 전계좌조회 http://localhost/mybank/accounts → 이체 http://localhost/mybank/transfer → 예약·정기이체 조회 http://localhost/mybank/transfer/scheduled → 자산관리 http://localhost/mybank/assets (헤더 "자산관리") → OTP 발급 http://localhost/mybank/security/otp

## 10. 팀에 전달·협의할 것

| 대상 | 내용 | 상태 |
|---|---|---|
| 팀1 | members 이체한도 컬럼 ALTER | 완료 |
| 팀1 | progress 5장 "팀1에 전달" 항목 (한도 NOT NULL DEFAULT, `transfer_limit_request` 회원 기준 등) | 남음 |
| 팀1 | **본인확인 `request()/approve()/requireVerified()` 제공 시기, approve 때 팀2 `AccountManageService.activatePending(accountId)` 호출 요청** | 남음 (그동안 계좌는 본인확인 없이 즉시 개설) |
| 팀1 | OTP 기능 제공 시기 (이체에서 지금은 건너뜀) | **해결 (10-19)**: 팀1 `OtpService.verify()`를 이체에 연결 |
| 팀3 | `AccountRepository`/`AccountService`에 대출금 계좌 조회 메서드 추가함 (10-17, 이지훈). `account_type` NULL 계좌는 목록에 안 나옴 → 테스트 계좌만 해당이라 그대로 둠 (10-19 사용자 결정) | 확인함 |
| 팀4 | `fds_transfer_block`(사기 신고 계좌 이체 차단 기록) 추가됨. 이체 전 검사를 팀2가 호출해야 하는지, 신고 데이터 위치·차단 기준 확인 필요 | 남음 (다음 작업 후보) |
| 팀1 | **OTP 암호화 키(`onbank.otp.seed-key`) 공유 요청** | 받음 (10-20 사용자가 입력) |
| 팀3 | **(10-20) `AccountStatus`·`AccountType` enum 전환**: `account.getAccountType()`/`getAccountStatus()`가 String이 아니라 enum을 돌려준다. 비교는 `== AccountType.CHECKING` / `== AccountStatus.ACTIVE`. 팀3 코드(`LoanApplicationService`, `LoanExecutionService`, `AccountTransferService.disburseLoan`)의 문자열 비교는 팀2가 같이 고쳤고 주석으로 남아 있던 enum 버전으로 바꿈. `AccountRepository.findByMemberIdAndAccountTypeAndAccountStatus` 매개변수도 enum. 화면에서 `${account.accountType}`은 전과 같이 `CHECKING`으로 출력되고, 한글은 `.label` | 남음 (팀3에 알리기) |
| 팀1 | **(10-20) 팀1 화면 `security/otp.html` 수정**: 사용중(ACTIVE) 상태의 [번호 확인] 옆에 [이체 화면으로 돌아가기] 버튼 추가 (사용자 요청). 팀1에 알리기 | 남음 |
| 팀3 | ~~공용 DB `loan_repayment_schedule` 금액 3컬럼 타입 불일치로 서버 시작 실패~~ | **해결 (10-20 확인)**: 팀3 엔티티가 `BigDecimal`로 바뀜 |
| 팀3 | **(10-20) main 합친 뒤 팀3 코드 2곳의 문자열 비교를 enum으로 고침**: `LoanRepaymentService`(대출 상환 계좌 검사, 그대로면 상환이 항상 실패), `SavingsProductDetailController`(예적금 가입 출금계좌 목록, 그대로면 항상 빈 목록). 팀3가 주석으로 남긴 enum 버전으로 바꿈. **커밋 전** | 남음 (팀3에 알리기) |
| 팀3 | **(10-20) `config/SchedulingConfig`(`@EnableScheduling`)를 팀2 예약이체 스케줄러도 같이 쓴다.** 지우면 예약이체 자동 실행이 멈춘다 | 남음 (팀3에 알리기) |
| 팀3 | **(10-20) 화면 이름 "정기이체" 겹침**: 팀3 `/savings/auto-transfers` 제목이 "정기이체 현황"(적금 자동납입). 팀2 정기이체(5.5, 다른 계좌로 매월 이체)를 만들 때 이름을 어떻게 나눌지 | **10-21 사용자 결정: 따로 작업하므로 둘 다 "정기이체" 그대로** (알릴 필요 없음) |
| ~~팀3~~ | ~~예약·정기이체가 호출할 이체 메서드 시그니처~~ → **10-20 예약·정기이체를 팀2가 직접 맡기로 함** (기획서 원래 배정). 팀3에 "예약·정기이체는 팀2가 한다"고 알리기 | 남음 (알리기만) |
| 팀3 | 자산 리포트가 팀3 테이블 5개를 SQL로 읽는다 (`AssetReportRepository`). 컬럼 이름이 바뀌면 알려 달라고 요청 | 남음 → 10-21 문구에 포함 |
| 팀3 | **(10-21) 예적금·대출 연동 공유 + 연결계좌 해지·동결 문제 확인 요청**: 전계좌조회·자산관리가 `savings_account`, `savings_product`, `savings_payment`, `loan_account` 등을 조회함 / 연결계좌 해지(팀2가 진행 중 예적금 있으면 막는 안 제안)·동결(만기 `credit`은 동결 계좌도 허용 검토 요청) / 예적금 고유 번호(선택). 팀3 답: "예적금 상태·테이블은 따로 관리" → 돈 이동은 팀2 `account` 연결계좌를 쓰므로 문제는 그대로라고 문구 보완. 문구는 대화에서 사용자에게 제공 | 사용자가 전달, 답 대기 |
| 팀3 | ~~예금·적금 가입 시 계좌 생성 규칙 문서 전달~~ | 10-20 사용자가 문서 삭제 (팀3 예적금이 main에 들어옴) |
| 팀3 | ~~대출금 입금(`AccountService.deposit()`)~~ | 해결: 팀3가 `AccountTransferService.disburseLoan()`으로 구현 (`account_loan_transaction`에 기록, `transaction_history`에는 안 남김) |
| 팀4 | 동결·해제·잠금 해제 화면에서 호출할 메서드 | **만듦 (10-16)**: `AccountManageService.freeze/unfreeze/close/unlockPassword/activatePending/rejectPending(accountId)`. 커밋 후 팀4에 전달 |
| 팀4 | 이체 완료 이벤트 클래스 이름과 필드 (`AccountTransferService` 8번 자리) | 남음 |
| 팀4 | `02_admin_ddl.sql` 참조 테이블 이름 | 해결됨 (v0.7에서 `account`, `account_transfer`, `transaction_history`로 수정) |
