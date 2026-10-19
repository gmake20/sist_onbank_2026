# OTP 사용 안내 (팀원 공유용)

- 작성: 2026-10-20
- 대상: OTP 기능을 직접 써 보거나 테스트할 팀원
- 내부 구조가 궁금하면 [OTP-구현-설명.md](OTP-구현-설명.md)를 보세요. 이 문서는 **사용법만** 다룹니다.

---

## 1. OTP가 뭔가요?

이체할 때 **계좌 비밀번호와 함께 입력하는 6자리 번호**입니다.

- 번호는 휴대폰 OTP 앱에 표시되고, **30초마다 바뀝니다.**
- 한 번 쓴 번호는 다시 쓸 수 없습니다.
- 실물 OTP 기기 대신 **휴대폰 앱**(Google Authenticator 또는 Microsoft Authenticator)을 씁니다. 앱스토어나 플레이스토어에서 무료로 받을 수 있습니다.

---

## 2. 준비물

- 서버 실행, **일반 회원 계정**으로 로그인
- 휴대폰에 OTP 앱 설치 (Google Authenticator 추천)
- 휴대폰 시간 설정이 **"자동"** 으로 되어 있는지 확인 (시간이 틀리면 번호가 맞지 않습니다)

---

## 3. 처음 한 번: 발급하고 등록하기

### ① OTP 화면 들어가기

다음 중 하나로 들어갑니다.

- 마이페이지 → **보안설정 (OTP)** 버튼
- 주소창에 `http://localhost/mybank/security/otp` 입력

"등록된 OTP가 없습니다."가 보이면 정상입니다.

### ② 발급받기

**[OTP 발급]** 버튼을 누릅니다. QR 코드와 **등록 코드**(영문·숫자 32글자)가 나옵니다.

> ⚠️ 이 화면은 **딱 한 번만** 보입니다. 새로고침하거나 다른 화면으로 가면 다시 볼 수 없으니, 바로 다음 단계로 앱에 등록하세요.

### ③ 휴대폰 앱에 등록하기

OTP 앱에서 **+** 버튼을 누르고 둘 중 하나를 고릅니다.

- **QR 코드 스캔**: 화면의 QR 코드를 카메라로 찍습니다. (가장 쉬움)
- **설정 키 입력**: QR이 안 보이거나 스캔이 안 될 때. 등록 코드를 그대로 입력하고, 키 유형은 **"시간 기준"** 으로 둡니다.

등록되면 앱에 **"OnBank"** 라는 이름으로 6자리 번호가 나타나고, 30초마다 바뀝니다.

### ④ 사용 등록 (번호 두 번 입력)

같은 화면 아래 **OTP 사용 등록** 칸에서:

1. 앱에 지금 보이는 번호를 **첫 번째 번호**에 입력
2. **번호가 바뀔 때까지 기다린 뒤**(최대 30초) 새 번호를 **두 번째 번호**에 입력
3. **[사용 등록]** 버튼

"OTP 사용 등록이 완료되었습니다."가 나오고, 상태가 **사용중**으로 바뀌면 끝입니다.

> 같은 번호를 두 칸에 넣으면 실패합니다. 반드시 번호가 한 번 바뀐 뒤에 두 번째를 입력하세요.

### ⑤ (선택) 잘 되는지 확인

OTP 화면의 **OTP 번호 확인** 칸에 앱의 지금 번호를 넣고 **[번호 확인]** → "OTP 번호가 맞습니다."가 나오면 정상입니다.

---

## 4. 이체할 때 쓰기: OTP 확인 함수 부르는 법

OTP 번호가 맞는지는 **`OtpService.verify()` 한 줄**로 확인합니다. 이체(팀원2)에 이미 이렇게 붙어 있으니, 다른 기능에 OTP를 붙일 때도 똑같이 하면 됩니다.

### ① OtpService 받아 오기

Service 클래스에 필드 하나만 추가합니다.

```java
@Service
@RequiredArgsConstructor
public class AccountTransferService {

	private final OtpService otpService;	// 추가
	...
}
```

### ② 확인 함수 부르기

```java
otpService.verify(memberId, otpCode);
```

| 넘기는 값 | 뜻 |
|---|---|
| `memberId` | 로그인한 회원 번호 (`me.getMemberId()`) |
| `otpCode` | 화면에서 입력받은 6자리 번호 (문자열) |

- **맞으면** 아무 일 없이 다음 줄로 넘어갑니다.
- **틀리면** `BusinessException`이 나오고, 메시지에 사용자에게 보여 줄 안내 문구가 들어 있습니다. (예: "OTP 번호가 올바르지 않습니다. (오류 3/10회)")
- 오류 횟수 기록, 10회 정지, 같은 번호 재사용 막기는 **함수 안에서 알아서** 합니다. 부르는 쪽에서 따로 할 일은 없습니다.
- 이체 중에 틀려서 이체가 취소돼도 **오류 횟수는 그대로 남습니다.**

### ③ 실제 이체 코드 (`AccountTransferService.transfer()`)

```java
// 1. OTP가 필요한 이체인지 판단 (타인 계좌 + 10만 원 초과)
boolean otpRequired = requiresOtp(memberId, toAccountInfo.getMemberId(), amount);
if (otpRequired) {
	if (!isOtpReady(memberId)) {	// OTP를 등록하지 않은 회원
		throw new OtpRequiredException("100,000원을 넘는 이체는 OTP 등록이 필요해요.");
	}
	if (otpCode == null || !otpCode.matches("\\d{6}")) {
		throw new IllegalArgumentException("OTP 번호 6자리를 입력하세요.");
	}
}

// 2. 계좌 비밀번호 확인
...

// 3. OTP 확인 ← 여기
if (otpRequired) {
	otpService.verify(memberId, otpCode);
}

// 4. 실제 출금·입금
...
```

**순서가 중요합니다:** OTP 확인은 **계좌 비밀번호 확인 다음, 실제 출금 전**에 합니다. 돈이 움직이기 전에 확인해야 하기 때문입니다.

**OTP 등록 여부를 미리 보고 싶을 때:** `otpService.findCurrent(memberId)`로 회원의 OTP 상태를 볼 수 있습니다. 이체에서는 이것을 감싼 `isOtpReady()`로 "등록 안 한 회원"을 먼저 걸러, **[OTP 등록하기]** 버튼을 보여 줍니다.

### ④ Controller에서 오류 메시지 보여 주기 (`TransferPageController`)

```java
try {
	accountTransferService.transfer(..., form.getOtpCode(), ...);
	return "redirect:/mybank/transfer/result";
} catch (OtpRequiredException e) {			// OTP 미등록
	model.addAttribute("errorMessage", e.getMessage());
	model.addAttribute("otpRegisterNeeded", true);	// [OTP 등록하기] 버튼 표시
	return showForm(me, model);
} catch (BusinessException e) {				// OTP 번호 틀림·정지 등
	model.addAttribute("errorMessage", e.getMessage());	// 메시지를 그대로 화면에 표시
	return showForm(me, model);
}
```

`BusinessException`을 **꼭 catch** 해 주세요. 빠뜨리면 OTP가 틀렸을 때 안내 문구 대신 오류 페이지가 나옵니다.

---

## 5. 이럴 땐 이렇게 (자주 보는 메시지)

| 화면 메시지 | 뜻 | 해결 |
|---|---|---|
| OTP 번호가 올바르지 않습니다. (오류 N/10회) | 번호가 틀림 | 앱의 **지금** 번호를 다시 입력 |
| 이미 사용한 OTP 번호입니다. | 방금 쓴 번호를 다시 넣음 | 앱에서 번호가 바뀐 뒤 새 번호 입력 (오류 횟수는 안 늘어남) |
| OTP 번호가 맞지 않습니다. … 이어서 입력해 주세요. | 사용 등록 실패 | 첫 번호 입력 → 번호가 바뀐 뒤 두 번째 입력. 다시 시도 |
| OTP 사용 등록을 먼저 완료해 주세요. | 발급만 하고 등록을 안 함 | 3장 ④ 사용 등록 진행 |
| 등록된 OTP가 없습니다. … | OTP 발급 전 | 3장 처음부터 진행 |
| 이미 등록된 OTP가 있습니다. 분실 신고 후 다시 발급받아 주세요. | 사용중인 OTP가 있는데 또 발급하려 함 | 지금 OTP를 그대로 사용 |
| OTP 번호 오류 10회로 … 정지 | 연속 10회 틀려서 정지 | 아래 6장 참고 |

**번호가 계속 틀린다면?** 거의 대부분 **휴대폰이나 PC 시간이 어긋난 경우**입니다. 둘 다 시간 설정을 "자동"으로 바꾸고 다시 해 보세요.

**등록 코드 화면을 놓쳤다면?** 사용 등록 **전**이라면 OTP 화면에서 **[다시 발급]** 을 누르면 새 QR이 나옵니다. (이전 것은 못 씁니다. 앱에 예전 것이 있으면 지우세요.)

---

## 6. 10번 틀리면 정지됩니다

- 번호를 **연속 10회** 틀리면 OTP가 **정지**됩니다. 중간에 한 번이라도 맞으면 횟수는 0으로 돌아갑니다.
- OTP 화면에서 **연속 오류 N / 10회** 로 지금 횟수를 볼 수 있습니다.
- 정지되면 번호가 맞아도 OTP가 필요한 이체를 할 수 없습니다.
- 사용 등록 단계에서 틀린 것은 횟수에 들어가지 않습니다.

> ⚠️ **아직 화면에서 정지를 풀 수 없습니다.** (직원 화면의 정지 해제 기능은 다음 단계 예정)
> 정지 테스트는 **테스트용 계정으로, 맨 마지막에** 해 주세요. 풀어야 하면 OTP 담당자에게 알려 주세요.

### 정지되면 DB에서 바뀌는 값

테이블: **`otp_device`** (회원 1명당 사용 중인 OTP 1줄)

| 컬럼 | 정지 전 (사용중) | 정지 후 | 설명 |
|---|---|---|---|
| `status` | `ACTIVE` | **`SUSPENDED`** | OTP 상태. 정지의 핵심 값 |
| `fail_count` | 0 ~ 9 | **`10`** | 연속으로 틀린 횟수. 틀릴 때마다 1씩 오르고, 맞으면 0으로 돌아감 |
| `updated_at` | 이전 시각 | 정지된 시각 | 마지막으로 바뀐 시각 (자동 기록) |

나머지 컬럼(`serial_no`, `seed_encrypted`, `last_used_step`, `activated_at` 등)은 바뀌지 않습니다.

**정지된 OTP 찾기**

```sql
SELECT otp_device_id, member_id, serial_no, status, fail_count, updated_at
FROM otp_device
WHERE status = 'SUSPENDED';
```

**테스트 후 정지 풀기** (OTP 담당자와 상의 후, 테스트 계정만)

```sql
UPDATE otp_device
SET status = 'ACTIVE', fail_count = 0
WHERE member_id = ? AND status = 'SUSPENDED';
```

`status`만 바꾸고 `fail_count`를 그대로 두면 다음에 한 번만 틀려도 바로 다시 정지되니, **두 값을 함께** 바꿔야 합니다.

---

## 7. 테스트 체크리스트

- [ ] 발급 → QR 스캔 → 앱에 "OnBank" 표시
- [ ] 사용 등록 (번호 두 번) → 상태 "사용중"
- [ ] 번호 확인 성공
- [ ] 다른 사람 계좌로 10만 원 초과 이체 → OTP 입력해야 성공
- [ ] 10만 원 이하 / 내 계좌끼리 이체 → OTP 없이 성공
- [ ] 틀린 번호 → 오류 횟수 증가
- [ ] 방금 쓴 번호 다시 입력 → "이미 사용한 번호" 안내
- [ ] (마지막에, 테스트 계정으로) 10회 틀림 → 정지
