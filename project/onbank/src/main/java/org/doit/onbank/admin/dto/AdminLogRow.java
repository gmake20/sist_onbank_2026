package org.doit.onbank.admin.dto;

import org.doit.onbank.admin.entity.AdminLog;

// 활동 로그 목록 한 줄. admin_log에는 작업자·대상이 번호로만 있어서 아이디·이름을 붙여 넘긴다
// actor = 작업한 사람, target = 대상이 ADMIN / EMPLOYEE 일 때 그 사람 (계좌 등 다른 대상이면 null)
public record AdminLogRow(AdminLog log, Person actor, Person target) {

    // 아이디(관리자 login_id / 직원 사번)와 이름
    public record Person(String loginId, String name) {
    }
}
