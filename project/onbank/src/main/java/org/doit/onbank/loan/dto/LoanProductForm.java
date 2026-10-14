package org.doit.onbank.loan.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LoanProductForm {

    @NotBlank(message = "상품명을 입력하세요.")
    private String name;

    @NotBlank(message = "상품 설명을 입력하세요.")
    private String subtitle;

    @NotBlank(message = "대출 대상을 입력하세요.")
    private String target;

    @NotBlank(message = "대출 한도를 입력하세요.")
    private String limit;

    @NotBlank(message = "상환 방식을 선택하세요.")
    private String method;

    private String tag;

    @NotNull(message = "연 이자율을 입력하세요.")
    @DecimalMin("0.0")
    @DecimalMax("100.0")
    @Digits(integer = 3, fraction = 4)
    private BigDecimal annualInterestRate;

    @NotNull(message = "대출 기간을 입력하세요.")
    @Min(1)
    @Max(600)
    private Integer maxTermMonths;
}