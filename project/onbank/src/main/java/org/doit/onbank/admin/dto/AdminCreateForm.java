package org.doit.onbank.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.doit.onbank.admin.entity.AdminRole;

// S-20 관리자 계정 생성 폼 입력값. 비밀번호 칸은 없다 (서버가 임시 비밀번호를 만든다)
@Getter
@Setter
public class AdminCreateForm {

    @NotBlank(message = "아이디를 입력하세요")
    @Pattern(regexp = "[a-z0-9]{4,20}", message = "아이디는 영문 소문자, 숫자 4~20자입니다.")
    private String loginId;

    @NotBlank(message = "이름을 입력하세요")
    @Size(max = 50, message = "이름은 50자까지 입력할 수 있습니다.")
    private  String name;

    @NotNull(message = "권한을 선택하세요.")
    private AdminRole role;

    @NotBlank(message = "생성 사유를 입력하세요")
    @Size(max = 200, message = "생성 사유는 200자까지 입력할 수 있습니다.")
    private String reason;

}
