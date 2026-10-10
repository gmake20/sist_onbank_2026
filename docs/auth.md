# 회원가입 · 로그인/로그아웃 · OTP 구현 가이드

> 담당: 팀원1 (인증/보안 파트) | 관련 문서: [README.md](README.md) 5-2장, 6장, 8-1장, 11-1장, 12장
>
> 이 문서는 **기획 단계의 참고 자료**입니다. 코드는 구조를 이해하기 위한 뼈대이며, 실제 구현은 프로젝트 초기 세팅(`build.gradle`, 패키지 구조 등)이 확정된 뒤 진행합니다.

---

## 1. 실제 은행 vs 우리 프로젝트

| 구분 | 실제 은행 | 우리 프로젝트 (기획서 기준) |
|---|---|---|
| **회원가입** | 휴대폰 본인인증(통신사 PASS 등) → 약관 동의 → 아이디/비밀번호 생성. 직원 승인 없음 | **문자메시지(SMS) API로 실제 인증번호를 발송**해 휴대폰 인증 → 가입 즉시 `ACTIVE` (상세는 3장 ③ 참고) |
| **로그인 수단** | 공동인증서 / 금융인증서 / 간편비밀번호 / 생체인증 / 아이디·비밀번호 | **아이디·비밀번호만** (Spring Security 폼 로그인). 간편비밀번호·생체인증은 구현하지 않음 (1-1 참고) |
| **이체 인증** | 계좌 비밀번호 + 보안매체(OTP 기기, 모바일 OTP 등) | 계좌 비밀번호 + **가상 OTP** (웹으로 구현한 OTP 기기, 4장 참고) |
| **로그인 실패** | 비밀번호 5회 오류 시 잠금 → 본인인증 후 재설정 또는 영업점 방문 | **5회 실패 시 잠금**, 해제는 비밀번호 재설정(휴대폰 SMS 인증) 또는 관리자 |
| **세션** | 약 10분 미사용 시 자동 로그아웃(연장 버튼 제공), **중복 로그인 차단** | 세션 타임아웃 10분 + 동시 로그인 1개 제한 |
| **로그인 후** | "마지막 접속 일시/IP" 표시 | `LOGIN_LOG` 기록 → 보안설정 화면(13.3)에서 조회 |
| **로그아웃** | 서버 세션 파기 | `POST /logout` → 세션 무효화 + `JSESSIONID` 쿠키 삭제 |

**핵심**: 은행 인증이 화려해 보여도, 우리가 진짜로 구현하는 것은 **아이디/비밀번호 + 세션**입니다. 그 위에 은행다운 규칙(**5회 잠금, 자동 로그아웃, 중복 로그인 차단, 로그인 이력**)을 얹고, 돈이 움직이는 이체에는 **가상 OTP**를 추가로 요구하는 구조입니다.

### 1-1. 인증 수단 정책 (확정)

> **"로그인은 아이디/비밀번호, 이체는 OTP"** 구성으로 확정합니다.

| 상황 | 인증 수단 | 담당 |
|---|---|---|
| 회원가입 | 휴대폰 SMS 인증 (실제 발송) | 팀원1 |
| 로그인 | 아이디/비밀번호 | 팀원1 |
| 이체 | 계좌 비밀번호 + 가상 OTP 6자리 | 팀원2 (OTP 검증은 팀원1 모듈 호출) |
| 아이디 찾기, 비밀번호 재설정, 로그인 잠금 해제 | 휴대폰 SMS 인증 | 팀원1 |

실제 은행에서도 OTP는 로그인보다 **이체처럼 돈이 움직일 때 쓰는 보안매체**입니다. 로그인에 OTP를 요구하면 OTP를 아직 등록하지 않은 신규 회원이 로그인할 수 없으므로, 이체에만 적용합니다.

**구현하지 않는 인증 수단과 이유**

| 인증 수단 | 제외 이유 |
|---|---|
| 공동인증서 / 금융인증서 | 인증기관·금융결제원과 계약이 필요해 학생 팀은 사용할 수 없음 |
| 생체인증 (패스키/WebAuthn) | 배포 서버에 도메인과 HTTPS가 반드시 필요하고, 은행 업무 흐름보다는 브라우저 기능 활용에 가까움 |
| 간편비밀번호 (PIN 6자리) | 기기 등록·기기 토큰·별도 로그인 처리기가 필요해 작업량이 큼. 로그인 수단을 하나로 단순하게 유지 |
| 실물 OTP 기기 | 기기 구매·배송 부담. 같은 원리(TOTP)의 **가상 OTP**로 대체 |

---

## 2. 전체 동작 흐름

```
[회원가입]
브라우저 ─ GET /signup ──▶ 가입 폼(Thymeleaf)
        ─ fetch POST /api/phone/send ──▶ 재발송 제한 확인 → 인증번호 6자리 생성 → 세션에 저장(3분)
                                         → 문자메시지 API로 실제 SMS 발송 → 사용자 휴대폰에 수신
        ─ fetch POST /api/phone/verify ─▶ 만료·시도 횟수(5회) 확인 → 번호 일치 → 세션에 "phoneVerified=true"
        ─ POST /signup ──▶ 검증(형식, 아이디 중복, 휴대폰 인증 여부)
                           → BCrypt로 비밀번호 암호화 → MEMBER 저장(ACTIVE, 실명확인 NONE)
                           → /login 으로 redirect

[로그인]  ※ 로그인 컨트롤러를 직접 만들지 않습니다. Spring Security가 처리합니다.
POST /login ──▶ UsernamePasswordAuthenticationFilter
              → MemberDetailsService.loadUserByUsername(아이디)  (DB 조회)
              → BCrypt로 비밀번호 비교, 잠금/탈퇴 여부 확인
              ├ 성공 → 세션 생성, 실패 횟수 0으로 초기화, LOGIN_LOG 기록 → USER는 /mybank, ADMIN은 /admin
              └ 실패 → 실패 횟수 +1(5회면 잠금), LOGIN_LOG 기록 → /login?error

[로그아웃]
POST /logout ──▶ 세션 무효화 + JSESSIONID 삭제 → / 로 이동
```

---

## 3. 구성 요소별 설명과 코드 뼈대 (Spring Boot 3 / Spring Security 6)

| 구성 요소 | 역할 |
|---|---|
| `Member` | 회원 엔티티 (`MEMBER` 테이블) |
| `SignupForm`, `MemberService`, `SignupController` | 회원가입 입력 검증, 저장 |
| `PhoneVerifyApiController`, `PhoneVerificationService`, `SmsSender` | 휴대폰 인증 (REST + fetch, 문자메시지 API로 실제 발송) |
| `MemberDetailsService`, `LoginMember` | Spring Security가 로그인 시 회원을 조회하는 통로 |
| `LoginSuccessHandler`, `LoginFailureHandler`, `LoginService` | 로그인 결과 처리 (5회 잠금, 이력 기록, 권한별 이동) |
| `SecurityConfig` | 접근 권한, 로그인/로그아웃, 세션 규칙 설정 |

### ① 회원 엔티티: `MEMBER`

- 권한(`role`), 회원 상태(`status`), 실명확인 상태(`verificationStatus`)를 모두 enum으로 관리합니다.
- 로그인 실패 횟수(`loginFailCount`)를 엔티티가 직접 관리하고, 5회째에 `LOCKED`로 전환합니다.

- MySQL 기준으로 작성했습니다. 테이블 이름과 PK 생성 방식은 아래 "MySQL 사용 시 주의점"을 참고하세요.

```java
@Entity @Table(name = "members")   // MEMBER는 MySQL 8 예약어라 테이블명을 바꿈
@Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)   // MySQL AUTO_INCREMENT
    private Long id;

    @Column(unique = true, nullable = false, length = 20) private String loginId;
    @Column(nullable = false, length = 60) private String password;      // BCrypt 해시(60자)
    @Column(length = 50) private String name;
    @Column(unique = true, length = 11) private String phone;
    private LocalDate birthDate;

    @Enumerated(EnumType.STRING) private Role role;                       // USER, ADMIN
    @Enumerated(EnumType.STRING) private MemberStatus status;             // ACTIVE, LOCKED, SUSPENDED, WITHDRAWN
    @Enumerated(EnumType.STRING) private VerificationStatus verificationStatus; // NONE, PENDING, VERIFIED, REJECTED
    private int loginFailCount;
    private LocalDateTime createdAt;

    public static Member signup(String loginId, String encodedPw, String name, String phone, LocalDate birth) {
        Member m = new Member();
        m.loginId = loginId; m.password = encodedPw; m.name = name; m.phone = phone; m.birthDate = birth;
        m.role = Role.USER; m.status = MemberStatus.ACTIVE;
        m.verificationStatus = VerificationStatus.NONE; m.createdAt = LocalDateTime.now();
        return m;
    }
    public void loginSucceeded() { this.loginFailCount = 0; }
    public void loginFailed() {
        if (++this.loginFailCount >= 5) this.status = MemberStatus.LOCKED;
    }
}
```

#### MySQL 사용 시 주의점

| 항목 | 문제 | 대응 |
|---|---|---|
| **테이블명 `MEMBER`** | MySQL 8.0.17부터 `MEMBER`가 **예약어**(JSON의 `MEMBER OF` 연산자)가 되어, `create table member ...`가 문법 오류로 실패합니다. | `@Table(name = "members")`처럼 이름을 바꿉니다. (백틱으로 감싸는 방법도 있지만 모든 SQL에 신경 써야 하므로 이름 변경을 권장) |
| **PK 생성 방식** | MySQL에는 Oracle 같은 시퀀스가 없습니다. `GenerationType.SEQUENCE`를 쓰면 Hibernate가 시퀀스를 흉내 내는 별도 테이블을 만들어 비효율적입니다. | `GenerationType.IDENTITY`(AUTO_INCREMENT)를 씁니다. |
| **문자셋** | 이름 등 한글과 이모지를 저장하려면 `utf8mb4`가 필요합니다. | DB·테이블을 `utf8mb4`로 만듭니다. (MySQL 8 기본값) |
| **대소문자 비교** | 기본 정렬 규칙(`utf8mb4_0900_ai_ci`)은 **대소문자를 구분하지 않아** `abc`와 `ABC`를 같은 아이디로 봅니다. | 아이디를 영문 소문자로만 받도록 검증하므로(`SignupForm`) 문제없습니다. 대문자를 허용하려면 컬럼 정렬 규칙을 `utf8mb4_bin`으로 지정합니다. |
| **시간대** | 서버(AWS는 UTC)와 DB 시간대가 다르면 가입일시·로그인 이력 시간이 9시간 어긋날 수 있습니다. | 아래 설정처럼 JDBC URL과 Hibernate 시간대를 `Asia/Seoul`로 맞춥니다. |
| **User-Agent 길이** | `LOGIN_LOG`의 접속 기기 정보(User-Agent)는 255자를 넘을 수 있어, 기본 `VARCHAR(255)`면 저장 오류가 납니다. | `@Column(length = 500)`으로 늘리거나 저장 전에 잘라냅니다. |

```yaml
# application-local.yml — 접속 정보 예시 (비밀번호는 환경변수로)
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/onbank?serverTimezone=Asia/Seoul&characterEncoding=UTF-8
    username: onbank
    password: ${DB_PASSWORD}
    driver-class-name: com.mysql.cj.jdbc.Driver
  jpa:
    properties:
      hibernate:
        jdbc:
          time_zone: Asia/Seoul
```

> 드라이버 의존성은 `com.mysql:mysql-connector-j`입니다. (예전 이름 `mysql:mysql-connector-java`는 더 이상 쓰지 않습니다.)

> ⚠️ **기획서 보완 필요**: README 10장의 `MEMBER` 상태는 정상/정지/탈퇴뿐입니다. 로그인 잠금을 위해 **`LOCKED` 상태와 `loginFailCount` 컬럼을 ERD에 추가**해야 합니다.

### ② 회원가입: 입력 검증 + 서비스 + 컨트롤러

- **형식 검증**은 DTO에 Bean Validation(`@Pattern`, `@NotBlank` 등)으로 선언합니다.
- **업무 검증**(아이디·휴대폰 중복, 비밀번호 확인 일치)은 Service에서 처리합니다.
- 비밀번호는 `PasswordEncoder`(BCrypt)로 암호화한 뒤 저장합니다.
- 가입 직후 상태는 `ACTIVE` + 실명확인 `NONE`입니다. 관리자 승인은 없습니다(README 5-2장).

```java
// SignupForm.java
@Getter @Setter
public class SignupForm {
    @NotBlank @Pattern(regexp = "^[a-z0-9]{5,20}$", message = "영문 소문자/숫자 5~20자")
    private String loginId;
    @NotBlank @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[!@#$%^&*]).{8,20}$",
                       message = "영문+숫자+특수문자 8~20자")
    private String password;
    @NotBlank private String passwordConfirm;
    @NotBlank private String name;
    @NotBlank @Pattern(regexp = "^010\\d{8}$") private String phone;
    @NotNull @Past private LocalDate birthDate;
    @AssertTrue(message = "필수 약관에 동의해주세요") private boolean termsAgreed;
}
```

```java
// MemberService.java
@Service @RequiredArgsConstructor @Transactional(readOnly = true)
public class MemberService {
    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public Long signup(SignupForm f) {
        if (!f.getPassword().equals(f.getPasswordConfirm()))
            throw new BusinessException("비밀번호가 일치하지 않습니다.");
        if (memberRepository.existsByLoginId(f.getLoginId()))
            throw new BusinessException("이미 사용 중인 아이디입니다.");
        if (memberRepository.existsByPhone(f.getPhone()))
            throw new BusinessException("이미 가입된 휴대폰 번호입니다.");

        Member m = Member.signup(f.getLoginId(), passwordEncoder.encode(f.getPassword()),
                                 f.getName(), f.getPhone(), f.getBirthDate());
        return memberRepository.save(m).getId();
    }
}
```

```java
// SignupController.java
@Controller @RequiredArgsConstructor
public class SignupController {
    private final MemberService memberService;

    @GetMapping("/signup")
    public String form(Model model) { model.addAttribute("form", new SignupForm()); return "auth/signup"; }

    @PostMapping("/signup")
    public String signup(@Valid @ModelAttribute("form") SignupForm form, BindingResult br,
                         HttpSession session, RedirectAttributes ra) {
        if (!Boolean.TRUE.equals(session.getAttribute("phoneVerified:" + form.getPhone())))
            br.rejectValue("phone", "notVerified", "휴대폰 인증을 완료해주세요.");
        if (br.hasErrors()) return "auth/signup";
        try {
            memberService.signup(form);
        } catch (BusinessException e) {
            br.reject("signupFail", e.getMessage());
            return "auth/signup";
        }
        session.removeAttribute("phoneVerified:" + form.getPhone());
        ra.addFlashAttribute("msg", "가입이 완료되었습니다. 로그인해주세요.");
        return "redirect:/login";
    }
}
```

### ③ 휴대폰 인증: 문자메시지 API 실제 발송

실제 은행과 최대한 비슷하게 하기 위해, 인증번호를 **문자메시지(SMS) API로 사용자 휴대폰에 실제로 보냅니다.**

#### SMS 인증과 "본인인증"의 차이 (발표 때 알고 있어야 할 점)

| 구분 | 실제 은행의 휴대폰 본인인증 | 우리 프로젝트의 SMS 인증 |
|---|---|---|
| 방식 | 본인확인기관(PASS, NICE, KCB 등)이 **이름·생년월일·통신사·휴대폰번호가 실제 명의자와 일치하는지** 확인 | 입력한 번호로 인증번호를 보내 **그 휴대폰을 지금 가지고 있는지** 확인 |
| 증명하는 것 | 명의(실명) + 휴대폰 소유 | 휴대폰 소유만 |
| 도입 조건 | 사업자 등록, 본인확인기관과 계약, 심사 필요 | 문자 서비스 가입, 발신번호 등록만 하면 사용 가능 |

- 학생 팀은 본인확인기관과 계약할 수 없으므로 **SMS 인증이 현실적으로 구현할 수 있는 가장 실제에 가까운 방식**입니다.
- 명의(실명) 확인은 README 5-2장대로 **첫 계좌 개설 때의 본인확인 심사**가 담당합니다. 실제 은행도 회원가입보다 계좌 개설 때 실명확인을 더 엄격하게 합니다.

#### 문자메시지 API 서비스 선택

국내 번호로 보내려면 국내 문자 발송 서비스를 쓰는 것이 편합니다.

| 서비스 | 특징 |
|---|---|
| 솔라피(SOLAPI, 구 CoolSMS) | 개발자용 문서와 Java SDK 제공, 개인도 가입 가능 |
| 네이버 클라우드 SENS | 네이버 클라우드 플랫폼의 메시지 서비스, REST API 제공 |
| 알리고(Aligo) | 가입이 간단하고 REST API 방식 |

- 요금은 **건당 과금(선불 충전)**이 일반적입니다. 가입 전에 각 서비스의 현재 요금과 무료 체험 크레딧을 확인하고, 팀 예산을 정해 두세요.
- 어떤 서비스를 쓰든 아래 `SmsSender` 인터페이스 뒤에 숨기면, 나중에 서비스를 바꿔도 다른 코드는 그대로 둘 수 있습니다.

#### 사용 전 준비 사항

1. **발신번호 사전 등록**: 전기통신사업법에 따라 문자는 **사전에 등록·인증된 발신번호로만** 보낼 수 있습니다. 팀원 한 명의 휴대폰 번호를 발신번호로 등록합니다(서비스마다 인증 절차가 다르므로 미리 진행).
2. **API 키 발급**: 서비스 콘솔에서 API Key / Secret을 발급받습니다.
3. **비밀정보 관리**: API 키와 발신번호는 **Git에 절대 커밋하지 않고** 환경변수로 주입합니다(README 13-1장 비밀정보 원칙과 동일). 키가 GitHub에 올라가면 누군가 문자를 대량 발송해 충전 금액이 소진될 수 있습니다.
4. **누가 결제·관리할지** 정합니다(충전 계정 소유자, 잔액 확인 담당).

#### 동작 규칙

- 인증번호: `SecureRandom`으로 만든 **6자리 숫자**, 유효시간 **3분**
- 저장 위치: **세션**(인증번호, 만료시각, 검증 시도 횟수). 인증 성공 시 `phoneVerified` 플래그를 남깁니다.
- 회원가입 POST 때 이 플래그를 서버에서 다시 확인합니다. 화면에서만 막으면 우회할 수 있기 때문입니다.
- **오남용 방지 (실제 비용이 드는 기능이므로 필수)**
  - 같은 번호로 **재발송은 60초 후**에만 가능
  - 같은 번호 **하루 발송 5회**, 같은 IP **하루 발송 20회** 정도로 제한
  - 인증번호 **입력은 5회까지**, 초과 시 해당 인증번호 폐기 → 다시 발송받아야 함
  - 이미 가입된 휴대폰 번호면 문자를 보내지 않고 바로 안내 (불필요한 발송 방지)
- 문자 내용 예시: `[OnBank] 인증번호 [123456]를 입력해주세요. (3분 이내)`
- **로그에 인증번호를 남기지 않습니다.** (운영 로그는 여러 사람이 볼 수 있음)

#### 개발 환경에서는 실제 발송을 끄기

개발 중 테스트할 때마다 문자를 보내면 비용이 들고 발송 제한에도 걸립니다. 그래서 **프로필별로 발송기를 바꿉니다.**

| 프로필 | 사용 구현체 | 동작 |
|---|---|---|
| `local` (개발) | `ConsoleSmsSender` | 문자를 보내지 않고 콘솔에 인증번호 출력 |
| `prod` (AWS 배포·시연) | `ApiSmsSender` | 문자메시지 API로 실제 발송 |

#### 코드 뼈대

```java
// SmsSender.java — 문자 발송 창구 (어떤 SMS 서비스를 쓰든 이 인터페이스만 의존)
public interface SmsSender {
    void send(String to, String text);
}
```

```java
// ConsoleSmsSender.java — 개발용: 실제로 보내지 않음
@Component @Profile("local") @Slf4j
public class ConsoleSmsSender implements SmsSender {
    @Override
    public void send(String to, String text) {
        log.info("[DEV SMS] to={} text={}", to, text);
    }
}
```

```java
// ApiSmsSender.java — 배포용: 문자메시지 API로 실제 발송
@Component @Profile("prod")
public class ApiSmsSender implements SmsSender {
    private final String apiKey, apiSecret, from;

    public ApiSmsSender(@Value("${sms.api-key}") String apiKey,
                        @Value("${sms.api-secret}") String apiSecret,
                        @Value("${sms.from}") String from) {
        this.apiKey = apiKey; this.apiSecret = apiSecret; this.from = from;
    }

    @Override
    public void send(String to, String text) {
        // 선택한 서비스의 SDK 또는 REST API 호출 (서비스 공식 문서의 예제 참고)
        // 실패 시 예외를 던져 "문자 발송에 실패했습니다. 잠시 후 다시 시도해주세요." 안내
    }
}
```

```yaml
# application-prod.yml — 값은 환경변수로 주입, 파일에 실제 키를 쓰지 않음
sms:
  api-key: ${SMS_API_KEY}
  api-secret: ${SMS_API_SECRET}
  from: ${SMS_FROM_NUMBER}
```

```java
// PhoneVerificationService.java — 발송 제한, 인증번호 생성·검증
@Service @RequiredArgsConstructor
public class PhoneVerificationService {
    private static final Duration TTL = Duration.ofMinutes(3);
    private static final Duration RESEND_INTERVAL = Duration.ofSeconds(60);
    private static final int MAX_ATTEMPTS = 5;

    private final SmsSender smsSender;
    private final MemberRepository memberRepository;
    private final SecureRandom random = new SecureRandom();

    public void send(String phone, HttpSession session) {
        if (memberRepository.existsByPhone(phone))
            throw new BusinessException("이미 가입된 휴대폰 번호입니다.");

        LocalDateTime sentAt = (LocalDateTime) session.getAttribute("phoneSentAt:" + phone);
        if (sentAt != null && sentAt.plus(RESEND_INTERVAL).isAfter(LocalDateTime.now()))
            throw new BusinessException("잠시 후 다시 요청해주세요.");
        // 하루 발송 횟수(번호·IP 기준) 제한은 DB 또는 캐시에 발송 이력을 남겨 확인

        String code = String.format("%06d", random.nextInt(1_000_000));
        smsSender.send(phone, "[OnBank] 인증번호 [" + code + "]를 입력해주세요. (3분 이내)");

        session.setAttribute("phoneCode:" + phone, code);
        session.setAttribute("phoneCodeExp:" + phone, LocalDateTime.now().plus(TTL));
        session.setAttribute("phoneSentAt:" + phone, LocalDateTime.now());
        session.setAttribute("phoneAttempts:" + phone, 0);
    }

    public boolean verify(String phone, String code, HttpSession session) {
        String saved = (String) session.getAttribute("phoneCode:" + phone);
        LocalDateTime exp = (LocalDateTime) session.getAttribute("phoneCodeExp:" + phone);
        Integer attempts = (Integer) session.getAttribute("phoneAttempts:" + phone);
        if (saved == null || exp == null || exp.isBefore(LocalDateTime.now()))
            throw new BusinessException("인증번호가 만료되었습니다. 다시 받아주세요.");
        if (attempts != null && attempts >= MAX_ATTEMPTS) {
            session.removeAttribute("phoneCode:" + phone);
            throw new BusinessException("입력 횟수를 초과했습니다. 인증번호를 다시 받아주세요.");
        }

        boolean ok = MessageDigest.isEqual(saved.getBytes(), code.getBytes());
        if (ok) {
            session.removeAttribute("phoneCode:" + phone);
            session.setAttribute("phoneVerified:" + phone, true);
        } else {
            session.setAttribute("phoneAttempts:" + phone, (attempts == null ? 0 : attempts) + 1);
        }
        return ok;
    }
}
```

```java
// PhoneVerifyApiController.java — 화면(fetch)에서 호출하는 REST API
@RestController @RequestMapping("/api/phone") @RequiredArgsConstructor
public class PhoneVerifyApiController {
    private final PhoneVerificationService phoneVerificationService;

    @PostMapping("/send")
    public Map<String, Object> send(@RequestParam String phone, HttpSession session) {
        phoneVerificationService.send(phone, session);
        return Map.of("ok", true);
    }

    @PostMapping("/verify")
    public Map<String, Object> verify(@RequestParam String phone, @RequestParam String code, HttpSession session) {
        return Map.of("ok", phoneVerificationService.verify(phone, code, session));
    }
}
```

> - `/api/phone/**`는 비회원도 호출하므로 SecurityConfig의 `permitAll`에 추가해야 합니다.
> - 화면에는 남은 시간(3:00 카운트다운)과 "재발송" 버튼(60초 후 활성화)을 표시하면 실제 은행 화면과 비슷해집니다.
> - 같은 문자 발송 기능은 **아이디 찾기(2.3)·비밀번호 재설정(2.4)** 의 본인인증에도 그대로 재사용합니다.

### ④ 로그인: `UserDetailsService` + `LoginMember`

- 로그인 처리는 Spring Security 필터가 담당합니다. 우리는 **"아이디로 회원을 찾아주는 방법"만 알려주면** 됩니다(`loadUserByUsername`).
- `LoginMember`는 세션에 저장되는 로그인 사용자 정보입니다. 상태에 따라 Security가 자동으로 예외를 던집니다.
  - `isAccountNonLocked() == false` → `LockedException` (5회 오류 잠금)
  - `isEnabled() == false` → `DisabledException` (관리자 정지, 탈퇴)

```java
@Service @RequiredArgsConstructor
public class MemberDetailsService implements UserDetailsService {
    private final MemberRepository memberRepository;

    @Override
    public UserDetails loadUserByUsername(String loginId) {
        Member m = memberRepository.findByLoginId(loginId)
                .orElseThrow(() -> new UsernameNotFoundException("not found"));
        return new LoginMember(m);   // UserDetails 구현체
    }
}
```

```java
@Getter
public class LoginMember implements UserDetails {
    private final Long memberId;
    private final String loginId, password, name;
    private final Role role;
    private final MemberStatus status;

    public LoginMember(Member m) {
        this.memberId = m.getId(); this.loginId = m.getLoginId(); this.password = m.getPassword();
        this.name = m.getName(); this.role = m.getRole(); this.status = m.getStatus();
    }
    @Override public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }
    @Override public String getUsername() { return loginId; }
    @Override public boolean isAccountNonLocked() { return status != MemberStatus.LOCKED; }
    @Override public boolean isEnabled() { return status == MemberStatus.ACTIVE || status == MemberStatus.LOCKED; }
    // SUSPENDED(관리자 정지) 또는 WITHDRAWN(탈퇴)이면 isEnabled=false → DisabledException
}
```

> 📌 **팀 공유 포인트**: 다른 팀원은 컨트롤러에서 `@AuthenticationPrincipal LoginMember me`로 로그인 사용자를 받아 `me.getMemberId()`를 바로 사용합니다. 그래서 이 클래스는 **1주차에 가장 먼저 만들어 공유**해야 합니다(README 12장 분담 원칙).

### ⑤ 로그인 성공/실패 핸들러: 5회 잠금 + 이력 기록

- **실패 핸들러**: 비밀번호 오류(`BadCredentialsException`)면 실패 횟수를 늘리고, 5회째에 잠급니다. 실패 사유를 쿼리 파라미터로 넘겨 화면에 안내합니다.
- **성공 핸들러**: 실패 횟수를 초기화하고, 권한에 따라 이동할 화면을 나눕니다(USER → `/mybank`, ADMIN → `/admin`).
- 두 경우 모두 `LOGIN_LOG`에 IP와 접속 기기(User-Agent)를 기록합니다.
- 핸들러는 트랜잭션 밖에서 실행되므로, DB 변경은 `@Transactional`이 붙은 `LoginService`에 맡깁니다.

```java
@Component @RequiredArgsConstructor
public class LoginFailureHandler implements AuthenticationFailureHandler {
    private final LoginService loginService;

    @Override
    public void onAuthenticationFailure(HttpServletRequest req, HttpServletResponse res, AuthenticationException ex)
            throws IOException {
        String loginId = req.getParameter("loginId");
        String reason = "bad";
        if (ex instanceof LockedException) reason = "locked";
        else if (ex instanceof DisabledException) reason = "disabled";
        else if (ex instanceof BadCredentialsException) {
            int count = loginService.recordFailure(loginId, req);   // 실패 횟수+1, LOGIN_LOG 기록, 5회면 LOCKED
            if (count >= 5) reason = "locked";
        }
        res.sendRedirect("/login?error=" + reason);
    }
}
```

```java
@Component @RequiredArgsConstructor
public class LoginSuccessHandler implements AuthenticationSuccessHandler {
    private final LoginService loginService;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest req, HttpServletResponse res, Authentication auth)
            throws IOException {
        LoginMember me = (LoginMember) auth.getPrincipal();
        loginService.recordSuccess(me.getMemberId(), req);  // 실패 횟수 0, LOGIN_LOG 기록
        res.sendRedirect(me.getRole() == Role.ADMIN ? "/admin" : "/mybank");
    }
}
```

```java
@Service @RequiredArgsConstructor
public class LoginService {
    private final MemberRepository memberRepository;
    private final LoginLogRepository loginLogRepository;

    @Transactional
    public int recordFailure(String loginId, HttpServletRequest req) {
        return memberRepository.findByLoginId(loginId).map(m -> {
            m.loginFailed();
            loginLogRepository.save(LoginLog.fail(m, req.getRemoteAddr(), req.getHeader("User-Agent")));
            return m.getLoginFailCount();
        }).orElse(0);   // 없는 아이디는 기록하지 않습니다
    }

    @Transactional
    public void recordSuccess(Long memberId, HttpServletRequest req) {
        Member m = memberRepository.findById(memberId).orElseThrow();
        m.loginSucceeded();
        loginLogRepository.save(LoginLog.success(m, req.getRemoteAddr(), req.getHeader("User-Agent")));
    }
}
```

### ⑥ SecurityConfig: 로그인·로그아웃·세션 규칙을 한곳에

| 설정 | 의미 |
|---|---|
| `authorizeHttpRequests` | URL별 접근 권한 (README 8-1장 권한표 기준) |
| `formLogin` | 우리가 만든 로그인 화면 사용, `POST /login`은 Security가 처리 |
| `logout` | `POST /logout` → 세션 무효화 + 쿠키 삭제 |
| `sessionFixation().changeSessionId()` | 로그인 시 세션 ID 교체 (세션 고정 공격 방지) |
| `maximumSessions(1)` | 은행처럼 동시 로그인 1개 제한 |
| `server.servlet.session.timeout` | 미사용 시 자동 로그아웃 시간 |

```java
@Configuration @EnableWebSecurity @RequiredArgsConstructor
public class SecurityConfig {
    private final LoginSuccessHandler successHandler;
    private final LoginFailureHandler failureHandler;

    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
          .authorizeHttpRequests(auth -> auth
              .requestMatchers("/", "/signup", "/login", "/find-id", "/reset-password", "/api/phone/**",
                               "/products/**", "/loans/calculator", "/support/notices/**", "/support/faq/**",
                               "/branches/**", "/css/**", "/js/**", "/images/**", "/error").permitAll()
              .requestMatchers("/admin/**").hasRole("ADMIN")
              .anyRequest().hasRole("USER"))
          .formLogin(form -> form
              .loginPage("/login")                 // 우리가 만든 Thymeleaf 로그인 화면
              .loginProcessingUrl("/login")        // POST /login은 Security가 처리
              .usernameParameter("loginId")
              .passwordParameter("password")
              .successHandler(successHandler)
              .failureHandler(failureHandler))
          .logout(logout -> logout
              .logoutUrl("/logout")                // POST만 허용 (CSRF 보호)
              .logoutSuccessUrl("/?logout")
              .invalidateHttpSession(true)
              .deleteCookies("JSESSIONID"))
          .sessionManagement(s -> s
              .sessionFixation(f -> f.changeSessionId())   // 로그인 시 세션 ID 교체 (세션 고정 공격 방지)
              .maximumSessions(1)                          // 은행처럼 중복 로그인 제한
              .expiredUrl("/login?error=duplicate"));
        return http.build();
    }
}
```

```yaml
# application.yml
server:
  servlet:
    session:
      timeout: 10m        # 10분 동안 아무것도 안 하면 자동 로그아웃
```

> `maximumSessions(1)`의 기본 동작은 **새 로그인이 기존 세션을 끊는 것**입니다. 반대로 새 로그인을 막고 싶다면 `.maxSessionsPreventsLogin(true)`를 씁니다. 이때는 로그아웃 시 세션이 정리되도록 `HttpSessionEventPublisher` 빈을 함께 등록해야 합니다.

### ⑦ 화면 (Thymeleaf)

- `th:action`을 쓰면 CSRF 토큰이 폼에 자동으로 들어갑니다.
- 실패 사유(`?error=locked` 등)에 따라 안내 문구를 다르게 보여줍니다.
- 로그아웃은 링크가 아니라 **POST 폼**으로 만듭니다.
- `sec:` 속성을 쓰려면 `thymeleaf-extras-springsecurity6` 의존성이 필요합니다.

```html
<!-- auth/login.html -->
<form th:action="@{/login}" method="post">
  <div th:if="${param.error}" th:switch="${param.error[0]}">
    <p th:case="'locked'">비밀번호 5회 오류로 잠겼습니다. 비밀번호 재설정을 진행해주세요.</p>
    <p th:case="'disabled'">이용이 정지되었거나 탈퇴한 계정입니다.</p>
    <p th:case="'duplicate'">다른 곳에서 로그인되어 로그아웃되었습니다.</p>
    <p th:case="*">아이디 또는 비밀번호가 올바르지 않습니다.</p>
  </div>
  <input name="loginId" placeholder="아이디">
  <input name="password" type="password" placeholder="비밀번호">
  <button type="submit">로그인</button>
</form>

<!-- 공통 헤더: 로그아웃은 POST 폼으로 -->
<form th:action="@{/logout}" method="post" sec:authorize="isAuthenticated()">
  <span sec:authentication="principal.name"></span>님
  <button type="submit">로그아웃</button>
</form>
```

---

## 4. 가상 OTP (이체용 보안매체)

실물 OTP 기기를 지급하기 어려우므로, **OTP 기기를 웹페이지로 구현한 "가상 OTP 기기"**를 사용합니다.

### 4-1. OTP의 원리 (TOTP 표준)

```
[OTP 기기]  내장된 비밀키(시드) + 현재 시각(30초 단위) → 계산 → 6자리 숫자 표시
[은행 서버] 같은 시드를 보관       + 현재 시각(30초 단위) → 같은 계산 → 입력값과 비교
```

- 기기와 서버는 서로 통신하지 않습니다. **같은 시드와 같은 시각**으로 각자 계산하므로 숫자가 일치합니다.
- 실제 은행 OTP 기기와 모바일 OTP 앱도 대부분 이 표준(TOTP, RFC 6238)으로 동작합니다.

### 4-2. 구현 방식: 브라우저가 직접 계산하는 가상 기기

| | ❌ 서버가 번호를 보여주는 방식 | ✅ 브라우저가 직접 계산하는 방식 (채택) |
|---|---|---|
| 동작 | 로그인한 회원이 "내 OTP" 화면을 열면 서버가 계산한 번호를 보여줌 | "가상 OTP 기기" 페이지가 **시드를 브라우저에 보관**하고 JavaScript로 30초마다 직접 계산 |
| 서버와의 관계 | 번호를 서버에서 받아옴 | 등록할 때 한 번만 시드를 받고, 그 뒤로는 **서버와 통신하지 않음** |
| 보안 의미 | 로그인 세션만 있으면 번호를 볼 수 있어 2차 인증 역할을 못 함 | 그 브라우저(기기)를 가진 사람만 번호를 알 수 있어 **실물 OTP와 원리가 같음** |

### 4-3. 업무 흐름

```
① [회원] 보안설정 > OTP 발급 신청 (본인확인 완료 회원만)
② [서버] 일련번호 + 시드 생성 → 시드는 AES로 암호화해 DB 저장
         → 등록용 QR/등록 코드를 화면에 딱 1번만 표시 (다시 볼 수 없음 = 기기 지급)
③ [회원] 가상 OTP 기기 페이지 열기 → QR 스캔 또는 등록 코드 입력
         → 시드가 그 브라우저에 저장되고, 일련번호와 6자리 번호 표시 시작
④ [회원] 사용 등록: 기기에 표시된 번호를 연속 2회 입력 → 상태: 사용중
⑤ [회원] 이체할 때 계좌 비밀번호 + OTP 6자리 입력
⑥ 오류 10회 → 상태: 정지 → 관리자 해제 또는 재발급
⑦ 브라우저 데이터 삭제 = 기기 분실 → 분실 신고 → 해당 OTP 즉시 사용 불가 → 재발급 신청
```

- **② 1번만 표시**: 실제 은행이 OTP 기기를 손에 쥐여주는 순간에 해당합니다. 서버는 이후 시드를 절대 다시 내보내지 않습니다.
- **④ 연속 2회 입력**: 실제 은행의 OTP 등록 절차와 같습니다. 기기를 실제로 가지고 있는지, 시계가 맞는지 확인합니다.
- **⑥ 오류 10회 정지**: 실제 은행 OTP 정책과 같습니다. 계좌 비밀번호 5회 잠금과 같은 구조입니다.
- 은행다움을 더하려면 ①과 ② 사이에 **관리자 승인** 단계를 넣을 수 있습니다(팀원4와 협의).

**OTP 상태 전이**

```
발급(ISSUED) ──연속 2회 입력──▶ 사용중(ACTIVE) ──오류 10회──▶ 정지(SUSPENDED) ──관리자 해제──▶ 사용중
                                      │
                                      └──분실 신고──▶ 분실(LOST) ──▶ 재발급 신청 → 새 OTP 발급
```

### 4-4. 가상 OTP 기기 페이지

- **모양**: 실물 OTP 카드처럼 꾸밉니다. 은행 로고, 일련번호, LCD 느낌의 6자리 숫자, 30초 남은 시간 막대를 넣습니다.
- **위치**: 메인 서비스와 분리된 정적 페이지(예: `/otp-device`)로 둡니다. **로그인 없이 열리고, 번호를 계산할 때 서버를 호출하지 않아야** 합니다. (`/otp-device`는 SecurityConfig의 `permitAll`에 추가)
- **사용 기기**: 휴대폰으로 열어 **"홈 화면에 추가"**하면 앱처럼 쓸 수 있습니다. PC에서 이체하고 휴대폰 OTP를 보는 모습이 실제 은행과 같아 시연 효과가 좋습니다.
- **호환성**: 등록 QR을 TOTP 표준 형식(`otpauth://totp/...`)으로 만들면 **Google Authenticator 같은 OTP 앱으로도 등록**할 수 있어, 가상 기기 페이지에 문제가 생겼을 때 대안이 됩니다.

### 4-5. 서버 검증 규칙

| 규칙 | 내용 |
|---|---|
| 시드 저장 | 해시가 아니라 **AES 암호화**로 저장 (서버가 계산에 써야 하므로 되돌릴 수 있어야 함). 암호화 키는 환경변수로 주입 |
| 시간 오차 허용 | 기기 시계가 조금 틀릴 수 있으므로 **앞뒤 1구간(±30초)**까지 허용 |
| 재사용 금지 | 한 번 성공한 30초 구간의 번호는 다시 받지 않음 (마지막 사용 구간을 저장) |
| 오류 횟수 | 연속 10회 오류 시 정지, 성공 시 0으로 초기화 |
| 트랜잭션 분리 | 오류 횟수는 이체가 롤백돼도 남아야 하므로 **이체 트랜잭션과 분리**(`REQUIRES_NEW`). README 11-1장의 계좌 비밀번호 오류 처리와 같은 원리 |

팀원2의 이체 Service는 아래 메서드 하나만 호출하면 됩니다.

```java
// 팀원1 제공: 실패 시 예외(OTP 불일치 / 정지 / 미등록), 오류 횟수는 별도 트랜잭션으로 커밋
otpService.verify(memberId, otpCode);
```

### 4-6. 테이블

| 테이블 | 컬럼 |
|---|---|
| `OTP_DEVICE` | 회원FK, 일련번호, 시드(AES 암호화), 상태(발급/사용중/정지/분실/폐기), 오류 횟수, 마지막 사용 구간, 발급일시, 사용등록일시 |

> 회원 한 명당 사용 가능한(발급·사용중·정지) OTP는 1개만 허용합니다. 분실·폐기된 이력은 남겨 둡니다.

### 4-7. 주의할 점

1. **HTTPS 문제**: 브라우저의 암호화 기능(Web Crypto API)과 카메라 QR 스캔은 **HTTPS에서만 동작**합니다(`localhost`는 예외). AWS에 IP 주소로 `http://` 배포하면 휴대폰에서 동작하지 않습니다.
   - 해결 1: 배포 서버에 도메인 + HTTPS 적용 (팀원4와 협의)
   - 해결 2: 계산은 순수 JavaScript 라이브러리로 하고, QR 스캔 대신 **등록 코드 직접 입력**으로 대체
2. **브라우저 보관의 한계**: 시드를 브라우저 저장소에 두는 것은 실물 기기보다 약합니다. 모의 프로젝트로는 충분하지만, 발표 때 "실제 은행은 시드를 변조 방지 하드웨어에 넣는다"고 짚어 주면 좋습니다.
3. **팀원2와 합의 필요**: OTP를 등록하지 않은 회원의 이체를 **전부 막을지**, **일정 금액 이하만 허용할지** 정해야 합니다. (실제 은행은 보안매체가 없으면 이체 한도를 아주 낮게 제한)

---

## 5. 은행다운 구현 포인트 (실수하기 쉬운 부분)

1. **아이디가 없는지, 비밀번호가 틀렸는지 구분해서 알려주지 않습니다.** 둘 다 "아이디 또는 비밀번호가 올바르지 않습니다"로 안내해야 아이디 존재 여부를 알아내는 공격을 막을 수 있습니다.
2. **비밀번호는 BCrypt 해시로만 저장합니다.** 평문 저장이나 로그 출력은 금지입니다.
3. **로그아웃은 반드시 POST로 합니다.** `<a href="/logout">`은 CSRF 보호가 켜져 있으면 동작하지 않습니다.
4. **잠금 해제 경로를 정해야 합니다.** 추천 방식은 비밀번호 재설정(2.4, 휴대폰 SMS 인증) 성공 시 `LOCKED`를 `ACTIVE`로 바꾸고 실패 횟수를 0으로 만드는 것입니다. 관리자 화면에서도 해제할지는 팀원4와 협의합니다.
5. **관리자 계정은 회원가입으로 만들지 않습니다.** `data.sql` 등 시드 데이터로 넣고, 비밀번호도 BCrypt 해시값으로 넣습니다.
6. **문자 발송은 실제 비용이 드는 기능입니다.** API 키는 Git에 올리지 않고, 재발송·일일 횟수 제한을 반드시 넣고, 개발 중에는 `local` 프로필로 실제 발송을 끕니다.
7. **테스트 계정을 시드로 공유합니다.** README 14장대로 "본인확인 완료" 상태의 테스트 회원을 만들어 두면 팀원2·3이 관리자 기능 없이도 바로 개발할 수 있습니다.

---

## 6. 추천 작업 순서

**1~2주차: 회원가입 · 로그인 (`[핵심]`)**


1. `Member` 엔티티, `MemberRepository`, `LoginMember` 작성 → **팀원들에게 먼저 공유**
2. `SecurityConfig` 최소 버전 작성 (`formLogin`, `logout`, 권한 규칙)
3. 시드 계정(USER, ADMIN)으로 로그인/로그아웃 동작 확인
4. 회원가입 폼, 검증, 서비스 구현
5. 휴대폰 인증 구현
   - 먼저 `ConsoleSmsSender`(local)로 인증 흐름과 발송 제한을 완성
   - 문자 서비스 가입, 발신번호 등록, API 키 발급 (등록 승인에 시간이 걸릴 수 있으니 **1주차에 미리 신청**)
   - `ApiSmsSender`(prod)를 연결해 실제 휴대폰으로 수신 확인
6. 성공/실패 핸들러, 5회 잠금, `LOGIN_LOG` 기록 구현
7. 세션 타임아웃, 중복 로그인 제한 적용
8. 아이디 찾기, 비밀번호 재설정 (`[심화]`)

**`[핵심]` 완료 후: 가상 OTP**

1. 팀원2와 OTP 미등록 회원의 이체 정책 합의, `otpService.verify()` 사용 방법 공유
2. `OTP_DEVICE` 테이블, 시드 생성·AES 암호화, `otpService.verify()` 구현 (Google Authenticator로 먼저 테스트)
3. 발급 신청 → 등록 코드 1회 표시 → 연속 2회 입력 사용 등록 화면 (보안설정)
4. 가상 OTP 기기 페이지(`/otp-device`) 구현
5. 오류 10회 정지, 분실 신고, 재발급 처리
6. 배포 서버(HTTPS 여부)에서 휴대폰으로 동작 확인

> 프로젝트 초기 세팅(`build.gradle`, 패키지 구조, 공통 예외 `BusinessException` 등)은 팀원4 담당이므로, 구현 착수 전에 이름과 위치를 맞춰야 합니다.
