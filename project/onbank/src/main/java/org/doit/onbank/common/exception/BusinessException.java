package org.doit.onbank.common.exception;

// 사용자에게 그대로 보여 줘도 되는 업무 규칙 위반 (아이디 중복, 한도 초과 등)
// RuntimeException이므로 @Transactional 안에서 던지면 롤백된다
public class BusinessException extends RuntimeException {

	public BusinessException(String message) {
		super(message);
	}
}
