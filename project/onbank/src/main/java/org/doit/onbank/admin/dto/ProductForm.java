package org.doit.onbank.admin.dto;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

// S-11 예적금 상품 등록·수정 폼 입력값 (등록·수정 공용). 칸은 product 테이블 기준 (명세서 2.8과 다름)
@Getter
@Setter
public class ProductForm {
    @NotBlank(message = "상품명을 입력하세요.")
    @Size(max = 100, message = "상품명은 100자까지 입력할 수 있습니다.")
    private String productName;

    // 저장값은 계좌 유형(AccountType)과 같게 : DEPOSIT 예금 / SAVINGS 적금 (팀원2 제안)
    @NotBlank(message = "유형을 선택하세요.")
    @Pattern(regexp = "DEPOSIT|SAVINGS", message = "유형은 예금 또는 적금입니다.")
    private String productType;

    // DECIMAL(7,4) : 정수 3자리, 소수 4자리
    @NotNull(message = "기본금리를 입력하세요.")
    @DecimalMin(value = "0.0", inclusive = false, message = "기본금리는 0보다 커야 합니다.")
    @Digits(integer = 3, fraction = 4, message = "금리는 소수점 4자리까지 입력할 수 있습니다.")
    private BigDecimal baseInterestRate;

    // 기본금리 이하인지는 두 칸을 같이 봐야 해서 서비스에서 검사한다
    @NotNull(message = "중도해지 이율을 입력하세요.")
    @DecimalMin(value = "0.0", message = "중도해지 이율은 0 이상이어야 합니다.")
    @Digits(integer = 3, fraction = 4, message = "금리는 소수점 4자리까지 입력할 수 있습니다.")
    private BigDecimal earlyTerminationRate;

    @NotNull(message = "가입기간을 입력하세요.")
    @Min(value = 1, message = "가입기간은 1개월 이상입니다.")
    @Max(value = 600, message = "가입기간은 600개월까지입니다.")
    private Integer termMonths;

    @NotNull(message = "최소가입금액을 입력하세요.")
    @Min(value = 1, message = "최소가입금액은 1원 이상입니다.")
    private Long minimumAmount;

    @Size(max = 500, message = "우대조건은 500자까지 입력할 수 있습니다.")
    private String preferentialCondition;

    // 저장값 ON_SALE 판매중 / STOPPED 판매중지 (제안값, 팀원3 확인 필요). 처음 열면 판매중
    @NotBlank(message = "판매 상태를 선택하세요.")
    @Pattern(regexp = "ON_SALE|STOPPED", message = "판매 상태를 다시 선택하세요.")
    private String status = "ON_SALE";
}
