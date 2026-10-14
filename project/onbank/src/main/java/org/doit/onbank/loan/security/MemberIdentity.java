package org.doit.onbank.loan.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** Replace numeric principal-name parsing if your authentication principal stores memberId elsewhere. */
@Component
public class MemberIdentity {
    public long requireMemberId() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null || !a.isAuthenticated() || "anonymousUser".equals(a.getPrincipal()))
            throw new org.springframework.security.access.AccessDeniedException("로그인이 필요합니다.");
        try { return Long.parseLong(a.getName()); }
        catch (NumberFormatException ex) {
            throw new IllegalStateException("MemberIdentity를 프로젝트 로그인 principal 구조에 맞게 구현하세요.");
        }
    }
}
