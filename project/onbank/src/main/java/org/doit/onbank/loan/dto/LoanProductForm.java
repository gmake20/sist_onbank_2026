
package org.doit.onbank.loan.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LoanProductForm {

    @NotBlank(message = "대출 카테고리를 선택하세요.")
    @Pattern(
        regexp = "credit|mortgage|jeonse|auto|group|housing|business",
        message = "유효하지 않은 대출 카테고리입니다."
    )
    private String category;

    @NotBlank(message = "상품명을 입력하세요.")
    @Size(max = 100)
    private String name;

    @NotBlank(message = "상품 설명을 입력하세요.")
    @Size(max = 255)
    private String subtitle;

    @NotBlank(message = "대출 대상을 입력하세요.")
    private String target;

    private String tag;

    @NotBlank(message = "대출 한도를 입력하세요.")
    private String limit;

    @NotBlank(message = "상환 방법을 선택하세요.")
    private String method;

    @NotNull(message = "연 이자율을 입력하세요.")
    @DecimalMin(value = "0.0")
    @DecimalMax(value = "100.0")
    private BigDecimal annualInterestRate;

    @NotNull(message = "최대 대출 기간을 입력하세요.")
    @Min(1)
    @Max(600)
    private Integer maxTermMonths;
}
