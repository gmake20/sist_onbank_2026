# 팀원1(인증/보안) 진행 상황 기록

> 다른 컴퓨터에서 작업을 이어가기 위한 인수인계 메모입니다. (마지막 업데이트: 2026-10-10)
>
> 새 컴퓨터에서 Claude Code를 쓸 때는 첫 메시지로 **"docs/progress-auth.md 를 읽고 이어서 진행해주세요"** 라고 하면 됩니다.

---

## 0. 배경

- 학원 Spring Boot 과정 팀 프로젝트: **모의 인터넷뱅킹 (ONBANK)**, 4인 팀
- 나는 **팀원1 — 인증/보안/대시보드 파트**
- 기획 문서: [README.md](README.md) (전체 기획서), [auth.md](auth.md) (내 파트 구현 가이드), [wireframes.html](wireframes.html)
- 작업 브랜치: `scm` (원격 `origin/scm`)

### 담당 범위 (README 12장)
- `[핵심]` 회원가입(SMS 휴대폰 인증), 로그인/로그아웃, Spring Security 설정, 본인확인 신청/심사 로직, 대시보드, 마이페이지
- `[심화]` 아이디 찾기/비밀번호 재설정, 보안설정(로그인 이력·접속 기기), 지점/ATM 찾기(카카오맵)
- 이체용 **가상 OTP** (auth.md 4장)
- 테이블: `members`, `employee`, `identity_verification`, `login_log`, `employee_login_log`, `transfer_limit_request`, `otp_device`, `branch`, `branch_service`
  - 정의: `docs/schema-team1-mysql.sql`, 시드: `docs/data-team1-mysql.sql` (Oracle 원본 `schema-team1.sql`/`data-team1.sql`은 참고용)

### 다른 팀원에게 제공해야 하는 것
- `LoginMember` (컨트롤러에서 `@AuthenticationPrincipal LoginMember me`) — **1주차 최우선**
- `memberService.requireVerified()`, `VerificationService.request()/approve()/reject()`
- `otpService.verify(memberId, code)` — 팀원2 이체에서 호출
- `LoginEmployee` (관리자 컨트롤러에서 `@AuthenticationPrincipal LoginEmployee me`) — 팀원4용
- 시드 계정: 고객 user1(본인확인 완료)/user2(전)/user3(심사중) `User1234!`, 직원 E0001(SYSTEM_ADMIN)/E0002(MANAGER)/E0003(STAFF) `Staff1234!` — 해시 일치 확인함

---

## 1. 실제 프로젝트 환경 (문서와 다른 점 주의)

| 항목 | 실제 | 비고 |
|---|---|---|
| Spring Boot | **4.1.1** | auth.md 3장 제목·설정 예시를 Boot 4 기준으로 수정함 (코드 뼈대는 Boot 3 자료 기반 → 의존성 이름 확인하며 옮길 것) |
| Java | **21** (Gradle toolchain) | 새 PC에 JDK 21 필요 |
| DB | **MySQL** (cafe24 원격 DB, 팀 공용) | README 기획서도 MySQL로 수정함 (2026-10-10) |
| 빌드 | Gradle (`gradlew`) | |
| 포트 | `server.port=80` | 로컬에서 권한 문제 시 `--server.port=8080` 등으로 실행 |
| JPA | `ddl-auto=validate`, naming strategy 기본값 (2026-10-12 변경, 미커밋) | 테이블은 SQL 파일로 생성, 이름은 소문자+밑줄. README 13장 8번 |

---

## 2. 완료한 작업

| 커밋 | 내용 |
|---|---|
| `aa928d2` | DB 접속 정보를 `application-local.properties`(Git 제외)로 분리, `spring.profiles.active=local`, 견본 파일 `application-local.properties.example` 추가, `.gitignore`에 `application-local.properties`/`application-prod.properties` 추가 |
| `375f282` | `MainController`를 `src/test` → `src/main/java/org/doit/onbank/controller/`로 이동 (이전엔 `/`가 404), DB 시간대 `Asia/Seoul` 통일 (`serverTimezone` + `hibernate.jdbc.time_zone`) |

- 확인 결과: `gradlew test` 성공, 앱 실행 시 `local` 프로필로 DB 연결 OK, `/` 메인 화면 HTTP 200

---

## 3. 다음 할 일

### 2단계. Spring Security 의존성 추가 (build.gradle)
- `spring-boot-starter-security`, `spring-boot-starter-validation`, Thymeleaf Spring Security extras
- Boot 4 기준 정확한 의존성 이름 확인 필요
- ⚠️ Security를 추가하면 **모든 페이지가 로그인으로 막힘** → 3단계 `SecurityConfig`와 **같은 커밋**으로 올릴 것
- `build.gradle`은 공통 파일(팀원4 관리) → 수정 전 팀에 공지

### 3단계. 팀 공유용 인증 뼈대 (auth.md 6장 1~3번)
0. 공용 DB에 `schema-team1-mysql.sql` → `data-team1-mysql.sql` 실행 (팀 공지 후 1번만, 실행 전 MySQL 버전 8.0.16 이상인지 확인)
1. `Member` 엔티티 + enum(`MemberStatus`, `VerificationStatus`) + `MemberRepository` (role 없음, 잠금은 `loginLockedAt`)
2. `LoginMember`, `MemberDetailsService`
3. 최소 `SecurityConfig` (permitAll 경로, formLogin, POST logout)
4. 로그인 화면 `templates/auth/login.html`
5. 시드 계정 user1~3으로 로그인 확인 (validate 오류 나면 엔티티 필드 ↔ SQL 컬럼 확인)
6. 로그인/로그아웃 확인 후 팀에 사용법 공유
7. 직원 로그인(auth.md ⑥-2): `Employee`, `LoginEmployee`, 직원용 필터 체인, `RoleHierarchy` → E0001~3으로 확인 → 팀원4에게 공유

### 4단계 이후 (2주차)
회원가입 → SMS 인증(`ConsoleSmsSender`부터) → 5회 실패 잠금 + `LOGIN_LOG` → 세션 10분 / 중복 로그인 차단 → 아이디 찾기/비밀번호 재설정

---

## 4. 팀과 정해야 할 것 / 공지할 것

- [ ] 패키지 구조 (예: `org.doit.onbank.member`, `.security`, `.common`), 공통 예외 `BusinessException` 이름·위치 (팀원4)
- [ ] 브랜치 전략: 기획서는 `develop` + `feature/{파트}-{기능}`, 현재 원격에는 `main`, `scm`, `account`만 있음
- [ ] **합의 필요 (2026-10-12 문서 반영 완료)**: ① 직원 EMPLOYEE 분리 → 팀원3(`LOAN.reviewed_employee_id`)·팀원4(`NOTICE`/`INQUIRY`/`ADMIN_LOG`의 `~_employee_id`) FK 변경 ② SQL 파일 + `ddl-auto=validate` ③ 소문자 snake_case naming (application.properties 수정은 팀원4 동의 후 커밋)
- [x] README 기획서의 DB 표기 Oracle → MySQL 수정 (2026-10-10 완료, 팀에 공유 필요)
- [ ] **공지**: 각자 `application-local.properties.example`을 복사해 `application-local.properties`를 만들고, `serverTimezone=Asia/Seoul`로 설정
- [ ] SMS 서비스(솔라피 등) 가입·발신번호 등록 미리 신청 (승인에 시간 걸림)

---

## 5. 새 컴퓨터 세팅 순서

1. JDK 21 설치
2. `git clone` 후 `git checkout scm` (또는 기존 폴더면 `git pull`)
3. `project/onbank/src/main/resources/application-local.properties.example`을 복사해 **`application-local.properties`** 생성
   - DB 주소·계정·비밀번호는 이 파일에 적혀 있지 않음 (Git에 안 올라감) → **팀 채널이나 개인 메모에서 확인해 입력**
4. `project/onbank`에서 `gradlew test` 로 DB 연결 확인
5. `gradlew bootRun --args='--server.port=8080'` 후 http://localhost:8080 접속 확인
