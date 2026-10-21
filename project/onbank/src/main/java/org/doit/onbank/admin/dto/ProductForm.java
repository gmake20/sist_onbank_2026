package org.doit.onbank.admin.dto;
import jakarta.validation.constraints.DecimalMax;
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

// S-11 예적금 상품 등록 폼 입력값. 칸은 팀원3 savings_product 기준
// 규칙은 SavingsProduct 생성자 검사와 같게 맞춘다 → 생성자의 뭉뚱그린 오류 대신 칸별 안내가 먼저 나온다
@Getter
@Setter
public class ProductForm {
    @NotBlank(message = "상품명을 입력하세요.")
    @Size(max = 120, message = "상품명은 120자까지 입력할 수 있습니다.")
    private String productName;

    // 저장값은 팀원3 SavingsType 이름 그대로 : FIXED_DEPOSIT 정기예금 / INSTALLMENT_SAVINGS 정기적금
    @NotBlank(message = "유형을 선택하세요.")
    @Pattern(regexp = "FIXED_DEPOSIT|INSTALLMENT_SAVINGS", message = "유형은 정기예금 또는 정기적금입니다.")
    private String productType;

    // DECIMAL(7,4) : 정수 3자리, 소수 4자리. 0% 상품도 허용 (엔티티도 0 이상)
    @NotNull(message = "연이율을 입력하세요.")
    @DecimalMin(value = "0.0", message = "연이율은 0 이상이어야 합니다.")
    @DecimalMax(value = "100.0", message = "연이율은 100 이하여야 합니다.")
    @Digits(integer = 3, fraction = 4, message = "연이율은 소수점 4자리까지 입력할 수 있습니다.")
    private BigDecimal annualRate;

    @NotNull(message = "가입기간을 입력하세요.")
    @Min(value = 1, message = "가입기간은 1개월 이상입니다.")
    @Max(value = 120, message = "가입기간은 120개월까지입니다.")
    private Integer termMonths;

    // 정기예금 = 처음 맡기는 금액, 정기적금 = 매달 넣는 금액의 범위
    @NotNull(message = "최소 금액을 입력하세요.")
    @Min(value = 1, message = "최소 금액은 1원 이상입니다.")
    private Long minAmount;

    // 최소 금액 이상인지는 두 칸을 같이 봐야 해서 서비스에서 검사한다
    @NotNull(message = "최대 금액을 입력하세요.")
    @Min(value = 1, message = "최대 금액은 1원 이상입니다.")
    private Long maxAmount;
}
