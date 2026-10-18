package org.doit.onbank.admin.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.doit.onbank.employee.entity.Employee;
import org.doit.onbank.employee.entity.EmployeeRole;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

// 직원 계정 생성 폼 입력값. 사번, 비밀번호 칸은 없음 (서버가 만든다)
@Getter
@Setter
public class EmployeeCreateForm {

    @NotBlank(message = "이름을 입력하세요")
    @Size(max = 30, message = "이름은 30자까지 입력할 수 있습니다.")
    private String name;

    @NotBlank(message = "이메일을 입력하세요")
    @Email(message = "이메일 형식이 올바르지 않습니다")
    @Size(max = 100, message = "이메일은 100자 이하로 입력하세요")
    private String email;

    @Size(max = 20, message = "전화번호는 20자까지 입력할 수 있습니다")
    private String phone;

    @NotNull(message = "소속 지점을 선택하세요")
    private Long branchId;

    @NotBlank(message = "직급을 선택하세요")
    private String position;

    @NotNull(message = "권한을 선택하세요")
    private EmployeeRole role;

    @NotNull(message = "입사일을 입력하세요")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate hiredAt;

    @NotBlank(message = "생성 사유를 입력하세요")
    @Size(max = 200, message = "생성 사유는 200자 이하로 입력하세요.")
    private String reason;


}
