package org.doit.onbank.admin.dto;

import org.doit.onbank.admin.entity.AdminRole;

// M-03 임시 비밀번호 안내에 보여 줄 값. 리다이렉트 후 flash로 한 번만 전달하고 어디에도 저장하지 않는다
public record AdminCreateResult(String loginId, String name, AdminRole role, String tempPassword) {

}
