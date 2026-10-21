package org.doit.onbank.member.controller;

import java.util.Map;

import org.doit.onbank.common.exception.BusinessException;
import org.doit.onbank.member.service.EmailVerificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.MailException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// 회원가입 화면(fetch)에서 호출하는 이메일 인증 API (휴대폰 인증 PhoneVerifyApiController와 같은 형식)
// 응답 : { ok: true/false, message: "안내 문구" }. 실패는 400, 메일 발송 실패는 500
// 화면이 폼 안의 _csrf 값을 함께 보낸다 (CSRF 보호를 끄지 않음)
@Slf4j
@RestController
@RequestMapping("/api/email")
@RequiredArgsConstructor
public class EmailVerifyApiController {

	private final EmailVerificationService emailVerificationService;

	@PostMapping("/send")
	public Map<String, Object> send(@RequestParam String email, HttpSession session) {
		emailVerificationService.send(email, session);
		return Map.of(
				"ok", true,
				"message", "인증번호를 메일로 보냈습니다. " + EmailVerificationService.CODE_TTL.toMinutes()
						+ "분 안에 입력해 주세요. (메일이 없으면 스팸함도 확인해 주세요)",
				"expiresIn", EmailVerificationService.CODE_TTL.toSeconds(),
				"resendIn", EmailVerificationService.RESEND_INTERVAL.toSeconds());
	}

	@PostMapping("/verify")
	public Map<String, Object> verify(@RequestParam String email, @RequestParam String code, HttpSession session) {
		emailVerificationService.verify(email, code, session);
		return Map.of("ok", true, "message", "이메일 인증이 완료되었습니다.");
	}

	@ExceptionHandler(BusinessException.class)
	ResponseEntity<Map<String, Object>> fail(BusinessException e) {
		return ResponseEntity.badRequest().body(Map.of("ok", false, "message", e.getMessage()));
	}

	@ExceptionHandler(MailException.class)
	ResponseEntity<Map<String, Object>> mailFail(MailException e) {
		log.error("이메일 인증번호 발송 실패", e);
		return ResponseEntity.internalServerError()
				.body(Map.of("ok", false, "message", "메일 발송 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요."));
	}

	@ExceptionHandler(MissingServletRequestParameterException.class)
	ResponseEntity<Map<String, Object>> missingParam(MissingServletRequestParameterException e) {
		return ResponseEntity.badRequest().body(Map.of("ok", false, "message", "입력값을 확인해 주세요."));
	}
}
