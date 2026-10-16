
package org.doit.onbank.loan.security;

import org.doit.onbank.employee.security.EmployeePrincipal;
import org.doit.onbank.common.security.ActorType;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class LoanReviewerIdentity {

    public Long requireEmployeeId(Authentication authentication) {

        // 1. 로그인 여부 확인
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {

            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "직원 로그인이 필요합니다."
            );
        }

        // 2. 실제 직원 Principal 확인
        if (!(authentication.getPrincipal()
                instanceof EmployeePrincipal employee)) {

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "은행 직원 계정으로 로그인해야 합니다."
            );
        }

        // 3. 사용자 종류 확인
        if (employee.getActorType() != ActorType.EMPLOYEE) {

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "직원 인증 정보가 올바르지 않습니다."
            );
        }

        // 4. 계정 상태 확인
        if (!employee.isEnabled()
                || !employee.isAccountNonLocked()) {

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "사용할 수 없는 직원 계정입니다."
            );
        }

        // 5. 직원 ID 확인
        Long employeeId = employee.getEmployeeId();

        if (employeeId == null || employeeId <= 0) {

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "직원 식별자가 올바르지 않습니다."
            );
        }

        return employeeId;
    }
}
