package org.doit.onbank.admin.dto;

import org.doit.onbank.admin.entity.AdminLoginLog;
import org.doit.onbank.common.security.LoginFailReason;

// 로그인 이력 목록 한 줄. admin_login_log에는 관리자가 번호로만 있어서 이름을 붙여 넘긴다
// adminName = null 이면 없는 아이디로 시도했거나 계정을 찾지 못한 경우
public record AdminLoginLogRow(AdminLoginLog log, String adminName) {

    // 실패 사유 화면 표시 (성공이면 null)
    public String failReasonLabel() {
        return LoginFailReason.labelOf(log.getFailReason());
    }
}
