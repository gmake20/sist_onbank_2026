package org.doit.onbank.member.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;

import org.doit.onbank.common.exception.BusinessException;
import org.doit.onbank.common.sms.SmsSender;
import org.doit.onbank.member.repository.MemberRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

// 회원가입 휴대폰 인증 : 인증번호 생성·발송·검증 (docs/auth.md 3장 ③)
// - 인증번호, 만료시각, 입력 횟수는 세션에 휴대폰 번호별로 저장
// - 인증에 성공하면 "phoneVerified:번호"를 남기고, 회원가입 POST에서 서버가 다시 확인한다
// - onbank.phone-verification.enabled=false 이면 인증을 쓰지 않는다 (문자 서비스 연동 전)
// 다음 단계 : 하루 발송 횟수 제한 (번호 5회, IP 20회). 실제 발송(prod) 전에 반드시 추가
@Service
@RequiredArgsConstructor
public class PhoneVerificationService {

	public static final Duration CODE_TTL = Duration.ofMinutes(3);
	public static final Duration RESEND_INTERVAL = Duration.ofSeconds(60);
	private static final int MAX_ATTEMPTS = 5;
	private static final String PHONE_PATTERN = "^010\\d{8}$";

	private static final String CODE = "phoneCode:";
	private static final String EXPIRES_AT = "phoneCodeExp:";
	private static final String SENT_AT = "phoneSentAt:";
	private static final String ATTEMPTS = "phoneAttempts:";
	private static final String VERIFIED = "phoneVerified:";

	// prod용 발송기(ApiSmsSender)가 아직 없어도 서버가 뜨도록 발송할 때 꺼낸다
	private final ObjectProvider<SmsSender> smsSender;
	private final MemberRepository memberRepository;
	private final SecureRandom random = new SecureRandom();

	@Value("${onbank.phone-verification.enabled:false}")
	private boolean enabled;

	public boolean isEnabled() {
		return enabled;
	}

	public void send(String phone, HttpSession session) {
		SmsSender sender = smsSender.getIfAvailable();
		if (!enabled || sender == null) {
			throw new BusinessException("지금은 휴대폰 인증을 사용하지 않습니다.");
		}
		checkFormat(phone);
		// 이미 가입된 번호면 문자를 보내지 않는다 (불필요한 발송 비용 방지)
		if (memberRepository.existsByPhone(phone)) {
			throw new BusinessException("이미 가입된 휴대폰 번호입니다.");
		}

		LocalDateTime now = LocalDateTime.now();
		LocalDateTime sentAt = (LocalDateTime) session.getAttribute(SENT_AT + phone);
		if (sentAt != null && sentAt.plus(RESEND_INTERVAL).isAfter(now)) {
			long waitSeconds = Duration.between(now, sentAt.plus(RESEND_INTERVAL)).toSeconds() + 1;
			throw new BusinessException("인증번호는 " + waitSeconds + "초 후에 다시 받을 수 있습니다.");
		}

		String code = String.format("%06d", random.nextInt(1_000_000));
		sender.send(phone,"[OnBank] 인증번호 [" + code + "]를 입력해주세요. (3분 이내)");

		session.setAttribute(CODE + phone, code);
		session.setAttribute(EXPIRES_AT + phone, now.plus(CODE_TTL));
		session.setAttribute(SENT_AT + phone, now);
		session.setAttribute(ATTEMPTS + phone, 0);
		session.removeAttribute(VERIFIED + phone);
	}

	// 실패하면 사유를 담은 BusinessException
	public void verify(String phone, String code, HttpSession session) {
		checkFormat(phone);
		String saved = (String) session.getAttribute(CODE + phone);
		LocalDateTime expiresAt = (LocalDateTime) session.getAttribute(EXPIRES_AT + phone);
		if (saved == null || expiresAt == null) {
			throw new BusinessException("인증번호를 먼저 받아 주세요.");
		}
		if (expiresAt.isBefore(LocalDateTime.now())) {
			removeCode(phone, session);
			throw new BusinessException("인증번호가 만료되었습니다. 다시 받아 주세요.");
		}
		if (code == null || code.isBlank()) {
			throw new BusinessException("인증번호를 입력하세요.");
		}

		// 비교 시간으로 일치 여부를 추측하지 못하도록 고정 시간 비교
		if (MessageDigest.isEqual(saved.getBytes(StandardCharsets.UTF_8), code.trim().getBytes(StandardCharsets.UTF_8))) {
			removeCode(phone, session);
			session.setAttribute(VERIFIED + phone, true);
			return;
		}

		int attempts = (Integer) session.getAttribute(ATTEMPTS + phone) + 1;
		if (attempts >= MAX_ATTEMPTS) {
			removeCode(phone, session);
			throw new BusinessException("입력 횟수(" + MAX_ATTEMPTS + "회)를 초과했습니다. 인증번호를 다시 받아 주세요.");
		}
		session.setAttribute(ATTEMPTS + phone, attempts);
		throw new BusinessException("인증번호가 일치하지 않습니다. (남은 횟수 " + (MAX_ATTEMPTS - attempts) + "회)");
	}

	public boolean isVerified(String phone, HttpSession session) {
		return phone != null && Boolean.TRUE.equals(session.getAttribute(VERIFIED + phone));
	}

	// 회원가입 완료 후 인증 흔적을 지운다
	public void clear(String phone, HttpSession session) {
		removeCode(phone, session);
		session.removeAttribute(SENT_AT + phone);
		session.removeAttribute(VERIFIED + phone);
	}

	private void checkFormat(String phone) {
		if (phone == null || !phone.matches(PHONE_PATTERN)) {
			throw new BusinessException("휴대폰 번호는 010으로 시작하는 숫자 11자리로 입력하세요.");
		}
	}

	private void removeCode(String phone, HttpSession session) {
		session.removeAttribute(CODE + phone);
		session.removeAttribute(EXPIRES_AT + phone);
		session.removeAttribute(ATTEMPTS + phone);
	}
}
