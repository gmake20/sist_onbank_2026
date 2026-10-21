package org.doit.onbank.member.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Locale;

import org.doit.onbank.common.exception.BusinessException;
import org.doit.onbank.common.mail.EmailSender;
import org.doit.onbank.member.repository.MemberRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

// 회원가입 이메일 인증 : 인증번호 생성·메일 발송·검증
// 비밀번호 찾기(임시 비밀번호)가 가입한 이메일로 가므로, 가입할 때 실제로 받을 수 있는 주소인지 확인한다
// - 휴대폰 인증(PhoneVerificationService)과 같은 방식 : 인증번호, 만료시각, 입력 횟수를 세션에 이메일별로 저장
// - 인증에 성공하면 "emailVerified:주소"를 남기고, 회원가입 POST에서 서버가 다시 확인한다
// - 이메일은 대소문자를 구분하지 않도록 소문자로 바꿔 세션 키로 쓴다
// - onbank.email-verification.enabled=false 이면 인증을 쓰지 않는다
// 다음 단계 : 하루 발송 횟수 제한 (주소별, IP별)
@Service
@RequiredArgsConstructor
public class EmailVerificationService {

	public static final Duration CODE_TTL = Duration.ofMinutes(5);	// 메일은 문자보다 늦게 도착할 수 있어 5분
	public static final Duration RESEND_INTERVAL = Duration.ofSeconds(60);
	private static final int MAX_ATTEMPTS = 5;
	private static final String EMAIL_PATTERN = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$";
	private static final int EMAIL_MAX_LENGTH = 100;

	private static final String CODE = "emailCode:";
	private static final String EXPIRES_AT = "emailCodeExp:";
	private static final String SENT_AT = "emailSentAt:";
	private static final String ATTEMPTS = "emailAttempts:";
	private static final String VERIFIED = "emailVerified:";

	private final EmailSender emailSender;
	private final MemberRepository memberRepository;
	private final SecureRandom random = new SecureRandom();

	@Value("${onbank.email-verification.enabled:true}")
	private boolean enabled;

	public boolean isEnabled() {
		return enabled;
	}

	// 메일 발송에 실패하면 MailException (세션에는 아무것도 남기지 않음)
	public void send(String email, HttpSession session) {
		if (!enabled) {
			throw new BusinessException("지금은 이메일 인증을 사용하지 않습니다.");
		}
		String key = normalize(email);
		checkFormat(key);
		// 이미 가입된 이메일이면 메일을 보내지 않는다
		if (memberRepository.existsByEmail(key)) {
			throw new BusinessException("이미 가입된 이메일입니다.");
		}

		LocalDateTime now = LocalDateTime.now();
		LocalDateTime sentAt = (LocalDateTime) session.getAttribute(SENT_AT + key);
		if (sentAt != null && sentAt.plus(RESEND_INTERVAL).isAfter(now)) {
			long waitSeconds = Duration.between(now, sentAt.plus(RESEND_INTERVAL)).toSeconds() + 1;
			throw new BusinessException("인증번호는 " + waitSeconds + "초 후에 다시 받을 수 있습니다.");
		}

		String code = String.format("%06d", random.nextInt(1_000_000));
		emailSender.send(email.trim(), "[ONBANK] 회원가입 이메일 인증번호", """
				ONBANK 회원가입 이메일 인증번호입니다.

				인증번호 : %s

				가입 화면에 %d분 이내로 입력해 주세요.
				본인이 요청하지 않았다면 이 메일을 무시하셔도 됩니다.
				""".formatted(code, CODE_TTL.toMinutes()));

		session.setAttribute(CODE + key, code);
		session.setAttribute(EXPIRES_AT + key, now.plus(CODE_TTL));
		session.setAttribute(SENT_AT + key, now);
		session.setAttribute(ATTEMPTS + key, 0);
		session.removeAttribute(VERIFIED + key);
	}

	// 실패하면 사유를 담은 BusinessException
	public void verify(String email, String code, HttpSession session) {
		String key = normalize(email);
		checkFormat(key);
		String saved = (String) session.getAttribute(CODE + key);
		LocalDateTime expiresAt = (LocalDateTime) session.getAttribute(EXPIRES_AT + key);
		if (saved == null || expiresAt == null) {
			throw new BusinessException("인증번호를 먼저 받아 주세요.");
		}
		if (expiresAt.isBefore(LocalDateTime.now())) {
			removeCode(key, session);
			throw new BusinessException("인증번호가 만료되었습니다. 다시 받아 주세요.");
		}
		if (code == null || code.isBlank()) {
			throw new BusinessException("인증번호를 입력하세요.");
		}

		// 비교 시간으로 일치 여부를 추측하지 못하도록 고정 시간 비교
		if (MessageDigest.isEqual(saved.getBytes(StandardCharsets.UTF_8), code.trim().getBytes(StandardCharsets.UTF_8))) {
			removeCode(key, session);
			session.setAttribute(VERIFIED + key, true);
			return;
		}

		int attempts = (Integer) session.getAttribute(ATTEMPTS + key) + 1;
		if (attempts >= MAX_ATTEMPTS) {
			removeCode(key, session);
			throw new BusinessException("입력 횟수(" + MAX_ATTEMPTS + "회)를 초과했습니다. 인증번호를 다시 받아 주세요.");
		}
		session.setAttribute(ATTEMPTS + key, attempts);
		throw new BusinessException("인증번호가 일치하지 않습니다. (남은 횟수 " + (MAX_ATTEMPTS - attempts) + "회)");
	}

	public boolean isVerified(String email, HttpSession session) {
		return email != null && Boolean.TRUE.equals(session.getAttribute(VERIFIED + normalize(email)));
	}

	// 회원가입 완료 후 인증 흔적을 지운다
	public void clear(String email, HttpSession session) {
		if (email == null) {
			return;
		}
		String key = normalize(email);
		removeCode(key, session);
		session.removeAttribute(SENT_AT + key);
		session.removeAttribute(VERIFIED + key);
	}

	private String normalize(String email) {
		return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
	}

	private void checkFormat(String email) {
		if (email == null || email.length() > EMAIL_MAX_LENGTH || !email.matches(EMAIL_PATTERN)) {
			throw new BusinessException("이메일 형식이 올바르지 않습니다.");
		}
	}

	private void removeCode(String email, HttpSession session) {
		session.removeAttribute(CODE + email);
		session.removeAttribute(EXPIRES_AT + email);
		session.removeAttribute(ATTEMPTS + email);
	}
}
