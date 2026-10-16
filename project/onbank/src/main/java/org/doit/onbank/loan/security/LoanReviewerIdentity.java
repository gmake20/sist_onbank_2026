package org.doit.onbank.loan.security;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class LoanReviewerIdentity {

    /** 클라이언트 파라미터가 아니라 인증된 직원 Principal에서만 식별자를 취득합니다. */
    public Long requireEmployeeId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof LoanReviewerPrincipal staff)
                || staff.getEmployeeId() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "직원 인증이 연결되지 않아 심사를 처리할 수 없습니다.");
        }
        return staff.getEmployeeId();
    }
}
