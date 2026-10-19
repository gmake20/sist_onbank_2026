# 가상 OTP 구현 설명 (처음 보는 사람용)

- 작성: 2026-10-18
- 대상 코드: `project/onbank/src/main/java/org/doit/onbank/otp/` 전체, 화면 `templates/security/otp*.html`
- 설계 근거: `docs/auth.md` 4장 (가상 OTP)
- 이 문서는 **코드를 처음부터 끝까지 읽지 않아도 무엇이 어디서 왜 일어나는지** 알 수 있게 쓴 것입니다. 순서대로 읽으면 됩니다.

---

## 목차

1. OTP가 뭔가요?
2. 번호는 어떻게 만들어지나요? (TOTP 원리)
3. 전체 흐름 한눈에 보기
4. 파일 지도 (어떤 파일이 무슨 일을 하나)
5. 파일별 자세한 설명
6. "왜 이렇게 했나요?" — 설계 이유 모음
7. 화면 흐름 (Controller와 HTML)
8. 테스트는 무엇을 확인하나
9. 발표·질문 대비 Q&A
10. 용어 정리
11. 부록: QR 코드와 `otpauth://` 주소 (공식 문서 요약)

---

## 1. OTP가 뭔가요?

**OTP(One-Time Password) = 한 번만 쓰는 비밀번호**입니다.

은행에서 이체할 때 "계좌 비밀번호 4자리"만으로는 불안합니다. 비밀번호는 한 번 새어 나가면 계속 쓸 수 있기 때문입니다. 그래서 은행은 **30초마다 바뀌는 6자리 번호**를 하나 더 요구합니다. 이 번호를 보여 주는 장치가 OTP 기기(또는 휴대폰 OTP 앱)입니다.

- 누가 번호를 훔쳐보더라도 **30초 뒤면 쓸모가 없습니다.**
- 번호를 보려면 **그 기기를 손에 쥐고 있어야** 합니다.
- 그래서 "아는 것(계좌 비밀번호) + 가진 것(OTP 기기)" 두 가지로 확인하게 됩니다. 이것을 **2차 인증**이라고 합니다.

우리 프로젝트는 실물 기기를 나눠 줄 수 없으니, **Google Authenticator 같은 OTP 앱**(나중에는 우리가 만든 웹 페이지 `/otp-device`)을 기기 대신 씁니다. 그래서 이름이 "가상 OTP"입니다.

---

## 2. 번호는 어떻게 만들어지나요? (TOTP 원리)

### 2-1. 핵심 아이디어: "같은 재료 + 같은 시각 = 같은 번호"

```
[OTP 앱]   비밀 재료(시드) + 지금 시각 → 계산 → 6자리 번호 화면에 표시
[은행 서버] 같은 시드         + 지금 시각 → 같은 계산 → 고객이 입력한 번호와 비교
```

- **앱과 서버는 서로 통신하지 않습니다.** 처음 등록할 때 시드를 한 번 나눠 가진 뒤로는, 각자 같은 계산을 해서 같은 답을 얻습니다.
- 비유하면: 두 사람이 **같은 암호책**을 나눠 갖고, "오늘 몇 시 몇 분이면 몇 페이지 몇째 줄"이라는 규칙을 정해 둔 것과 같습니다. 서로 연락하지 않아도 같은 단어를 떠올릴 수 있습니다.

### 2-2. 용어 3개만 알면 됩니다

| 용어 | 뜻 | 우리 코드에서 |
|---|---|---|
| **시드(seed)** | 앱과 서버가 나눠 가진 비밀 재료. 20바이트의 무작위 값 | `OtpService.issue()`에서 `SecureRandom`으로 만듦 |
| **구간(step)** | 시각을 30초 단위로 자른 번호. "1970년 1월 1일부터 몇 번째 30초인가" | `TotpGenerator.step()` |
| **TOTP** | 시드와 구간으로 6자리를 만드는 국제 표준 계산법 (RFC 6238) | `TotpGenerator.generate()` |

### 2-3. 구간(step) 예시

```
시각(초)          ÷ 30 (나머지 버림) = 구간
1,700,000,000  →  56,666,666
1,700,000,029  →  56,666,667   ← 30초 안에서는 구간이 같음 → 번호도 같음
1,700,000,030  →  56,666,667
1,700,000,060  →  56,666,668   ← 30초가 지나면 구간이 바뀜 → 번호도 바뀜
```

번호가 30초마다 바뀌는 이유는 단순히 **구간 번호가 30초마다 1씩 커지기 때문**입니다.

### 2-4. 6자리 계산 과정 (그냥 "섞는 기계"라고 생각해도 됩니다)

```
① 구간 번호를 8바이트로 바꾼다
② HMAC-SHA1(시드, 구간) 계산 → 20바이트짜리 "지문"이 나옴
   (시드나 구간이 1비트만 달라져도 완전히 다른 지문이 나오는 함수)
③ 지문의 마지막 바이트를 보고 "몇 번째 칸부터 읽을지" 정한다 (dynamic truncation)
④ 그 칸부터 4바이트를 읽어 숫자로 만든다
⑤ 그 숫자를 1,000,000으로 나눈 나머지 → 0 ~ 999999 → 6자리로 맞춤 (앞에 0 채움)
```

②~④는 표준 문서(RFC 4226, 6238)에 정해진 그대로 구현했습니다. **우리가 마음대로 정한 부분이 없어야** Google Authenticator와 같은 번호가 나옵니다. 그래서 테스트에서 RFC 문서에 실린 공식 정답과 비교합니다(8장 참고).

---

## 3. 전체 흐름 한눈에 보기

### 3-1. 고객 입장에서

```
① 발급        보안설정 > OTP 발급 버튼
                 → 서버가 시드를 만들고, QR 코드·등록 코드를 딱 한 번 보여 줌
② 앱에 등록    휴대폰 OTP 앱으로 QR 스캔 (또는 등록 코드 직접 입력)
                 → 이제 앱이 30초마다 6자리를 보여 줌
③ 사용 등록    앱의 번호를 입력 → 30초 뒤 바뀐 번호를 한 번 더 입력
                 → 서버가 "이 사람이 진짜 기기를 갖고 있구나" 확인 → 사용중
④ 이체할 때    계좌 비밀번호 + OTP 6자리 입력 (팀원2 이체 화면에 붙일 예정)
                 → 지금은 OTP 화면의 "번호 확인"으로 미리 시험해 볼 수 있음
```

### 3-2. 상태 변화 (otp_device.status)

```
           [발급 버튼]                [연속 2회 입력 성공]
 (없음) ──────────────▶ ISSUED ─────────────────────────▶ ACTIVE
                          │                                 │
                          │ [다시 발급]                      │ [번호 10회 틀림]
                          ▼                                 ▼
                       DISPOSED (폐기)                   SUSPENDED (정지)

   * LOST(분실)는 다음 단계(분실 신고)에서 사용 예정
```

| 상태 | 뜻 | 화면에 보이는 것 |
|---|---|---|
| (없음) | 발급받은 적 없거나, 이전 것이 폐기·분실됨 | **OTP 발급** 버튼 |
| `ISSUED` | 발급만 받고 사용 등록 전 | **사용 등록** 입력칸 + **다시 발급** 버튼 |
| `ACTIVE` | 사용중 | **번호 확인** 입력칸 |
| `SUSPENDED` | 10회 틀려서 정지 | 정지 안내 |
| `DISPOSED` | 폐기 (기록으로만 남음) | 화면에서는 "없음"처럼 보임 |

> 회원 한 명이 동시에 가질 수 있는 OTP(ISSUED·ACTIVE·SUSPENDED)는 **1개뿐**입니다. DB의 유일 인덱스 `ux_otp_device_one_usable`이 이것을 강제합니다. 폐기·분실된 것은 몇 개든 기록으로 남습니다.

---

## 4. 파일 지도

```
otp/
├─ entity/
│   ├─ OtpStatus.java          상태 5개 (ISSUED, ACTIVE, SUSPENDED, LOST, DISPOSED)
│   └─ OtpDevice.java          otp_device 테이블 한 줄 = 기기 1대
├─ repository/
│   └─ OtpDeviceRepository.java DB 조회 (잠금 조회, 화면용 조회, 일련번호 중복 검사)
├─ dto/
│   ├─ OtpIssueResult.java     발급 결과 (일련번호, 등록 코드, QR용 주소) - 화면에 1번만
│   └─ OtpDeviceInfo.java      화면 표시용 기기 정보 (시드는 없음)
├─ service/
│   ├─ TotpGenerator.java      6자리 번호 계산 (2장의 계산 그 자체)
│   ├─ Base32.java             시드 ↔ 글자 변환 (QR·등록 코드용)
│   ├─ OtpSeedCipher.java      시드 암호화·복호화 (DB 저장용)
│   ├─ OtpCheckResult.java     번호 검증 결과 종류 (OK, 틀림, 정지 등)
│   ├─ OtpCodeChecker.java     번호 검증 + 오류 횟수 기록 (따로 커밋)
│   └─ OtpService.java         바깥에서 부르는 창구: 조회·발급·사용 등록·검증
└─ controller/
    └─ OtpPageController.java  /mybank/security/otp 화면 주소들

resources/
├─ templates/security/otp.html         상태 화면
├─ templates/security/otp-issued.html  발급 직후 화면 (QR, 등록 코드)
├─ static/css/otp.css                  화면 꾸미기
└─ static/js/otp.js                    QR 코드 그리기
```

**누가 누구를 부르나**

```
브라우저
  │  (주소 요청)
  ▼
OtpPageController ──▶ OtpService ──┬──▶ OtpDeviceRepository ──▶ DB(otp_device)
                                   ├──▶ OtpSeedCipher (암호화/복호화)
                                   ├──▶ TotpGenerator, Base32 (계산)
                                   └──▶ OtpCodeChecker ──▶ (위와 같은 것들)

팀원2 이체 Service ──▶ OtpService.verify()   ← 나중에 이체 화면이 부를 곳
```

---

## 5. 파일별 자세한 설명

### 5-1. `OtpStatus` — 상태 목록

```java
public enum OtpStatus {
	ISSUED, ACTIVE, SUSPENDED, LOST, DISPOSED;

	public static final List<OtpStatus> USABLE = List.of(ISSUED, ACTIVE, SUSPENDED);
}
```

- `USABLE`은 "회원당 1개만 있을 수 있는 상태"를 한 곳에 모아 둔 것입니다. 조회할 때마다 세 개를 일일이 쓰지 않으려고 만들었습니다.
- DB의 유일 인덱스 조건(`status IN ('ISSUED','ACTIVE','SUSPENDED')`)과 **같은 기준**이어야 합니다.

### 5-2. `OtpDevice` — 테이블 한 줄

| 필드 | 컬럼 | 설명 |
|---|---|---|
| `otpDeviceId` | `otp_device_id` | 기본키 (AUTO_INCREMENT) |
| `memberId` | `member_id` | 누구의 OTP인지 |
| `serialNo` | `serial_no` | 일련번호 10자리 숫자 (기기 뒷면 번호 같은 것) |
| `seedEncrypted` | `seed_encrypted` | **암호화된** 시드 (원래 시드는 DB에 없음) |
| `status` | `status` | 5-1의 상태 |
| `failCount` | `fail_count` | 연속으로 틀린 횟수 |
| `lastUsedStep` | `last_used_step` | 마지막으로 **성공한** 구간 번호 (재사용 방지용) |
| `issuedAt` | `issued_at` | 발급 일시 (자동으로 채워짐) |
| `activatedAt` | `activated_at` | 사용 등록 완료 일시 |
| `updatedAt` | `updated_at` | 마지막 수정 일시 (자동) |

**상태를 바꾸는 메서드** (상태는 이 메서드로만 바뀝니다. setter가 없습니다)

| 메서드 | 하는 일 |
|---|---|
| `activate(step, now)` | ISSUED → ACTIVE. 등록 일시 기록, 오류 횟수 0, 마지막 사용 구간 기록 |
| `recordFailure()` | 오류 +1. 10회가 되면 SUSPENDED. 정지됐으면 `true` |
| `recordSuccess(step)` | 오류 0으로, 마지막 사용 구간 기록 |
| `isStepUsed(step)` | 이 구간 번호를 이미 썼는지 (마지막 사용 구간 **이하**면 썼다고 봄) |
| `dispose()` | DISPOSED로 (사용 등록 전 기기를 다시 발급할 때) |

**다른 엔티티와 다른 점**

- 다른 엔티티는 `BaseEntity`를 상속해서 `created_at`을 자동으로 채웁니다. 그런데 `otp_device`에는 `created_at`이 없고 대신 `issued_at`이 있습니다.
- 그래서 `BaseEntity`를 상속하지 않고, `@EntityListeners(AuditingEntityListener.class)` + `@CreatedDate issuedAt` + `@LastModifiedDate updatedAt`을 직접 붙였습니다. 효과는 같습니다.
- `@ToString(exclude = "seedEncrypted")` : 로그에 객체를 출력해도 시드가 찍히지 않게 합니다.

### 5-3. `OtpDeviceRepository` — DB 조회

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("select d from OtpDevice d where d.memberId = :memberId and d.status in :statuses")
Optional<OtpDevice> findByMemberIdForUpdate(Long memberId, Collection<OtpStatus> statuses);

Optional<OtpDevice> findByMemberIdAndStatusIn(Long memberId, Collection<OtpStatus> statuses);

boolean existsBySerialNo(String serialNo);
```

| 메서드 | 언제 쓰나 | 잠금 |
|---|---|---|
| `findByMemberIdForUpdate` | 발급·사용 등록·검증 (값을 **바꿀** 때) | O |
| `findByMemberIdAndStatusIn` | 화면에 상태를 **보여 줄** 때 | X |
| `existsBySerialNo` | 새 일련번호가 겹치는지 확인 | X |

**잠금(`PESSIMISTIC_WRITE`)이 뭔가요?**
SQL로 치면 `SELECT ... FOR UPDATE`입니다. 이 줄을 읽은 트랜잭션이 끝날 때까지 **다른 요청은 같은 줄을 읽으려고 기다립니다.**

왜 필요할까요? 같은 번호로 거의 동시에 두 번 검증하는 경우를 생각해 봅시다.

```
잠금이 없으면                         잠금이 있으면
요청 A: 읽음 (마지막 사용 구간 = 10)    요청 A: 읽고 잠금
요청 B: 읽음 (마지막 사용 구간 = 10)    요청 B: 기다림...
요청 A: 구간 11 번호 맞음 → 통과        요청 A: 구간 11 통과, 기록, 끝
요청 B: 구간 11 번호 맞음 → 통과 (!!)   요청 B: 이제 읽음 (마지막 사용 = 11) → "이미 사용한 번호"
```

계좌 잔액에 쓰는 `AccountRepository.findByIdForUpdate`와 같은 방법입니다.

### 5-4. `TotpGenerator` — 번호 계산기

```java
public static long step(Instant now)                         // 시각 → 구간
public static String generate(byte[] seed, long step)        // 시드 + 구간 → "005924"
public static boolean matches(byte[] seed, long step, String code)  // 입력한 번호가 맞는지
```

- 상태(필드)가 없는 순수한 계산이라 `static` 메서드로만 만들었습니다. Spring Bean이 아닙니다.
- `generate`는 2-4의 ①~⑤를 그대로 코드로 옮긴 것입니다.
- `matches`에서 문자열을 `equals`가 아니라 `MessageDigest.isEqual`로 비교합니다.
  - `equals`는 앞 글자부터 비교하다 다르면 바로 멈춥니다. 그래서 "몇 번째 글자까지 맞았는지"에 따라 걸리는 시간이 아주 조금 달라집니다.
  - 이 시간 차이로 번호를 한 글자씩 알아내는 공격(timing attack)이 이론적으로 가능합니다. `isEqual`은 항상 끝까지 비교해서 시간이 같습니다.
  - 우리 프로젝트에서 실제로 위험할 정도는 아니지만, 비밀번호·인증번호 비교의 **좋은 습관**입니다.
- 6자리가 아니거나 `null`이면 바로 `false`입니다 ("5924"처럼 앞자리 0을 빼고 넣으면 틀림).

### 5-5. `Base32` — 시드를 사람이 입력할 수 있는 글자로

시드는 20바이트의 **이진 데이터**라서 그대로는 화면에 보여 줄 수 없습니다. 그래서 글자로 바꿔야 합니다.

| 방식 | 글자 종류 | 특징 |
|---|---|---|
| Base64 | A-Z, a-z, 0-9, +, / | 대소문자 구분, 기호 있음 → 사람이 받아 적기 불편 |
| **Base32** | A-Z, 2-7 | 대문자만, 헷갈리는 0/1/8/9 없음 → 받아 적기 쉬움 |

- OTP 앱들은 **Base32를 표준으로** 씁니다. 그래서 Base32를 씁니다.
- 20바이트 → 32글자가 됩니다. 화면에는 `ABCD EFGH ...`처럼 4글자씩 띄워 보여 줍니다 (`OtpIssueResult.formattedRegistrationCode()`).
- `decode`는 소문자, 띄어쓰기, `-`, `=`를 무시합니다. 사람이 입력할 때 실수해도 받아 주려는 것입니다.
- JDK에는 Base64만 있고 Base32는 없어서 직접 만들었습니다 (40줄 정도).

### 5-6. `OtpSeedCipher` — 시드를 DB에 안전하게 보관

**왜 해시가 아니라 암호화인가요?**

| | 해시 (BCrypt 등) | 암호화 (AES) |
|---|---|---|
| 되돌릴 수 있나 | X (한 방향) | O (키가 있으면) |
| 쓰는 곳 | 로그인 비밀번호, 계좌 비밀번호 | **OTP 시드** |
| 이유 | 서버는 "같은지"만 확인하면 됨 | 서버가 시드로 **번호를 직접 계산**해야 하므로 원래 값이 필요 |

비밀번호는 "입력값을 해시해서 저장된 해시와 같은지" 보면 끝이지만, OTP는 서버가 시드를 꺼내 TOTP 계산을 해야 합니다. 그래서 되돌릴 수 있는 암호화를 씁니다.

**AES-256-GCM 저장 형식**

```
DB에 저장되는 값 = Base64( IV 12바이트 | 암호문 20바이트 | 인증 태그 16바이트 )
                  = 64글자 정도 (seed_encrypted VARCHAR(255)에 충분히 들어감)
```

| 부분 | 뜻 |
|---|---|
| 키 (32바이트) | 잠그고 여는 열쇠. **DB에 없고 설정 파일(`onbank.otp.seed-key`)에만 있음** |
| IV (12바이트) | 매번 새로 뽑는 무작위 값. 같은 시드를 두 번 암호화해도 결과가 달라지게 함 |
| 인증 태그 (16바이트) | "중간에 누가 값을 바꾸지 않았는지" 확인하는 도장. 값이 1비트라도 바뀌면 복호화가 실패함 |

- DB가 통째로 유출되어도 **키가 없으면 시드를 꺼낼 수 없습니다.** 키는 Git에 올리지 않습니다.
- GCM은 같은 키로 같은 IV를 다시 쓰면 안전성이 깨집니다. 그래서 `encrypt`할 때마다 `SecureRandom`으로 새 IV를 만듭니다.

**키가 없을 때 동작**

- 키 설정이 비어 있어도 **앱은 정상적으로 시작**합니다. OTP를 쓰지 않는 다른 팀원 개발 환경이 멈추지 않게 하려는 것입니다.
- 대신 발급·검증을 할 때 "OTP 암호화 키가 설정되지 않았습니다" 오류가 납니다.
- 키 길이가 틀리면(32바이트가 아니면) 설정 실수라서 앱 시작 때 바로 오류를 냅니다.

**팀원 모두 같은 키를 써야 하는 이유**

공용 DB를 함께 쓰기 때문입니다. A의 서버(키 A)가 암호화한 시드를 B의 서버(키 B)가 읽으면 열 수 없습니다. 그래서 A가 발급한 OTP가 B의 서버에서는 검증되지 않습니다.

### 5-7. `OtpCheckResult` — 검증 결과 종류

```java
public record OtpCheckResult(Type type, int failCount) {
	public enum Type { OK, MISMATCH, SUSPENDED_NOW, REUSED, NOT_REGISTERED, NOT_ACTIVATED, SUSPENDED }
}
```

| 결과 | 뜻 | 오류 횟수 |
|---|---|---|
| `OK` | 맞음 | 0으로 초기화 |
| `MISMATCH` | 틀림 | +1 |
| `SUSPENDED_NOW` | 틀림, **이번에** 10회가 되어 정지됨 | +1 |
| `REUSED` | 번호는 맞지만 이미 사용한 번호 | 그대로 |
| `NOT_REGISTERED` | OTP가 없음 | - |
| `NOT_ACTIVATED` | 발급만 받고 사용 등록 전 | - |
| `SUSPENDED` | 이미 정지된 OTP | - |

`record`는 Java 16부터 생긴 문법으로, "값만 담는 클래스"를 한 줄로 만듭니다. `getType()` 대신 `type()`으로 꺼냅니다.

### 5-8. `OtpCodeChecker` — 번호 검증 (가장 중요한 파일)

```java
@Transactional(propagation = Propagation.REQUIRES_NEW)
public OtpCheckResult check(Long memberId, String code) {
	① 회원의 OTP를 잠그고 조회
	   없으면 NOT_REGISTERED / ISSUED면 NOT_ACTIVATED / SUSPENDED면 SUSPENDED

	② 시드를 복호화하고, 지금 구간(current)을 구한다

	③ current-1, current, current+1 세 구간을 차례로 확인
	   - 번호가 안 맞으면 다음 구간으로
	   - 번호가 맞는데 이미 쓴 구간이면 "재사용" 표시만 하고 다음 구간으로
	   - 번호가 맞고 처음 쓰는 구간이면 → 성공 기록, OK

	④ 맞는 구간이 없었는데 재사용 표시가 있으면 → REUSED (오류 횟수 그대로)

	⑤ 그냥 틀렸으면 → 오류 +1, MISMATCH 또는 SUSPENDED_NOW
}
```

**③ 왜 앞뒤 1구간(±30초)을 봐주나요?**

- 휴대폰 시계와 서버 시계가 몇 초씩 다를 수 있습니다.
- 고객이 번호를 보고 입력하는 사이에 30초가 넘어갈 수도 있습니다 (예: 28초에 보고 32초에 제출).
- 그래서 표준 권고대로 앞뒤 1구간까지 인정합니다. 그 이상 넓히면 그만큼 맞히기 쉬워져서 1구간만 허용합니다.

**④ 재사용 금지가 왜 필요한가요?**

번호는 30초(허용 범위까지 최대 90초) 동안 같습니다. 누군가 어깨 너머로 번호를 보고 그 사이에 먼저 써 버리면 안 됩니다. 그래서 **한 번 성공한 구간과 그 이전 구간의 번호는 다시 받지 않습니다** (`lastUsedStep` 이하 거절).
재사용은 고객이 실수로 같은 화면을 두 번 제출한 경우가 대부분이라 오류 횟수는 올리지 않습니다.

**`REQUIRES_NEW`가 뭔가요? (중요)**

이체는 이런 순서로 진행됩니다.

```
[이체 트랜잭션 시작]
   계좌 비밀번호 확인
   OTP 확인   ← 여기서 틀림 → 오류 횟수 +1
   예외 발생 → 이체 취소
[이체 트랜잭션 롤백]   ← 같은 트랜잭션이었다면 "오류 횟수 +1"도 함께 취소됨!
```

같은 트랜잭션이라면 이체가 취소될 때 오류 횟수 증가도 함께 취소됩니다. 그러면 몇 번을 틀려도 횟수가 쌓이지 않아 **10회 정지가 동작하지 않습니다.**

`REQUIRES_NEW`는 "이 메서드는 **새 트랜잭션**으로 따로 실행하고 따로 커밋하라"는 뜻입니다.

```
[이체 트랜잭션]
   ├─ [OTP 검증 트랜잭션 (REQUIRES_NEW)] 오류 +1 → 커밋 ✔ (여기서 확정)
   └─ 예외 → 이체만 롤백. OTP 오류 횟수는 이미 커밋돼서 남음
```

**왜 결과를 예외 대신 값(`OtpCheckResult`)으로 돌려주나요?**

`check()` 안에서 예외를 던지면 `check()`의 트랜잭션(REQUIRES_NEW)이 **롤백**됩니다. 그러면 방금 올린 오류 횟수가 또 사라집니다. 그래서 `check()`는 예외 없이 결과만 돌려주고 정상 커밋합니다. 예외는 바깥(`OtpService.verify`)에서 던집니다.

**왜 `OtpService` 안에 두지 않고 따로 클래스를 만들었나요?**

Spring의 `@Transactional`은 **다른 Bean을 통해 호출할 때만** 동작합니다(프록시 방식). 같은 클래스 안에서 `this.check()`로 부르면 `REQUIRES_NEW`가 무시됩니다. 그래서 별도 Bean(`OtpCodeChecker`)으로 분리했습니다.

> 팀원2의 `AccountPasswordVerifier`(계좌 비밀번호 5회 오류)가 **정확히 같은 구조**입니다. 두 파일을 나란히 읽으면 이해가 쉽습니다.

**`Clock`은 왜 있나요?**

"지금 시각"을 `Instant.now()`로 바로 쓰면, 테스트할 때마다 시각이 달라져 결과를 예측할 수 없습니다. 그래서 시각을 `Clock`으로 받습니다.

- 실제 실행: `Clock.systemDefaultZone()` (진짜 현재 시각)
- 테스트: `Clock.fixed(...)` (항상 같은 시각으로 고정)

생성자가 두 개인 이유입니다. `@Autowired`가 붙은 쪽을 Spring이 쓰고, 다른 하나는 테스트가 씁니다.

### 5-9. `OtpService` — 바깥에서 부르는 창구

#### `findCurrent(memberId)` — 화면 표시용 조회

지금 가진 OTP(ISSUED·ACTIVE·SUSPENDED)를 `OtpDeviceInfo`로 돌려줍니다. 시드는 넣지 않습니다.

#### `issue(memberId)` — 발급

```
① 지금 가진 OTP를 잠그고 조회
   - ACTIVE·SUSPENDED가 있으면 → 거절 ("분실 신고 후 다시 발급")
   - ISSUED가 있으면 → 폐기(DISPOSED)하고 flush   (등록 코드를 잃어버린 경우)
② 시드 20바이트를 SecureRandom으로 생성
③ 일련번호 10자리 생성 (겹치면 다시, 최대 5번)
④ 시드를 암호화해서 저장 (saveAndFlush)
⑤ 등록 코드(Base32)와 QR용 주소(otpauth://...)를 돌려줌 → 화면에 1번만 표시
```

**① flush는 왜 하나요?** (헷갈리기 쉬운 부분)

Hibernate는 모아 둔 변경을 DB에 보낼 때 **INSERT를 UPDATE보다 먼저** 보냅니다. 그러면 이런 일이 생깁니다.

```
우리가 의도한 순서:  ① 이전 기기 UPDATE (ISSUED → DISPOSED)  ② 새 기기 INSERT (ISSUED)
Hibernate 실제 순서: ② 새 기기 INSERT (ISSUED)  ← 이 순간 ISSUED가 2개 → 회원당 1개 제약 위반!
                    ① 이전 기기 UPDATE
```

그래서 폐기 직후 `flush()`로 **UPDATE를 먼저 DB에 보내 둡니다.**

**④ `DataIntegrityViolationException`은 언제 나나요?**

같은 회원이 발급 버튼을 거의 동시에 두 번 누르는 경우입니다. 둘 다 "OTP 없음"을 보고 둘 다 INSERT하려 할 수 있는데, 두 번째는 DB 유일 인덱스에 막힙니다. 그 오류를 사용자에게 보여 줄 수 있는 메시지로 바꿉니다. `MemberService.signup()`의 중복 처리와 같은 방식입니다.

**⑤ `otpauth://` 주소란?**

```
otpauth://totp/OnBank:0123456789?secret=ABCD...&issuer=OnBank&algorithm=SHA1&digits=6&period=30
```

OTP 앱들이 약속한 표준 형식(Key Uri Format)입니다. 이 주소를 QR 코드로 만들면 앱이 스캔해서 "OnBank / 0123456789" 이름으로 시드를 등록합니다. 각 항목의 뜻은 11-1장에 정리했습니다.

#### `activate(memberId, firstCode, secondCode)` — 사용 등록

```
① OTP를 잠그고 조회. ISSUED가 아니면 거절
② 지금 구간(current)을 구한다
③ step = current-2, current-1, current 중에서
   "첫 번호 = step의 번호" 이고 "두 번째 번호 = step+1의 번호" 인 것이 있으면 → ACTIVE
④ 없으면 거절
```

**왜 연속 2회인가요?**

- 실제 은행 OTP 등록 절차와 같습니다.
- 번호 하나만 받으면 우연히 맞힐 확률이 (허용 범위 3구간 기준) 약 33만 분의 1이고, **연속된 두 번호**를 받으면 거의 0이 됩니다.
- 무엇보다 "이 사람이 정말 그 시드로 계산하는 기기를 갖고 있고, 시계도 맞다"는 것을 확인합니다.
- 같은 번호를 두 번 넣으면 통과하지 않습니다 (서로 다른 연속 구간이어야 함).

**왜 `current-2`부터 보나요?**
고객은 첫 번호를 입력하고 **30초를 기다린 뒤** 두 번째를 입력합니다. 제출 시점에는 두 번째 번호가 "지금" 구간이거나 그 앞뒤일 수 있으므로, 두 번째 번호의 구간(`step+1`)이 `current-1 ~ current+1` 안에 들어오도록 `step`을 `current-2 ~ current`로 잡았습니다.

등록에 쓴 두 번째 번호의 구간은 `lastUsedStep`에 기록됩니다. 그래서 그 번호로 바로 이체할 수는 없습니다.

#### `verify(memberId, code)` — 이체할 때 검증

```java
public void verify(Long memberId, String code) {
	OtpCheckResult result = otpCodeChecker.check(memberId, code);   // 따로 커밋
	switch (result.type()) {
		case OK -> { }                                               // 통과
		case MISMATCH -> throw new BusinessException("... (오류 3/10회)");
		...
	}
}
```

- 이 메서드에는 일부러 `@Transactional`을 **붙이지 않았습니다.** 이체 트랜잭션 안에서 불려도 결과만 보고 예외를 던지는 역할입니다.
- 팀원2는 이체 Service에서 `otpService.verify(memberId, otpCode);` **한 줄만** 부르면 됩니다. 실패하면 `BusinessException`이 나와서 이체 트랜잭션이 롤백됩니다.

---

## 6. "왜 이렇게 했나요?" — 설계 이유 모음

| 결정 | 이유 | 관련 위치 |
|---|---|---|
| 외부 OTP 라이브러리를 쓰지 않음 | 계산이 30줄 정도로 짧고, 공통 파일인 `build.gradle`을 고치지 않아도 됨. 표준 테스트 값으로 정확성을 확인함 | `TotpGenerator` |
| 시드는 해시가 아니라 AES 암호화 | 서버가 시드로 번호를 계산해야 하므로 되돌릴 수 있어야 함 | `OtpSeedCipher` |
| 키는 설정 파일, Git 제외 | DB가 유출돼도 키 없이는 시드를 못 꺼냄 | `application.properties` |
| 키가 없어도 앱 시작 | OTP를 안 쓰는 팀원 개발 환경 보호 | `OtpSeedCipher` 생성자 |
| ±30초 허용 | 기기 시계 오차, 입력 지연 | `OtpCodeChecker` ③ |
| 같은 구간 재사용 금지 | 엿본 번호를 먼저 쓰는 공격 방지 | `lastUsedStep` |
| 10회 오류 시 정지 | 무작위로 계속 넣어 맞히는 공격 방지 (실제 은행 정책과 같음) | `OtpDevice.recordFailure()` |
| 오류 횟수는 REQUIRES_NEW | 이체가 롤백돼도 오류 횟수는 남아야 함 | `OtpCodeChecker` |
| 잠금 조회(FOR UPDATE) | 동시에 같은 번호로 두 번 통과하는 것 방지 | `OtpDeviceRepository` |
| 등록 코드는 1번만 표시 | 실제 은행이 기기를 손에 쥐여 주는 순간에 해당. 다시 보여 주면 세션만 훔쳐도 기기를 복제할 수 있음 | `OtpPageController.issued()` |
| 등록 전 기기는 다시 발급 가능 | 등록 코드를 잃어버린 고객 구제. 사용중 기기는 분실 신고 절차를 거쳐야 함 | `OtpService.issue()` |
| 회원당 1개 | 여러 개면 어느 것으로 검증할지 모호. DB 유일 인덱스로 강제 | `ux_otp_device_one_usable` |

---

## 7. 화면 흐름 (Controller와 HTML)

### 7-1. 주소 목록

| 주소 | 방식 | 하는 일 | 다음 화면 |
|---|---|---|---|
| `/mybank/security/otp` | GET | 상태 화면 | `security/otp.html` |
| `/mybank/security/otp/issue` | POST | 발급 | 성공 → `/issued`, 실패 → 상태 화면 |
| `/mybank/security/otp/issued` | GET | 등록 코드 화면 (1번만) | `security/otp-issued.html` |
| `/mybank/security/otp/activate` | POST | 사용 등록 | 상태 화면 |
| `/mybank/security/otp/verify` | POST | 번호 확인 | 상태 화면 |

- `/mybank/**`는 `SecurityConfig`에서 **로그인한 고객만** 들어올 수 있습니다. 그래서 컨트롤러에서 따로 로그인 검사를 하지 않습니다.
- 로그인한 고객 정보는 `@AuthenticationPrincipal LoginMember me`로 받고, `me.getMemberId()`를 씁니다. 다른 사람의 OTP를 건드릴 수 없습니다 (회원 번호를 화면에서 받지 않음).

### 7-2. PRG 패턴 (Post → Redirect → Get)

```
브라우저 ──POST /issue──▶ 서버: 발급 처리
         ◀──302 redirect /issued── (결과는 flash에 담음)
브라우저 ──GET /issued──▶ 서버: flash의 결과로 화면 그림
```

- POST 결과 화면을 바로 그리면, 사용자가 **새로고침(F5)할 때 POST가 다시 실행**됩니다 (OTP가 또 발급됨).
- 그래서 POST 처리 후에는 항상 다른 주소로 **리다이렉트**하고, 결과는 **flash 속성**에 담습니다.
- flash 속성은 **리다이렉트 직후 한 번만** 꺼낼 수 있고 그 뒤 사라집니다. 그래서 `/issued`를 새로고침하면 결과가 없어 상태 화면으로 돌아갑니다. 이것이 "등록 코드를 1번만 보여 주는" 원리입니다.

### 7-3. 화면 조각 재사용

`otp-issued.html` 안의 사용 등록 폼에 `th:fragment="activateForm"`을 붙였습니다. 상태 화면(`otp.html`)에서 ISSUED 상태일 때 이 조각을 `th:replace`로 그대로 가져다 씁니다. 같은 폼을 두 번 만들지 않으려는 것입니다.

### 7-4. 그 외

- **CSRF**: `th:action`을 쓰면 Thymeleaf가 폼에 CSRF 토큰을 자동으로 넣습니다. 토큰 없이 POST하면 Spring Security가 막습니다.
- **중복 제출 방지**: 폼에 `js-once-form` 클래스를 붙이면 팀원2의 `account.js`가 제출 버튼을 한 번만 누를 수 있게 잠급니다.
- **QR 코드**: `otp.js`가 cdnjs의 `qrcodejs` 라이브러리로 `data-uri` 값을 QR로 그립니다. 라이브러리를 못 불러오면 QR만 숨기고, 등록 코드로 등록하면 됩니다. 라이브러리 사용법은 11-2장에 정리했습니다.
- **입력칸**: `inputmode="numeric"`은 휴대폰에서 숫자 키패드를 띄우고, `pattern="\d{6}"`은 6자리 숫자가 아니면 제출 전에 브라우저가 막습니다. 서버에서도 다시 확인합니다(`TotpGenerator.matches`).

---

## 8. 테스트는 무엇을 확인하나

테스트 위치: `src/test/java/org/doit/onbank/otp/`
실행: `./gradlew test --tests "org.doit.onbank.otp.*"` 또는 IDE에서 ▶ 버튼

| 테스트 파일 | 확인하는 것 | 방식 |
|---|---|---|
| `TotpGeneratorTests` | RFC 6238 문서의 공식 정답과 같은 번호가 나오는지 | 순수 계산 |
| `Base32Tests` | RFC 4648 문서의 공식 정답, 띄어쓰기·소문자 허용 | 순수 계산 |
| `OtpSeedCipherTests` | 암호화→복호화 원래대로, 값 변조·다른 키면 실패, 키 없으면 사용할 때만 오류 | 순수 계산 |
| `OtpCodeCheckerTests` | ±30초 허용, 범위 밖 실패, 재사용 거절, 10회 정지, 정지 후 거절 | Mock + 고정 시각 |
| `OtpServiceTests` | 발급(암호화 저장·등록 코드 일치), 재발급, 사용중이면 거절, 연속 2회 등록, 메시지 | Mock + 고정 시각 |
| `OtpDeviceRepositoryTests` | **공용 DB 테이블과 엔티티가 맞는지**, 회원당 1개 제약, 폐기 후 재발급 | 실제 DB (끝나면 롤백) |
| `OtpPageControllerTests` | 로그인 필요, 상태별 화면, 리다이렉트·flash 메시지, 새로고침 시 코드 안 보임, CSRF | 가짜 Service + 실제 화면 |

**테스트 읽는 법 (예시)**

```java
@Test
void sameCodeCannotBeReused() {
	deviceIsActive();                          // 준비: 사용중 OTP
	checker.check(MEMBER_ID, code(STEP));      // 한 번 성공

	OtpCheckResult result = checker.check(MEMBER_ID, code(STEP));   // 같은 번호 다시

	assertThat(result.type()).isEqualTo(Type.REUSED);   // 확인: 재사용으로 거절
	assertThat(device.getFailCount()).isZero();         // 확인: 오류 횟수는 그대로
}
```

"준비 → 실행 → 확인" 세 덩어리로 읽으면 됩니다. 메서드 이름이 곧 "무엇을 확인하는지"입니다.

**Mock이란?** 진짜 DB 대신 "이렇게 물으면 이렇게 답하라"고 정해 둔 가짜 객체입니다. 예를 들어 `given(repository.findByMemberIdForUpdate(...)).willReturn(Optional.of(device))`는 "OTP를 조회하면 이 device를 돌려줘라"는 뜻입니다. DB 없이 규칙만 빠르게 확인할 수 있습니다.

---

## 9. 발표·질문 대비 Q&A

**Q. 서버가 OTP 번호를 고객에게 보내 주는 건가요?**
아니요. 서버와 앱은 등록 때 시드를 한 번 나눈 뒤로는 통신하지 않습니다. 각자 같은 시드와 같은 시각으로 계산해서 같은 번호가 나옵니다. 문자 인증번호와 가장 다른 점입니다.

**Q. Google Authenticator에서 나온 번호가 우리 서버에서 왜 맞나요?**
둘 다 국제 표준(TOTP, RFC 6238)으로 계산하기 때문입니다. 테스트에서 RFC 문서의 공식 정답과 비교해 확인했습니다.

**Q. DB가 해킹되면 OTP를 복제할 수 있나요?**
DB에는 암호화된 시드만 있습니다. 암호화 키는 DB가 아니라 서버 설정에 있어서, DB만 유출되면 시드를 꺼낼 수 없습니다. (실제 은행은 키를 HSM이라는 전용 보안 장비에 넣습니다.)

**Q. 번호를 계속 넣어 보면 언젠가 맞지 않나요?**
6자리라 한 번에 맞힐 확률은 100만 분의 1(허용 범위 3구간이면 약 33만 분의 1)입니다. 게다가 10회 틀리면 정지되므로 무작위로 맞히는 것은 사실상 불가능합니다.

**Q. 이체가 실패하면 OTP 오류 횟수도 취소되나요?**
아니요. 오류 횟수는 별도 트랜잭션(`REQUIRES_NEW`)으로 먼저 커밋되므로 이체가 롤백돼도 남습니다. 그래서 10회 정지가 제대로 동작합니다.

**Q. 등록 코드를 다시 볼 수 없나요?**
일부러 막았습니다. 다시 볼 수 있으면 로그인 세션만 훔쳐도 OTP를 복제할 수 있습니다. 등록 전이라면 "다시 발급"으로 새 코드를 받으면 됩니다.

**Q. 휴대폰 시계가 틀리면요?**
앞뒤 30초까지는 허용합니다. 그보다 많이 틀리면 번호가 맞지 않으니 휴대폰 시간을 "자동 설정"으로 맞추면 됩니다.

---

## 10. 용어 정리

| 용어 | 쉬운 뜻 |
|---|---|
| OTP | 한 번만 쓰는 비밀번호 |
| TOTP | 시각(Time)을 재료로 만드는 OTP. 30초마다 바뀜 |
| 시드 | 앱과 서버가 나눠 가진 비밀 재료 |
| 구간(step) | 30초 단위로 자른 시각 번호 |
| HMAC-SHA1 | 키와 메시지로 "지문"을 만드는 함수. 재료가 조금만 달라도 결과가 완전히 달라짐 |
| Base32 | 이진 데이터를 대문자+숫자 32종류 글자로 바꾸는 방법 |
| AES-GCM | 키로 잠그고 여는 암호화 방식 + 변조 확인 도장(인증 태그) |
| IV | 암호화할 때마다 새로 뽑는 무작위 값. 같은 내용도 결과가 달라지게 함 |
| 비관적 잠금 (FOR UPDATE) | 읽은 줄을 트랜잭션이 끝날 때까지 다른 요청이 못 건드리게 잠금 |
| REQUIRES_NEW | 지금 트랜잭션과 별개로 새 트랜잭션을 열어 따로 커밋 |
| 프록시 | Spring이 Bean을 감싸서 `@Transactional` 같은 기능을 끼워 넣는 대리 객체. 같은 클래스 안 호출에는 동작 안 함 |
| PRG | POST 처리 후 리다이렉트해서 GET으로 결과를 보여 주는 방식. 새로고침 중복 실행 방지 |
| flash 속성 | 리다이렉트 직후 한 번만 꺼낼 수 있는 임시 값 |
| CSRF | 다른 사이트가 사용자 몰래 우리 서버에 요청을 보내는 공격. 폼의 토큰으로 막음 |
| Mock | 테스트용 가짜 객체 |
| RFC | 인터넷 표준 문서. RFC 6238 = TOTP, RFC 4226 = HOTP, RFC 4648 = Base32 |

---

## 11. 부록: QR 코드와 `otpauth://` 주소 (공식 문서 요약)

발급 화면의 QR 코드는 두 단계로 만들어집니다.

```
[서버] OtpService.otpauthUri()  →  "otpauth://totp/OnBank:0123456789?secret=...&..." 문자열
         │  (otp-issued.html 의 data-uri 속성으로 전달)
         ▼
[브라우저] otp.js  →  qrcodejs 라이브러리  →  QR 이미지
```

- 서버(Java)는 **QR에 담을 문자열만** 만듭니다. Java 쪽 QR 라이브러리는 쓰지 않습니다.
- QR 이미지는 **브라우저에서 JavaScript 라이브러리 qrcodejs**가 그립니다.

아래 내용은 두 공식 문서(영어)를 한글로 요약한 것입니다. 정확한 원문이 필요하면 링크를 확인하세요.

### 11-1. `otpauth://` 주소 형식 (Key Uri Format)

- 원문: https://github.com/google/google-authenticator/wiki/Key-Uri-Format
- Google이 정한 형식이고, Google Authenticator·Microsoft Authenticator 등 대부분의 OTP 앱이 이 형식의 QR을 읽습니다.
- 참고: 이 위키가 있는 저장소는 2021년 4월에 보관(archived) 처리되어 더 이상 수정되지 않지만, 형식 자체는 지금도 널리 쓰입니다.

**전체 구조**

```
otpauth://TYPE/LABEL?PARAMETERS
```

**TYPE (종류)**

| 값 | 뜻 |
|---|---|
| `totp` | 시각 기반 OTP (30초마다 바뀜) — **우리가 쓰는 것** |
| `hotp` | 횟수(카운터) 기반 OTP (버튼 누를 때마다 바뀜) |

**LABEL (앱에 표시되는 이름)**

```
발급자:계정이름      예) OnBank:0123456789
```

- 계정 이름 앞에 **발급자(issuer)** 를 붙이고 콜론(`:`)으로 구분합니다. 콜론은 `%3A`로 써도 됩니다.
- 발급자와 계정 이름 **안에는 콜론이 들어가면 안 됩니다.**
- 공백 같은 특수문자는 URL 인코딩합니다. (예: `ACME%20Co`)
- 공식 문서는 **발급자를 LABEL 앞부분과 `issuer` 파라미터 양쪽 모두에 넣기를 권장**합니다. 옛날 버전 앱은 `issuer` 파라미터를 무시하고 LABEL 앞부분만 보기 때문입니다.

**PARAMETERS (뒤에 붙는 값)**

| 이름 | 필수 여부 | 기본값 | 뜻 | 우리 값 |
|---|---|---|---|---|
| `secret` | **필수** | 없음 | 시드를 Base32로 바꾼 것. 끝의 `=` 패딩은 **빼는 것**이 규칙 | 등록 코드 (32글자, 패딩 없음) |
| `issuer` | **강력 권장** | LABEL 앞부분 | 발급자 이름. LABEL과 둘 다 있으면 **같아야** 함 | `OnBank` |
| `algorithm` | 선택 | `SHA1` | 계산 방식. `SHA1` / `SHA256` / `SHA512` | `SHA1` |
| `digits` | 선택 | `6` | 번호 자릿수. `6` 또는 `8` | `6` |
| `period` | 선택 (totp만) | `30` | 번호가 바뀌는 주기(초) | `30` |
| `counter` | hotp일 때 필수 | 없음 | 카운터 시작값 | (쓰지 않음) |

**주의: Google Authenticator는 일부 값을 무시합니다** (공식 문서에 적힌 내용)

- `algorithm` → 무시하고 항상 SHA1로 계산
- `period` → 무시하고 항상 30초
- `digits` → Android 앱에서는 무시하고 항상 6자리

그래서 **기본값(SHA1, 6자리, 30초)을 그대로 쓰는 것이 가장 안전합니다.** 우리 구현은 세 값 모두 기본값이라 어느 앱에서나 같은 번호가 나옵니다. 기본값이어도 주소에 명시해 둔 것은 다른 앱이 헷갈리지 않게 하려는 것입니다.

**우리 주소를 하나씩 뜯어 보면**

```
otpauth://totp/OnBank:0123456789?secret=ABCD...&issuer=OnBank&algorithm=SHA1&digits=6&period=30
          ^^^^ ^^^^^^ ^^^^^^^^^^        ^^^^^^^        ^^^^^^           ^^^^        ^        ^^
          종류  발급자  일련번호         등록 코드      발급자(다시)     계산 방식    자릿수   주기(초)
```

- 만드는 코드: `OtpService.otpauthUri()`
- `OnBank`와 일련번호(숫자 10자리)에는 공백·콜론이 없어서 URL 인코딩이 필요 없습니다. 나중에 발급자 이름을 `On Bank`처럼 공백이 있게 바꾸면 인코딩을 추가해야 합니다.

### 11-2. qrcodejs 라이브러리

- 원문(사용법): https://github.com/davidshimjs/qrcodejs
- 데모: https://davidshimjs.github.io/qrcodejs/
- 라이선스: **MIT** (무료, 상업용 포함 자유롭게 사용 가능. 저작권 표시만 유지)
- 다른 라이브러리에 **의존하지 않는** 순수 JavaScript 파일 하나입니다.
- 브라우저의 **Canvas**(그림 그리기 기능)로 그리고, Canvas가 없는 옛날 브라우저에서는 HTML **table**로 그립니다.

**불러오기** (`otp-issued.html`)

```html
<script src="https://cdnjs.cloudflare.com/ajax/libs/qrcodejs/1.0.0/qrcode.min.js"></script>
<script th:src="@{/js/otp.js}"></script>   <!-- 라이브러리 다음에 불러와야 QRCode 를 쓸 수 있음 -->
```

**만드는 법**

```js
// 가장 간단한 형태 : 글자만 넘김
new QRCode(document.getElementById('otp-qr'), 'otpauth://totp/...');

// 옵션을 넘기는 형태 (우리가 쓰는 방식, otp.js)
new QRCode(qr, {
    text: qr.dataset.uri,                      // QR에 담을 글자 (otpauth 주소)
    width: 180,                                // 가로 크기(px)
    height: 180,                               // 세로 크기(px)
    correctLevel: QRCode.CorrectLevel.M        // 오류 복원 수준
});
```

| 옵션 | 뜻 | 우리 값 |
|---|---|---|
| `text` | QR에 담을 글자 | `data-uri` 속성 값 |
| `width`, `height` | 크기(px) | 180 × 180 |
| `colorDark` | 점(어두운 칸) 색 | 지정 안 함 (검정) |
| `colorLight` | 배경(밝은 칸) 색 | 지정 안 함 (흰색) |
| `correctLevel` | 오류 복원 수준 (아래 표) | `M` |

**오류 복원 수준 (`correctLevel`)**

QR 코드는 일부가 가려지거나 더러워져도 읽히도록 여분의 정보를 함께 넣습니다. 수준이 높을수록 잘 읽히지만 QR 점이 촘촘해집니다. (복원 비율은 QR 코드 표준의 값입니다.)

| 값 | 복원 가능 비율 | 설명 |
|---|---|---|
| `L` | 약 7% | 가장 단순한 모양 |
| `M` | 약 15% | **우리 선택.** 화면에 띄워 카메라로 찍는 용도라 손상될 일이 적고, 점이 너무 촘촘하지 않음 |
| `Q` | 약 25% | |
| `H` | 약 30% | 인쇄물처럼 훼손 가능성이 있을 때. 공식 문서 예제에서 쓰는 값 |

**그 밖의 기능**

```js
const qrcode = new QRCode(element, { text: '처음 글자' });
qrcode.clear();               // 그린 QR 지우기
qrcode.makeCode('새 글자');    // 같은 자리에 다른 QR 다시 그리기
```

우리는 발급 화면에서 한 번만 그리므로 이 두 기능은 쓰지 않습니다.

### 11-3. 알아 두면 좋은 점

- **QR 안에는 시드가 그대로 들어 있습니다.** QR 사진만 있어도 같은 OTP를 만들 수 있으므로, 발급 화면을 한 번만 보여 주는 것입니다(6장 "등록 코드는 1번만 표시").
- **QR은 브라우저 안에서만 그려집니다.** qrcodejs는 받은 글자로 그림만 그리고 어디로도 보내지 않습니다. 다만 라이브러리 파일 자체는 외부 CDN(cdnjs)에서 받아 옵니다.
- **인터넷이 안 되는 환경**(발표장 등)에서는 CDN을 못 불러와 QR이 안 보일 수 있습니다. 이때는 등록 코드를 앱에 직접 입력하면 됩니다. 확실하게 하려면 `qrcode.min.js`를 `static/js/`에 내려받아 두고 `th:src="@{/js/qrcode.min.js}"`로 바꿉니다.

---

## 다음 단계 (참고)

1. 팀원2 이체 화면에 OTP 입력칸 + `otpService.verify()` 연결 (`BusinessException`도 catch하도록)
2. 분실 신고(ACTIVE → LOST)와 재발급
3. 정지 해제 (직원 화면, 팀원4와 협의)
4. 가상 OTP 기기 페이지 `/otp-device` (브라우저가 직접 계산, auth.md 4-4장)
5. 본인확인 기능이 생기면 `issue()`에 `requireVerified()` 추가
