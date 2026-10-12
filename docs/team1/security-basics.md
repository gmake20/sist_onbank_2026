# 보안 기초 개념 정리 (auth.md 읽기 전에)

> [auth.md](auth.md)에 나오는 보안 용어와 동작 원리를 처음부터 차례대로 설명합니다.
> 각 장 끝의 **"auth.md에서는"** 이 auth.md의 어느 부분과 연결되는지 알려 줍니다.
> 순서대로 읽는 것을 추천합니다. 뒤의 장은 앞의 장 내용을 알고 있다고 가정합니다.

---

## 목차

1. 인증과 인가 — 가장 먼저 구분할 두 단어
2. 세션과 쿠키 — 로그인 상태가 유지되는 원리
3. Spring Security의 큰 그림 — 필터(검문소)
4. 로그인 한 번이 처리되는 과정
5. `UserDetails`와 `LoginMember`
6. 비밀번호 저장 — 해시와 BCrypt
7. 접근 권한 설정 — `authorizeHttpRequests`
8. CSRF — 왜 로그아웃을 POST로 해야 하나
9. 세션 관련 보안 — 세션 고정 공격, 자동 로그아웃, 중복 로그인
10. 로그인 실패 처리 — 5회 잠금과 "아이디 또는 비밀번호" 문구
11. 휴대폰 인증번호의 보안 포인트
12. OTP — 해시가 아니라 암호화를 쓰는 이유
13. 비밀정보 관리 — API 키, DB 비밀번호
14. 용어 사전
15. 공부 순서 추천 (직접 해보기)

---

## 1. 인증과 인가 — 가장 먼저 구분할 두 단어

| 용어 | 영어 | 질문 | 은행 창구 비유 |
|---|---|---|---|
| **인증** | Authentication | **"너 누구야?"** | 신분증을 보여 주고 본인임을 확인받는 것 |
| **인가** | Authorization | **"너 이거 해도 돼?"** | 확인된 사람이 금고실(관리자 전용)에 들어갈 수 있는지 판단하는 것 |

- **로그인 = 인증**입니다. 아이디·비밀번호로 "이 사람이 회원 hong이 맞다"를 확인합니다.
- **권한 확인 = 인가**입니다. hong이 `/admin` 화면에 들어가려고 하면 "hong은 USER라서 안 됨"이라고 막습니다.
- 순서는 항상 **인증 → 인가**입니다. 누군지 모르면 권한도 판단할 수 없습니다.

Spring Security는 이 두 가지를 모두 맡아 주는 라이브러리입니다.

> **auth.md에서는**: 로그인(2장, 3장 ④⑤) = 인증, `SecurityConfig`의 `hasRole("MANAGER")` 같은 권한 규칙(3장 ⑥, ⑥-2) = 인가

---

## 2. 세션과 쿠키 — 로그인 상태가 유지되는 원리

### 문제: HTTP는 기억력이 없다
웹은 요청 하나하나가 독립적입니다. 로그인 요청을 보낸 다음 `/mybank`를 요청하면, 서버는 기본적으로 **방금 로그인한 그 사람인지 알 수 없습니다.**

### 해결: 번호표(세션 ID)를 나눠 준다
은행 번호표와 비슷합니다.

```
① 로그인 성공
   서버: "hong님 확인했습니다" → 서버 메모리에 [A1B2C3 = hong] 기록 (이것이 세션)
   서버 → 브라우저: "번호표 A1B2C3 가지고 계세요" (쿠키 JSESSIONID=A1B2C3)

② 다음 요청 (/mybank)
   브라우저 → 서버: 요청 + 쿠키 JSESSIONID=A1B2C3 (브라우저가 자동으로 붙여 보냄)
   서버: "A1B2C3번은 hong이네" → hong의 화면을 보여 줌

③ 로그아웃
   서버: [A1B2C3] 기록 삭제 → 번호표가 있어도 더 이상 쓸모없음
```

| 용어 | 뜻 |
|---|---|
| **세션(Session)** | 서버가 "이 번호표 = 이 사람"이라고 적어 둔 메모. **서버에** 있음 |
| **쿠키(Cookie)** | 브라우저가 보관하다가 요청마다 자동으로 보내는 작은 값. **브라우저에** 있음 |
| **JSESSIONID** | 톰캣(스프링 부트 내장 서버)이 쓰는 번호표 쿠키의 이름 |

### 중요한 결론
- **번호표(JSESSIONID)를 훔치면 그 사람으로 행세할 수 있습니다.** 그래서 9장의 "세션 고정 공격 방지" 같은 설정이 필요합니다.
- README에서 JWT 대신 세션을 쓰기로 한 이유: Thymeleaf처럼 서버가 화면을 만드는 구조에서는 세션이 가장 단순하고, Spring Security가 기본으로 지원하기 때문입니다.

> **auth.md에서는**: 1장 표의 "세션", 2장의 "세션 생성", 로그아웃의 "세션 무효화 + JSESSIONID 삭제", 휴대폰 인증번호를 **세션에 저장**하는 부분(3장 ③)

---

## 3. Spring Security의 큰 그림 — 필터(검문소)

### 필터란?
요청이 **컨트롤러에 도착하기 전에** 거치는 검문소입니다. Spring Security를 추가하면 이 검문소 여러 개가 줄지어 세워집니다. 이것을 **필터 체인(Filter Chain)** 이라고 부릅니다.

```
브라우저 요청
   │
   ▼
┌─────────────── Spring Security 필터 체인 ───────────────┐
│ [CSRF 검사]        POST 요청에 올바른 CSRF 토큰이 있나?    │
│ [로그인 처리]      POST /login 이면 여기서 로그인 처리      │
│ [로그아웃 처리]    POST /logout 이면 여기서 로그아웃 처리    │
│ [세션 확인]        쿠키의 번호표로 "누구인지" 복원         │
│ [권한 검사]        이 사람이 이 URL에 들어가도 되나?       │
└──────────────────────────────────────────────────────────┘
   │ (모두 통과한 요청만)
   ▼
컨트롤러 (@Controller)
```

(실제로는 필터가 더 많지만, 이 프로젝트에서 알아야 할 것은 위의 다섯 가지입니다.)

### 그래서 생기는 일
1. **Security를 추가하는 순간 모든 페이지가 로그인 화면으로 막힙니다.** 권한 검사 필터의 기본값이 "전부 로그인 필요"이기 때문입니다. → `SecurityConfig`로 "이 주소는 누구나 OK"를 알려 줘야 합니다. (progress-auth.md 2단계 경고가 이 뜻입니다.)
2. **로그인 컨트롤러를 직접 만들지 않습니다.** `POST /login`은 컨트롤러까지 가기 전에 로그인 처리 필터가 가로채서 처리합니다. 우리는 `GET /login`(로그인 **화면**을 보여 주는 것)만 만듭니다.
3. 대신 필터에게 **"우리 회원은 DB의 어디서 찾아라"** 만 알려 주면 됩니다. → 4장, 5장

> **auth.md에서는**: 2장 "※ 로그인 컨트롤러를 직접 만들지 않습니다", 3장 ⑥ `SecurityConfig` 전체

---

## 4. 로그인 한 번이 처리되는 과정

사용자가 아이디 `hong`, 비밀번호 `pass1234!`를 입력하고 로그인 버튼을 눌렀을 때입니다.

```
① 브라우저: POST /login  (loginId=hong, password=pass1234!, _csrf=토큰)
      │
② [CSRF 검사 필터] 토큰 확인 (8장)
      │
③ [로그인 처리 필터] "로그인 요청이구나" → 아이디·비밀번호를 꺼냄
      │
④ MemberDetailsService.loadUserByUsername("hong")   ← 우리가 만드는 부분
      │  DB에서 hong을 조회 → LoginMember로 포장해서 돌려줌
      │  (없는 아이디면 UsernameNotFoundException)
      │
⑤ 계정 상태 확인 (Security가 자동으로)
      │  LoginMember.isAccountNonLocked() == false → LockedException (잠김)
      │  LoginMember.isEnabled() == false          → DisabledException (정지/탈퇴)
      │
⑥ 비밀번호 비교 (Security가 자동으로)
      │  PasswordEncoder.matches("pass1234!", DB에 저장된 해시)
      │  틀리면 BadCredentialsException
      │
      ├── 성공 → ⑦ 세션 ID 교체(9장) → 세션에 LoginMember 저장
      │          → LoginSuccessHandler 실행  ← 우리가 만드는 부분
      │            (실패 횟수 0, LOGIN_LOG 기록, /mybank로 이동)
      │
      └── 실패 → LoginFailureHandler 실행  ← 우리가 만드는 부분
                 (실패 횟수 +1, 5회면 잠금, LOGIN_LOG 기록, /login?error=... 로 이동)
```

### 우리가 만드는 것 vs Security가 해 주는 것

| 단계 | 누가 | auth.md 위치 |
|---|---|---|
| 로그인 화면(HTML) | 우리 | 3장 ⑦ |
| 아이디·비밀번호 꺼내기 | Security | — |
| 회원 조회 (`loadUserByUsername`) | **우리** | 3장 ④ `MemberDetailsService` |
| 잠김·정지 확인 | Security (우리가 만든 `LoginMember`의 답을 보고) | 3장 ④ `LoginMember` |
| 비밀번호 비교 | Security (우리가 등록한 `PasswordEncoder`로) | 3장 ⑥ `passwordEncoder()` |
| 세션 저장 | Security | — |
| 성공·실패 후 처리 | **우리** | 3장 ⑤ 핸들러 |

> 💡 ⑤가 ⑥보다 먼저입니다. 그래서 **잠긴 계정은 비밀번호를 맞게 입력해도** 로그인되지 않고 "잠겼습니다" 안내가 나옵니다.

---

## 5. `UserDetails`와 `LoginMember`

### 왜 `Member`를 그대로 쓰지 않나?
Spring Security는 우리 프로젝트의 `Member` 클래스를 모릅니다. Security가 아는 것은 **`UserDetails`라는 약속(인터페이스)** 뿐입니다.

> "로그인 처리하려면 나한테 이 질문들에 답할 수 있는 객체를 줘"
> — 아이디는? 비밀번호(해시)는? 권한은? 잠겼어? 사용 가능해?

`LoginMember`는 `Member`의 정보를 **Security가 알아듣는 형태로 바꾼 통역사**입니다.

| `UserDetails`의 질문 (메서드) | `LoginMember`의 대답 |
|---|---|
| `getUsername()` 아이디는? | `loginId` |
| `getPassword()` 비밀번호는? | DB에 저장된 BCrypt 해시 |
| `getAuthorities()` 권한은? | 항상 `ROLE_USER` (고객은 모두 같은 권한) |
| `isAccountNonLocked()` 안 잠겼어? | 잠금 시각(`loginLockedAt`)이 비어 있으면 true |
| `isEnabled()` 사용 가능해? | 상태가 `ACTIVE`면 true (정지·탈퇴면 false) |

직원은 같은 역할을 하는 `LoginEmployee`가 따로 있고, 권한이 `ROLE_STAFF` / `ROLE_MANAGER` / `ROLE_SYSTEM_ADMIN` 중 하나입니다.

### `ROLE_` 접두사
- `getAuthorities()`는 `"ROLE_USER"`처럼 앞에 `ROLE_`을 붙여 돌려줍니다.
- `SecurityConfig`의 `hasRole("MANAGER")`는 내부적으로 `"ROLE_MANAGER"`를 찾습니다.
- 그래서 **한쪽에만 `ROLE_`을 붙이거나 빼면 권한 검사가 항상 실패합니다.** 자주 하는 실수입니다.

### 다른 팀원이 쓰는 방법
로그인에 성공하면 `LoginMember`가 세션에 저장됩니다. 컨트롤러에서는 이렇게 꺼내 씁니다.

```java
@GetMapping("/accounts")
public String list(@AuthenticationPrincipal LoginMember me) {
    Long memberId = me.getMemberId();   // 로그인한 회원 번호
    ...
}
```

이것이 progress-auth.md의 "1주차 최우선"인 이유입니다. 이 클래스가 없으면 팀원2·3·4가 "지금 로그인한 사람"을 알 방법이 없습니다.

> ⚠️ **화면에서 회원 번호를 받지 마세요.** `?memberId=3` 같은 값을 믿으면 주소만 바꿔서 남의 계좌를 볼 수 있습니다. 회원 번호는 항상 `@AuthenticationPrincipal`에서 꺼냅니다.

---

## 6. 비밀번호 저장 — 해시와 BCrypt

### 해시(Hash)란?
**한 방향으로만 계산되는 변환**입니다.

```
"pass1234!"  ──해시──▶  "$2a$10$N9qo8uLOickgx2ZMRZoMy..."   (가능)
"$2a$10$N9qo8..."  ──▶  "pass1234!"                           (불가능)
```

- DB에는 해시값만 저장합니다. DB가 유출돼도 원래 비밀번호를 바로 알 수 없습니다.
- 로그인할 때는 **입력값을 다시 해시해서 저장된 해시와 비교**합니다. 그래서 `equals`로 직접 비교하지 않고 `passwordEncoder.matches(입력값, 저장된해시)`를 씁니다.

### 해시 vs 암호화 (12장에서 다시 나옴)

| | 해시 | 암호화 |
|---|---|---|
| 되돌리기 | **불가능** | 키가 있으면 **가능** |
| 용도 | 비밀번호 (원래 값을 알 필요가 없음) | OTP 시드 (원래 값으로 계산해야 함) |
| 이 프로젝트 | BCrypt | AES |

### 왜 하필 BCrypt인가?
1. **솔트(Salt)가 자동으로 들어갑니다.** 같은 비밀번호라도 매번 다른 해시가 나옵니다. 그래서 "자주 쓰는 비밀번호의 해시 목록"을 미리 만들어 맞춰 보는 공격이 통하지 않습니다.
   ```
   encode("pass1234!") → $2a$10$abc...   (첫 번째)
   encode("pass1234!") → $2a$10$xyz...   (두 번째, 다름!)
   matches("pass1234!", 둘 중 아무거나) → 둘 다 true
   ```
2. **일부러 느립니다.** 한 번 계산에 수십~수백 밀리초가 걸리도록 만들어져서, 해커가 비밀번호를 수억 번 대입해 보기 어렵습니다.
3. 결과가 항상 **60자**입니다. → `Member.password` 컬럼이 `length = 60`인 이유입니다.

### 시드 계정 만들 때 주의
SQL로 직접 넣을 때도 비밀번호 칸에는 **평문이 아니라 BCrypt 해시**를 넣어야 합니다. 평문을 넣으면 `matches`가 실패해서 로그인되지 않습니다.

> **auth.md에서는**: 3장 ② `passwordEncoder.encode(...)`, 3장 ⑥ `BCryptPasswordEncoder`, 5장 2번·5번

---

## 7. 접근 권한 설정 — `authorizeHttpRequests`

```java
// 고객용 체인
.authorizeHttpRequests(auth -> auth
    .requestMatchers("/", "/signup", "/login", ...).permitAll()   // ① 누구나
    .anyRequest().hasRole("USER"))                                 // ② 나머지는 고객만

// 직원용 체인 (/admin/** 전용)
.authorizeHttpRequests(auth -> auth
    .requestMatchers("/admin/login").permitAll()                   // ① 직원 로그인 화면
    .requestMatchers("/admin/employees/**").hasRole("SYSTEM_ADMIN")// ② 시스템 관리자만
    .requestMatchers("/admin/approvals/**").hasRole("MANAGER")     // ③ 책임자 이상
    .anyRequest().hasRole("STAFF"))                                // ④ 나머지는 직원 누구나
```

| 표현 | 뜻 |
|---|---|
| `permitAll()` | 로그인 안 해도 접근 가능 |
| `hasRole("MANAGER")` | `ROLE_MANAGER` 권한이 있어야 함 (상하 관계가 있으면 그 위 권한도 통과, 아래 참고) |
| `authenticated()` | 로그인만 했으면 누구나 |
| `anyRequest()` | 위에서 언급하지 않은 나머지 모든 주소 |
| `/admin/**` | `/admin`으로 시작하는 모든 주소 (`**` = 하위 경로 전부) |

### 위에서부터 차례대로 검사합니다
첫 번째로 맞는 규칙이 적용되고 아래는 보지 않습니다. 그래서 **구체적인 규칙을 위에, `anyRequest()`는 맨 아래**에 둡니다.

### `permitAll`에 꼭 넣어야 하는 것
- 로그인 전에 쓰는 화면: `/`, `/login`, `/signup`, 아이디 찾기, 비밀번호 재설정
- 로그인 전에 호출하는 API: `/api/phone/**` (회원가입 중 인증번호 발송)
- **정적 파일**: `/css/**`, `/js/**`, `/images/**` — 빠뜨리면 로그인 화면이 CSS 없이 깨져 보입니다.
- `/error` — 빠뜨리면 에러가 났을 때 에러 화면 대신 로그인 화면으로 튕겨서 원인을 찾기 어렵습니다.
- `/otp-device` — 가상 OTP 기기 페이지 (4장)

### 필터 체인이 2개인 이유 — 고객과 직원의 분리
이 프로젝트는 고객(`MEMBER`)과 직원(`EMPLOYEE`)을 **다른 테이블, 다른 로그인 화면**으로 나눴습니다. 그래서 3장의 "검문소 줄"도 두 줄입니다.

```
요청 주소가 /admin/** 인가?
 ├─ 예   → [직원용 체인]  로그인 화면 /admin/login, EMPLOYEE에서 사번으로 조회
 └─ 아니오 → [고객용 체인]  로그인 화면 /login,       MEMBER에서 아이디로 조회
```
- `securityMatcher("/admin/**")`: "이 체인은 이 주소만 담당한다"는 표시
- `@Order(1)`, `@Order(2)`: 숫자가 작은 체인을 먼저 확인
- 고객은 `/admin/**`에 들어갈 수 없고(고객에게는 `ROLE_STAFF`가 없음), 직원은 `/mybank`에 들어갈 수 없습니다(직원에게는 `ROLE_USER`가 없음).

### 권한의 상하 관계 — `RoleHierarchy`
직원 권한은 STAFF < MANAGER < SYSTEM_ADMIN 3단계입니다. 그냥 두면 Security는 이 세 개를 **서로 무관한 권한**으로 봅니다. 즉 MANAGER가 `hasRole("STAFF")` 화면에 들어가지 못합니다.
`RoleHierarchy`로 "SYSTEM_ADMIN은 MANAGER를 포함, MANAGER는 STAFF를 포함"이라고 등록하면, 위 권한이 아래 권한의 화면을 모두 쓸 수 있습니다.

> **auth.md에서는**: 3장 ⑥ 코드 윗부분, 3장 ⑥-2, README 8-1장 권한표

---

## 8. CSRF — 왜 로그아웃을 POST로 해야 하나

### CSRF 공격이란?
**C**ross-**S**ite **R**equest **F**orgery, "사이트 간 요청 위조"입니다.

```
① hong이 ONBANK에 로그인한 상태 (브라우저에 JSESSIONID 쿠키 있음)
② hong이 같은 브라우저로 악성 사이트 evil.com에 접속
③ evil.com 안에 숨겨진 폼:
     <form action="https://onbank.com/transfer" method="post">
       <input name="to" value="해커계좌"><input name="amount" value="1000000">
     </form>
     <script>document.forms[0].submit()</script>
④ 브라우저는 onbank.com으로 가는 요청에 hong의 쿠키를 자동으로 붙임
⑤ ONBANK 서버: "hong의 정상적인 이체 요청이네" → 이체 실행 😱
```

핵심은 **브라우저가 쿠키를 자동으로 붙여 준다**는 점입니다(2장). 서버는 요청이 ONBANK 화면에서 왔는지, evil.com에서 왔는지 쿠키만으로는 구분할 수 없습니다.

### 방어: CSRF 토큰
서버가 **ONBANK 화면에만** 비밀 값(토큰)을 숨겨 둡니다. POST 요청에 그 토큰이 없거나 틀리면 거절합니다(403 에러). evil.com은 ONBANK 화면 안의 토큰을 읽을 수 없으므로 공격이 실패합니다.

```html
<!-- th:action 을 쓰면 Thymeleaf가 자동으로 아래 숨은 칸을 넣어 줍니다 -->
<form action="/logout" method="post">
  <input type="hidden" name="_csrf" value="4f8a2c...">
</form>
```

### 꼭 기억할 규칙 3가지
1. **폼에는 `action` 대신 `th:action`을 씁니다.** 그래야 토큰이 자동으로 들어갑니다. 그냥 `action="/login"`이라고 쓰면 403 에러가 납니다.
2. **로그아웃은 POST 폼으로 만듭니다.** CSRF 보호는 POST에만 걸리기 때문에, GET 링크(`<a href="/logout">`) 로그아웃을 허용하면 evil.com이 `<img src="onbank.com/logout">` 한 줄로 사용자를 로그아웃시킬 수 있습니다. 그래서 Security는 CSRF가 켜져 있으면 GET 로그아웃을 받지 않습니다.
3. **fetch로 보내는 POST에도 토큰이 필요합니다.** ⚠️ auth.md에는 빠져 있는 내용입니다. 회원가입 화면에서 `fetch('/api/phone/send', {method:'POST'})`를 그냥 호출하면 **403 에러**가 납니다. 화면에 토큰을 심어 두고 요청 헤더에 넣어 보내야 합니다.

```html
<!-- 공통 레이아웃 <head> 안 -->
<meta name="_csrf" th:content="${_csrf.token}">
<meta name="_csrf_header" th:content="${_csrf.headerName}">
```

```javascript
const token  = document.querySelector('meta[name="_csrf"]').content;
const header = document.querySelector('meta[name="_csrf_header"]').content;

fetch('/api/phone/send', {
  method: 'POST',
  headers: { [header]: token, 'Content-Type': 'application/x-www-form-urlencoded' },
  body: new URLSearchParams({ phone: '01012345678' })
});
```

> 💡 이 방법은 팀원2(계좌주 확인 API)와 팀원4(알림)도 똑같이 써야 하므로, 공통 레이아웃에 meta 태그를 넣어 두고 팀에 공유하면 좋습니다.

> **auth.md에서는**: 3장 ⑥ 로그아웃 주석 "POST만 허용 (CSRF 보호)", 3장 ⑦ 첫 줄, 5장 3번

---

## 9. 세션 관련 보안

### 9-1. 세션 고정 공격 → `sessionFixation().changeSessionId()`

```
① 해커가 ONBANK에 접속해 번호표 X를 받음 (로그인은 안 함)
② 어떤 방법으로 hong의 브라우저에 번호표 X를 심음
③ hong이 번호표 X를 가진 채로 로그인 → 서버: [X = hong]
④ 해커도 번호표 X를 갖고 있으므로 hong으로 행세 😱
```

**방어**: 로그인에 성공하는 순간 번호표를 **새것으로 바꿔 줍니다.** 해커가 가진 X는 쓸모없어집니다.
이 설정은 사실 Spring Security의 **기본 동작**이라 따로 쓰지 않아도 됩니다. auth.md에서는 의미를 보여 주려고 명시해 둔 것입니다.

### 9-2. 자동 로그아웃 → `server.servlet.session.timeout=10m`
- 마지막 요청 후 10분 동안 아무 요청이 없으면 세션이 사라집니다.
- PC방이나 회사에서 로그아웃을 잊고 자리를 떠도 피해를 줄여 줍니다.
- 은행 사이트의 "남은 시간 09:59 [연장]" 표시는 화면(JavaScript)에서 따로 만드는 기능입니다. 연장 버튼은 서버에 아무 요청이나 한 번 보내 시간을 다시 10분으로 늘리는 방식입니다.

### 9-3. 중복 로그인 차단 → `maximumSessions(1)`
- 한 계정은 동시에 한 곳에서만 로그인됩니다.
- 기본 동작: **나중에 로그인한 쪽이 이기고**, 먼저 로그인한 쪽은 다음 요청 때 `/login?error=duplicate`로 쫓겨납니다.
- 반대로 "이미 로그인 중이면 새 로그인을 막기"를 원하면 `maxSessionsPreventsLogin(true)`를 씁니다.

> **auth.md에서는**: 1장 표 "세션" 줄, 3장 ⑥ `sessionManagement` 부분과 그 아래 설명

---

## 10. 로그인 실패 처리

### 10-1. "아이디 또는 비밀번호가 올바르지 않습니다"
"없는 아이디입니다" / "비밀번호가 틀렸습니다"를 나눠서 알려 주면, 해커가 **어떤 아이디가 가입되어 있는지** 알아낼 수 있습니다. 그래서 둘 다 같은 문구로 안내합니다.
Spring Security도 기본적으로 "없는 아이디(`UsernameNotFoundException`)"를 "비밀번호 틀림(`BadCredentialsException`)"으로 바꿔서 실패 핸들러에 넘겨 줍니다.

### 10-2. 5회 실패 잠금
- 비밀번호를 무작위로 계속 넣어 보는 공격(무차별 대입)을 막습니다.
- 흐름: 실패 핸들러 → `loginFailCount + 1` → 5가 되면 `loginLockedAt`에 현재 시각 기록 → 다음 로그인 때 `isAccountNonLocked()`가 false → `LockedException`(4장 ⑤)
- 성공하면 `loginFailCount`를 0으로 되돌립니다.
- **잠금 해제 방법**을 꼭 만들어야 합니다. 안 그러면 잠긴 회원은 영원히 못 들어옵니다. (auth.md 5장 4번: 비밀번호 재설정 성공 시 해제)

### 10-3. 왜 `LoginService`를 따로 두나?
실패 핸들러는 **트랜잭션 밖**에서 실행됩니다. 트랜잭션 없이 엔티티 값을 바꾸면 DB에 저장되지 않습니다. 그래서 `@Transactional`이 붙은 `LoginService`에 "실패 횟수 올리고 이력 남기기"를 맡깁니다.

> **auth.md에서는**: 3장 ⑤ 전체, 5장 1번·4번

---

## 11. 휴대폰 인증번호의 보안 포인트

| auth.md 코드 | 이유 |
|---|---|
| `SecureRandom` 사용 | 일반 `Random`은 이전 값들로 다음 값을 예측할 수 있습니다. 보안용 숫자는 예측 불가능한 `SecureRandom`으로 만듭니다. |
| 유효시간 3분, 입력 5회 제한 | 6자리 숫자는 100만 가지뿐이라, 제한이 없으면 전부 넣어 보면 맞힙니다. |
| 회원가입 POST 때 서버에서 `phoneVerified` 재확인 | 화면(JavaScript)에서 "인증 완료" 표시만 보고 넘어가면, 개발자 도구로 화면을 조작해서 인증을 건너뛸 수 있습니다. **보안 검사는 항상 서버에서** 합니다. |
| `MessageDigest.isEqual(...)`로 비교 | `String.equals`는 첫 글자가 다르면 바로 멈춰서, 응답 시간 차이로 몇 번째 글자까지 맞았는지 추측할 여지가 있습니다(타이밍 공격). `isEqual`은 항상 끝까지 비교합니다. 실제 위험은 작지만 좋은 습관입니다. |
| 재발송 60초, 하루 5회 제한 | 문자 한 통마다 **실제 돈이 나갑니다.** 누군가 발송 버튼을 반복 호출하면 충전 금액이 바닥납니다. |
| 로그에 인증번호를 남기지 않음 | 로그는 여러 사람이 봅니다. (개발용 `ConsoleSmsSender`는 예외) |

> **auth.md에서는**: 3장 ③ "동작 규칙"과 `PhoneVerificationService`

---

## 12. OTP — 해시가 아니라 암호화를 쓰는 이유

### OTP 원리 (TOTP)
```
기기:   시드(비밀키) + 현재 시각(30초 단위) → 계산 → 482913
서버:   같은 시드   + 같은 시각            → 계산 → 482913 → 입력값과 같으면 통과
```
기기와 서버는 서로 통신하지 않습니다. **같은 재료(시드 + 시각)로 각자 계산**하므로 결과가 같습니다.

### 비밀번호는 해시인데, 시드는 왜 AES 암호화인가?
- 비밀번호: 서버는 원래 값을 몰라도 됩니다. "입력값을 해시한 것 = 저장된 해시"만 확인하면 됩니다.
- 시드: 서버가 **원래 시드로 직접 계산**해야 합니다. 해시하면 되돌릴 수 없어 계산할 수 없습니다.
- 그래서 시드는 **되돌릴 수 있는 암호화(AES)** 로 저장하고, 암호화 키는 Git이 아니라 환경변수에 둡니다. DB만 유출되면 키가 없어 시드를 알 수 없습니다.

### 나머지 규칙
| 규칙 | 이유 |
|---|---|
| 앞뒤 30초 허용 | 휴대폰 시계와 서버 시계가 조금 어긋날 수 있음 |
| 한 번 쓴 번호 재사용 금지 | 어깨너머로 본 번호를 30초 안에 다시 쓰는 것을 막음 |
| 10회 오류 시 정지 | 6자리 무작위 대입 방지 (로그인 5회 잠금과 같은 원리) |
| 오류 횟수는 `REQUIRES_NEW` | OTP가 틀려서 이체가 취소(롤백)될 때 **오류 횟수 증가까지 같이 취소되면** 무한히 시도할 수 있음. 그래서 오류 횟수는 별도 트랜잭션으로 먼저 저장 |
| 등록 코드는 딱 1번만 표시 | 실물 OTP 기기를 건네주는 것과 같음. 다시 보여 주면 로그인만 한 해커도 기기를 복제할 수 있음 |

> **auth.md에서는**: 4장 전체

---

## 13. 비밀정보 관리

| 비밀정보 | 보관 위치 | Git |
|---|---|---|
| DB 주소·계정·비밀번호 | `application-local.properties` (지금 이미 이렇게 하고 있음) | ❌ |
| SMS API 키·시크릿 | 배포 서버 환경변수 → `application-prod.properties`에서 `${SMS_API_KEY}`로 참조 | ❌ (참조 표현만 ⭕) |
| OTP 시드 암호화 키 | 환경변수 | ❌ |

- `${SMS_API_KEY}`는 "실행할 때 환경변수 `SMS_API_KEY`의 값을 넣어라"라는 뜻입니다. 파일에는 이름만 있고 실제 값은 없습니다.
- GitHub 공개 저장소에 키가 올라가면 자동으로 키를 수집하는 프로그램이 몇 분 안에 찾아냅니다. 실수로 올렸다면 커밋을 지우는 것보다 **키를 즉시 재발급(폐기)** 하는 것이 먼저입니다.

---

## 14. 용어 사전

| 용어 | 한 줄 설명 | 관련 장 |
|---|---|---|
| 인증 (Authentication) | 누구인지 확인 (로그인) | 1 |
| 인가 (Authorization) | 무엇을 할 수 있는지 확인 (권한) | 1, 7 |
| 세션 (Session) | 서버가 기억하는 "번호표 = 사람" 메모 | 2 |
| 쿠키 (Cookie) | 브라우저가 요청마다 자동으로 보내는 작은 값 | 2 |
| JSESSIONID | 세션 번호표 쿠키 이름 | 2 |
| 필터 체인 (Filter Chain) | 컨트롤러 앞의 검문소 줄 | 3 |
| `UserDetailsService` | "아이디로 회원 찾는 방법"을 Security에 알려 주는 인터페이스 | 4, 5 |
| `UserDetails` | Security가 이해하는 회원 정보 형식 | 5 |
| `LoginMember` | 우리 프로젝트의 `UserDetails` 구현체 | 5 |
| `@AuthenticationPrincipal` | 컨트롤러에서 로그인 회원을 꺼내는 어노테이션 | 5 |
| `ROLE_` | 권한 이름 앞에 붙는 접두사 | 5, 7 |
| 해시 (Hash) | 되돌릴 수 없는 변환 | 6 |
| BCrypt | 비밀번호용 해시 방식 (솔트 자동, 일부러 느림, 60자) | 6 |
| 솔트 (Salt) | 같은 비밀번호도 다른 해시가 나오게 섞는 무작위 값 | 6 |
| `PasswordEncoder` | 비밀번호 해시·비교 담당 (`encode`, `matches`) | 6 |
| `permitAll` / `hasRole` | 누구나 허용 / 특정 권한만 허용 | 7 |
| CSRF | 다른 사이트가 내 쿠키를 이용해 요청을 위조하는 공격 | 8 |
| CSRF 토큰 | 우리 화면에만 있는 비밀 값, POST마다 확인 | 8 |
| 세션 고정 공격 | 미리 심어 둔 번호표로 로그인 후 세션을 가로채는 공격 | 9 |
| 무차별 대입 (Brute Force) | 가능한 값을 전부 넣어 보는 공격 | 10, 11 |
| `SecureRandom` | 예측할 수 없는 난수 생성기 | 11 |
| 타이밍 공격 | 응답 시간 차이로 비밀 값을 추측하는 공격 | 11 |
| TOTP | 시드 + 현재 시각으로 계산하는 OTP 표준 | 12 |
| 시드 (Seed) | OTP 계산에 쓰는 비밀키 | 12 |
| AES | 키로 되돌릴 수 있는 암호화 방식 | 12 |
| `REQUIRES_NEW` | 바깥 트랜잭션과 별개로 따로 커밋하는 트랜잭션 | 10, 12 |

---

## 15. 공부 순서 추천 (직접 해보기)

글로만 읽는 것보다 **하나씩 붙여 보면서 화면 변화를 확인**하는 것이 훨씬 빨리 이해됩니다. progress-auth.md의 2~3단계와 순서가 같습니다.

| 순서 | 해볼 것 | 확인할 것 | 관련 장 |
|---|---|---|---|
| 1 | `build.gradle`에 Security 의존성만 추가하고 실행 | 모든 페이지가 Spring 기본 로그인 화면으로 바뀜. 콘솔에 임시 비밀번호가 출력됨 (아이디 `user`) | 3 |
| 2 | 그 임시 계정으로 로그인 | 브라우저 개발자 도구(F12) → Application → Cookies에서 `JSESSIONID` 확인. 로그인 전후로 값이 바뀌는지 확인 | 2, 9 |
| 3 | `SecurityConfig`에 `permitAll("/")`만 추가 | `/`는 열리고 다른 주소는 막힘 | 7 |
| 4 | `Member`, `LoginMember`, `MemberDetailsService` 작성 + BCrypt 해시로 시드 계정 추가 | 우리 DB 계정으로 로그인됨 | 4, 5, 6 |
| 5 | 로그인 화면을 `action="/login"`으로 만들어 보기 → `th:action`으로 고치기 | 처음엔 403, 고치면 정상. 개발자 도구에서 숨은 `_csrf` 칸 확인 | 8 |
| 6 | 컨트롤러에 `@AuthenticationPrincipal LoginMember me` 넣고 `me.getName()` 출력 | 로그인한 사람 이름이 나옴 → 팀 공유 | 5 |
| 7 | 브라우저 두 개(일반 + 시크릿 창)로 같은 계정 로그인 | `maximumSessions(1)` 적용 후 먼저 로그인한 쪽이 쫓겨남 | 9 |

여기까지 해 보면 auth.md 3장 ④~⑦의 코드가 대부분 읽힐 것입니다.
