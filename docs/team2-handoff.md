# 팀원2 작업 인수인계 (2026-10-19 정리)

> 새 세션에서 Claude에게 이렇게 요청하세요.
> **"docs/team2-handoff.md 와 docs/team2-progress.md 읽고 이어서 작업하자"**
>
> - 이 파일: 최근 작업 상태, 바로 다음에 할 일, 주의할 점 (**최신**)
> - [team2-progress.md](team2-progress.md): 기본 결정 사항(3장), 엔티티 메서드, 이체 설계, 전체 할 일 목록. **10-16 이후 결정은 이 파일 1-1이 기준**
> - [schema-team2-dictionary.md](schema-team2-dictionary.md): 테이블 컬럼 설명
> - [team2-account-number-rule.md](team2-account-number-rule.md): 팀3에 공유할 계좌번호 규칙·예적금 계좌 생성 방법

---

## 0. 시작하기 전에 확인할 것

1. **브랜치 상태 (10-19)**
   - 10-16 작업은 PR #33, 10-17 작업(내 계좌 / 상대계좌 즐겨찾기)은 PR #36으로 **main에 머지됨**
   - **10-19 작업(이체 OTP 연결, 자산 리포트)은 이 PC `account` 브랜치에 커밋하지 않은 상태로 있다.** 사용자가 지시하면 커밋·푸시 → main PR은 사용자가 GitHub에서 만들고 머지 (이 PC에는 `gh`가 없어 Claude가 PR을 만들 수 없음)
   - 로컬 `account`는 10-19 시작 시 `origin/main`과 같게 맞춰져 있었다 (`origin/account`보다 43커밋 앞섬, main 머지분이라 문제 없음)
   - 다른 PC에서 받기:
     ```
     git fetch origin
     git checkout account
     git pull origin account
     ```
   - 받아졌는지 확인: `project/onbank/src/main/java/org/doit/onbank/account/service/AssetReportService.java` 가 있으면 10-19 작업까지 받아진 것
   - 커밋하지 않은 파일이 있으면 pull 이 막힌다. 먼저 `git status` 로 확인 (`docs/data-team2-mysql.sql` 아이디 줄은 PC마다 다르므로 커밋하지 않는다)
   - PR이 머지된 뒤 새 작업을 시작할 때는 `git pull origin main`으로 `account`를 main과 맞춘다
2. PC마다 따로 있는 것 (Git에 없음)
   - `CLAUDE.local.md`: 개인 작업 원칙 (`.git/info/exclude`로 제외)
   - `application-local.properties`: 공용 DB(cafe24) 접속 정보 + **(10-19) `onbank.otp.seed-key=팀1 공유 키`** (없으면 OTP 발급·검증 시 "OTP 암호화 키가 설정되지 않았습니다" 오류. 10-19 기준 이 PC에는 없음, 팀1에 요청함)
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
- **(10-17 추가) 포트폴리오 정리**: 작업이 끝난 뒤 사용자가 **"포트폴리오 정리해 줘"**(또는 "포폴 정리해 줘")라고 하면, 그 작업에서 발표·포트폴리오에 쓸 트러블슈팅·설계 결정을 `[분류] 제목 / 한 줄 요약 / 배경 → 문제 → 원인 → 선택지(표) → 해결 → 결과 → 배운 점 / 관련 파일·커밋` 형식으로 정리해 **답변에 복사할 수 있는 텍스트로 바로 제공**하고, 같은 내용을 Git 제외 폴더 `docs/private/team2-portfolio-log.md`에 이어서 추가한다 (`.git/info/exclude`로 제외되어 이 PC에만 있다). 확인한 사실과 수치만 쓰고 사용자의 결정은 표시하며, **이 내용은 저장소에 커밋하지 않는다** (사용자가 개인 저장소에 따로 보관)

### 1-1. 2026-10-16 이후 확정한 결정

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

## 2. 작업 요약

### 2-00. 2026-10-19 한 일

| 작업 | 내용 | 확인 |
|---|---|---|
| 10-17 작업 main 머지 | PR #36 머지 확인. `account` = `origin/main` | |
| 즐겨찾기 브라우저 확인 | 사용자 확인 완료 | 완료 |
| **이체 OTP 연결** | 1-1 (10-19) 결정대로 구현. 순서: 입력 검증 → (OTP 필요 시) 등록 여부·번호 형식 확인 → 계좌 비밀번호(REQUIRES_NEW) → `otpService.verify()`(팀1, 오류 횟수는 팀1이 별도 트랜잭션) → 계좌 잠금 | 테스트 통과, **브라우저 확인 전** |
| 테스트 | 팀2 **158개 통과** (146 → 158) + `contextLoads` 통과 | |

- 수정: `AccountTransferService`(`OTP_REQUIRED_AMOUNT`, `transfer(...)`에 `otpCode` 인자 추가, `requiresOtp`, `isOtpReady`), `TransferForm`(`otpCode`), `TransferPageController`, `transfer.html`, `transfer.js`, `account.js`(금액 버튼이 `input` 이벤트를 보냄), `account.css`, `AccountTransferServiceTests`, `TransferPageControllerTests`
- 새 파일: `service/OtpRequiredException`
- **`transfer()` 시그니처가 바뀜**: `transfer(memberId, fromAccountId, rawPassword, otpCode, toAccountNumber, amount, senderMemo, receiverMemo)`. 팀3이 예약·정기이체에서 호출할 때 알려 줄 것
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
| 저장 | 화면을 열 때 계산하고 최근 6개월을 `asset_report`에 회원·기준년월당 1행 저장(있으면 갱신). `account_id`에 **member_id** 저장 (데이터 사전에 기록) |
| 기간 | 기본 이번 달, 최근 12개월 중 선택 (`?month=yyyy-MM`) |
| 입금·출금 | 본인 계좌끼리 이체(같은 거래 번호의 상대 행도 내 계좌)는 제외 |
| 저축액 | 예금·적금 계좌로 들어온 금액 (본인 계좌에서 옮긴 것 포함) |
| 공과금·관리비 | 출금·이체 메모 키워드 (`관리비` / `공과금·전기·가스·수도·통신·세금`). 화면에 한계 안내 |
| 투자 | 출처 없음 → "준비 중", `investment_amount` NULL |
| 대출 | 팀3 `loan_application`(APPROVED) + `loan_repayment_schedule`(APPLICATION_ID) 읽기만. 대출 실행 연동 전이라 승인 건을 대출 잔액으로 계산. 월 예상 이자 = 남은 원금 × 연 금리 ÷ 12 |
| 예적금·카드 | 팀3 `product_subscription`+`product`, `productcard`+`card_usage_history` 읽기만 (지금 데이터 없음) |
| 팀3 조회 실패 | 화면은 열리고 해당 부분만 "불러오지 못했습니다". 서비스에 `@Transactional`을 붙이지 않은 이유: 팀3 조회 예외가 바깥 트랜잭션을 rollback-only 로 만들어 저장까지 실패하므로 |
| 그래프 | 외부 라이브러리 없이 SVG(도넛)·CSS(막대). 계열 색 `#2a78d6/#eb6834/#1baf7a`(색각 검사 통과), 단일 계열은 네이비. 마우스 안내(`asset.js`), "표로 보기" |

- 새 파일: `AssetReportPageController`, `AssetReportService`, `AssetReportRepository`, `MonthlyCashFlow`, `MonthlyAmount`, `LoanSummary`, `SubscriptionSummary`, `dto/AssetReportView`, `templates/account/assets.html`, `asset-diff.html`, `css/asset.css`, `js/asset.js`, 테스트 2개 (`AssetReportServiceTests` 6개, `AssetReportPageControllerTests` 6개)
- 수정: `AssetReport.update(...)`, `schema-team2-dictionary.md`
- 테스트: 팀2 **170개 통과**. 집계 SQL 5개는 공용 DB에서 mysql 조회로 확인 (user1: 10월 입금 200,000 / 출금 60,000, 승인 대출 4건)
- **(10-19 확인) 지금 공용 DB로는 서버가 뜨지 않는다**: 팀3 `loan_repayment_schedule`의 `PRINCIPAL/INTEREST/TOTAL_AMOUNT`가 공용 DB에서 `decimal(19,0)`인데 팀3 엔티티는 `Long`(BIGINT) → `ddl-auto=validate` 실패. main 코드 그대로도 `contextLoads` 실패. 팀3 확인 필요 (팀2 코드 문제 아님)
- 브라우저 확인 전 (위 문제 해결 후): 헤더 "자산관리" → 리포트, 월 선택, 도넛·막대 마우스 안내, 표로 보기, 대출 4건 표시

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

### 2-3. 다음에 할 일 (순서대로)

1. **팀3 공용 DB 타입 문제 해결 대기**: `loan_repayment_schedule` 금액 3컬럼이 `decimal(19,0)` ↔ 팀3 엔티티 `Long` → 서버가 뜨지 않는다 (2-000). 사용자가 팀3에 전달. 해결 전에는 브라우저 확인 불가
2. **팀1 OTP 키 받기** → `application-local.properties`에 `onbank.otp.seed-key` 추가
3. **브라우저 확인** (서버 재시작 후): 이체 OTP (2-00), 자산 리포트 (2-000), 계좌 개설 4-1, 메인 "빠른메뉴 >" [계좌 개설]. 즐겨찾기는 10-19 사용자 확인 완료
4. 확인 결과 문제가 있으면 수정 → **사용자가 지시하면** 커밋·푸시 → PR (사용자)
5. 팀원 공유 문구 전달 (사용자가 직접, 10-19 대화에서 작성): OTP 연결 규칙, 팀1 키 요청, 팀3 `transfer()` 시그니처 변경, 팀4 `fds_transfer_block` 문의
6. 팀3에 [team2-account-number-rule.md](team2-account-number-rule.md) 전달, 팀4에 `AccountManageService` 메서드 전달 (10장)
7. **남은 작업 후보 — 사용자에게 먼저 묻는다**
   - **사기계좌 확인** (10-19 사용자가 정한 남은 작업): 팀4 `fds_transfer_block` 답을 받은 뒤 이체 Service에 검사 추가
   - 다건이체: 사용자 검토 중 (10-19 대화. 설계 방향: 이체 전 합계로 잔액·한도 검사 → 건별 트랜잭션 실행 → 건별 결과. OTP는 처음 한 번만 검증하고 인증 없는 내부 이체 메서드로 실행해야 함 — 팀1 OTP가 같은 번호 재사용을 막으므로)
   - 6.1 거래내역 페이징·필터: 10-19 사용자가 **건너뛰기로 함**
   - 즐겨찾기 관리 화면을 내 계좌·상대계좌로 나눌지 (2-0)
   - [심화] 4.4 계좌 별명, 4.6 계좌 해지, 4.7 계좌 비밀번호 변경, 5.4 예약이체(팀3), 6.4 CSV 다운로드 / [선택] 계좌 개설 20영업일 제한, 5.5 정기이체

## 3. 현재 상태 한눈에

| 단계 | 상태 |
|---|---|
| 테이블·엔티티 6개 | 완료 (공용 DB 생성, validate 통과) |
| 입출금 (엔티티·Service·화면) | 완료, 브라우저 확인 완료 |
| **이체 (당행 즉시이체)** | **완료, 브라우저 확인 완료 (10-16)** |
| **전계좌조회 화면 (`list.html` 원본 구성)** | **완료, 브라우저 확인 완료 (10-16)** |
| 팀2 테스트 | **170개 통과** (DB 없이, 10-19). `contextLoads`는 **팀3 공용 DB 타입 문제로 실패 중** (2-000) |
| **이체 OTP 연결 (10만 원 초과)** | 코드·테스트 완료 (10-19). **커밋 전, 브라우저 확인 전** (2-00). OTP 키 필요 |
| **자산 리포트 (`/mybank/assets`)** | 코드·테스트 완료, 집계 SQL 공용 DB 조회 확인 (10-19). **커밋 전, 브라우저 확인 전** (2-000) |
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

## 6. 구현 구조 요약 (10-19 기준)

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
  AccountOpenPageController  /mybank/accounts/open
     GET/POST /mybank/accounts/open           → open.html (입출금 ON 통장, 비밀번호 4자리·확인·약관 동의)
     GET  /mybank/accounts/open/result        → open-result.html (PRG, flash "receipt", 없으면 개설 화면으로)
  FavoritePageController  /mybank/favorites
     GET/POST /mybank/favorites               → favorites.html (목록 + 등록, returnTo=accounts면 전계좌조회로)
     GET/POST /mybank/favorites/{id}/edit     → favorite-edit.html (별칭·메모)
     POST /mybank/favorites/{id}/move         → direction=up|down
     POST /mybank/favorites/{id}/delete       → 삭제 후 목록으로 (returnTo=accounts면 전계좌조회로)
  AssetReportPageController (10-19)
     GET  /asset                              → redirect /mybank/assets (헤더 "자산관리" 링크)
     GET  /mybank/assets[?month=yyyy-MM]      → assets.html (자산 리포트, 최근 12개월 중 선택)
  AccountController (예전 목업 주소) /account/list, /account/transfer → 새 화면으로 redirect

[Service]
  AccountService          deposit / withdraw / getMyAccounts / getMyAccount / getMyHistories
  AccountTransferService  transfer / findRecipient / getLimit
     transfer(memberId, fromAccountId, rawPassword, otpCode, toAccountNumber, amount, senderMemo, receiverMemo)
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
  AccountPasswordVerifier (REQUIRES_NEW), TransactionSqnoGenerator, AccountNumberGenerator

[Entity] Account: open(정적 생성) / activate / reject / freeze / unfreeze / close
                  / deposit / withdraw / transferOut(오늘 이체 누적) / transferIn
                  / getTodayTransferAmountOn(date)(조회만) / touchTransaction / 비밀번호 오류 메서드
         상수: AccountStatus, AccountType, TransactionType, TransactionStatus, TransferType

[화면 스크립트] static/js/account.js (금액 버튼, 중복 제출 방지), static/js/transfer.js (잔액·오류횟수·한도 조회, 받는 분 확인, 확인 단계, Enter 제출 막기, 입금계좌 선택 창,
               10-19: 한글 금액, 금액 즉시 안내, [확인] 비활성, OTP 입력칸), static/js/asset.js (기준월 선택, 그래프 마우스 안내)
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
- **(10-19) 팀3 테이블은 DDL 파일(`onbank_loan.sql`)과 공용 DB 실제 구조가 다르다** (예: 상환 스케줄은 `LOAN_ID`가 아니라 `APPLICATION_ID`, 대출은 `loan`이 아니라 `loan_application`). 팀3 테이블을 SQL로 읽을 때는 `information_schema.columns`로 실제 컬럼을 먼저 확인
- **(10-19) 다른 팀 테이블 조회가 실패할 수 있는 Service에는 바깥 `@Transactional`을 붙이지 않는다.** Repository 안에서 난 예외가 바깥 트랜잭션을 rollback-only 로 만들어, 예외를 잡아도 뒤의 저장이 실패한다 (`AssetReportService`)
- **(10-19) 공용 DB를 조회만 해서 확인하는 방법**: mysql 명령줄로 SELECT 실행 (접속 정보는 `application-local.properties`에서 읽음). Spring 통합 테스트는 `contextLoads`가 실패하면 쓸 수 없다

## 8. 공용 DB 현재 데이터 (10-16 조회 기준)

- 회원: `user1`(member_id 1), `user2`, `user3`
- 테스트 계좌 (`user1` 소유, 비밀번호 1234): `TEST-0001` ACTIVE / `TEST-0002` FROZEN / `TEST-0003` PENDING. 모두 `account_type` NULL
- `members` 이체한도 컬럼: `user1`은 NULL → 기본 한도 적용
- 브라우저 확인으로 생긴 입금·출금·이체 거래가 남아 있다. 정리는 [data-team2-mysql.sql](data-team2-mysql.sql) 아래쪽 삭제 SQL
- `data-team2-mysql.sql` 15번째 줄 아이디는 각자 PC에서 바꿔 쓰고 **커밋하지 않는다**
- **(10-19 조회)** 계좌 5개(테스트 3 + 개설 `CHECKING` 2), 거래내역 6건, `asset_report` 0건. 팀3: `loan_application` 11건(user1 승인 4건, 8,100만 원), 예적금·카드·상환 스케줄 0건
- 자산 리포트 화면을 열면 `asset_report`에 회원당 최근 6개월 행이 생긴다 (정리: `DELETE FROM asset_report WHERE account_id = 회원ID`)

## 9. 자주 쓰는 명령 (`project/onbank` 폴더에서)

```
./gradlew compileJava                                   # 컴파일
./gradlew test --tests "org.doit.onbank.account.*"      # 팀2 테스트 170개 (DB 불필요)
./gradlew test --tests "org.doit.onbank.OnbankApplicationTests"   # 서버가 뜨는지 (공용 DB 연결, 읽기만)
./gradlew bootRun                                       # 서버 실행 (포트 80), PowerShell 은 .\gradlew.bat bootRun
```

- 로그인 http://localhost/login → 전계좌조회 http://localhost/mybank/accounts → 이체 http://localhost/mybank/transfer → 자산관리 http://localhost/mybank/assets (헤더 "자산관리") → OTP 발급 http://localhost/mybank/security/otp

## 10. 팀에 전달·협의할 것

| 대상 | 내용 | 상태 |
|---|---|---|
| 팀1 | members 이체한도 컬럼 ALTER | 완료 |
| 팀1 | progress 5장 "팀1에 전달" 항목 (한도 NOT NULL DEFAULT, `transfer_limit_request` 회원 기준 등) | 남음 |
| 팀1 | **본인확인 `request()/approve()/requireVerified()` 제공 시기, approve 때 팀2 `AccountManageService.activatePending(accountId)` 호출 요청** | 남음 (그동안 계좌는 본인확인 없이 즉시 개설) |
| 팀1 | OTP 기능 제공 시기 (이체에서 지금은 건너뜀) | **해결 (10-19)**: 팀1 `OtpService.verify()`를 이체에 연결 |
| 팀3 | `AccountRepository`/`AccountService`에 대출금 계좌 조회 메서드 추가함 (10-17, 이지훈). `account_type` NULL 계좌는 목록에 안 나옴 → 테스트 계좌만 해당이라 그대로 둠 (10-19 사용자 결정) | 확인함 |
| 팀4 | `fds_transfer_block`(사기 신고 계좌 이체 차단 기록) 추가됨. 이체 전 검사를 팀2가 호출해야 하는지, 신고 데이터 위치·차단 기준 확인 필요 | 남음 (다음 작업 후보) |
| 팀1 | **OTP 암호화 키(`onbank.otp.seed-key`) 공유 요청** | 남음 (10-19 문구 작성, 사용자 전달 예정) |
| 팀3 | **공용 DB `loan_repayment_schedule` 금액 3컬럼 `decimal(19,0)` ↔ 엔티티 `Long` 불일치로 서버 시작 실패** | 남음 (10-19 발견, 문구 작성) |
| 팀3 | 예약·정기이체가 호출할 이체 메서드 시그니처 (`TransferType.SCHEDULED/RECURRING`). **10-19 `transfer()`에 `otpCode` 인자 추가됨** → 예약·정기이체의 OTP 처리 방식 협의 | 남음 |
| 팀3 | 자산 리포트가 팀3 테이블 5개를 SQL로 읽는다 (`AssetReportRepository`). 컬럼 이름이 바뀌면 알려 달라고 요청 | 남음 |
| 팀3 | **예금·적금 가입 시 계좌 생성**: [team2-account-number-rule.md](team2-account-number-rule.md) 전달 (계좌번호 규칙, `AccountNumberGenerator` 사용, 초기값, 가입금 이체). 문서 5장 협의 4개(`product_type` 값, 예적금 계좌 비밀번호, 만기일 계산 기준, 대출 계좌 여부) | 문서 작성 완료, **사용자가 전달 예정** |
| 팀3 | 대출금 입금(`AccountService.deposit()`) | 남음 |
| 팀4 | 동결·해제·잠금 해제 화면에서 호출할 메서드 | **만듦 (10-16)**: `AccountManageService.freeze/unfreeze/close/unlockPassword/activatePending/rejectPending(accountId)`. 커밋 후 팀4에 전달 |
| 팀4 | 이체 완료 이벤트 클래스 이름과 필드 (`AccountTransferService` 8번 자리) | 남음 |
| 팀4 | `02_admin_ddl.sql` 참조 테이블 이름 | 해결됨 (v0.7에서 `account`, `account_transfer`, `transaction_history`로 수정) |
