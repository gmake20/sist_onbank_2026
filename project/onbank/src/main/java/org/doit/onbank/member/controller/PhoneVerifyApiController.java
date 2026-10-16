package org.doit.onbank.member.controller;

import java.util.Map;

import org.doit.onbank.common.exception.BusinessException;
import org.doit.onbank.member.service.PhoneVerificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

// 회원가입 화면(fetch)에서 호출하는 휴대폰 인증 API
// 응답 : { ok: true/false, message: "안내 문구" }. 실패는 400
// 화면이 폼 안의 _csrf 값을 함께 보낸다 (CSRF 보호를 끄지 않음)
@RestController
@RequestMapping("/api/phone")
@RequiredArgsConstructor
public class PhoneVerifyApiController {

	private final PhoneVerificationService phoneVerificationService;

	@PostMapping("/send")
	public Map<String, Object> send(@RequestParam String phone, HttpSession session) {
		phoneVerificationService.send(phone, session);
		return Map.of(
				"ok", true,
				"message", "인증번호를 보냈습니다. 3분 안에 입력해 주세요.",
				"expiresIn", PhoneVerificationService.CODE_TTL.toSeconds(),
				"resendIn", PhoneVerificationService.RESEND_INTERVAL.toSeconds());
	}

	@PostMapping("/verify")
	public Map<String, Object> verify(@RequestParam String phone, @RequestParam String code, HttpSession session) {
		phoneVerificationService.verify(phone, code, session);
		return Map.of("ok", true, "message", "휴대폰 인증이 완료되었습니다.");
	}

	@ExceptionHandler(BusinessException.class)
	ResponseEntity<Map<String, Object>> fail(BusinessException e) {
		return ResponseEntity.badRequest().body(Map.of("ok", false, "message", e.getMessage()));
	}

	@ExceptionHandler(MissingServletRequestParameterException.class)
	ResponseEntity<Map<String, Object>> missingParam(MissingServletRequestParameterException e) {
		return ResponseEntity.badRequest().body(Map.of("ok", false, "message", "입력값을 확인해 주세요."));
	}
}
