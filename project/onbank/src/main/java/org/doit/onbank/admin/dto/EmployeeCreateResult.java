package org.doit.onbank.admin.dto;

import org.doit.onbank.employee.entity.EmployeeRole;

// 임시 비밀번호 안내에 보여줄 값. 리다이렉트 후 한 번만 전달하고 어디에도 저장하지 않는다.
public record EmployeeCreateResult(String employeeNo, String name, String branchName,
                                   EmployeeRole role, String tempPassword) {
}
