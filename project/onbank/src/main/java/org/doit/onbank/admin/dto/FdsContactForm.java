package org.doit.onbank.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.doit.onbank.admin.entity.ContactMethod;
import org.doit.onbank.admin.entity.ContactResult;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

// S-16 고객 연락 결과 입력값 (FDS-006). 통화 내용은 fds_handling_history.memo 에 (메모 칸 따로 없음, 2026-10-22)
@Getter
@Setter
public class FdsContactForm {

    // <input type="datetime-local"> 은 초 없이 "2026-10-22T13:50" 으로 보낸다
    @NotNull(message = "연락 일시를 입력하세요.")
    @PastOrPresent(message = "연락 일시는 지금보다 늦을 수 없습니다.")
    @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime contactAt;

    @NotNull(message = "연락 방법을 고르세요.")
    private ContactMethod contactMethod;

    @NotNull(message = "판단 결과를 고르세요.")
    private ContactResult contactResult;

    @NotBlank(message = "통화 내용을 입력하세요.")
    @Size(max = 500, message = "통화 내용은 500자까지 입력할 수 있습니다.")
    private String memo;
}
