
package org.doit.onbank.loan.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LoanApplicationForm {

    @NotNull(message = "상품을 선택하세요.")
    private Long productId;

    @NotNull(message = "신청 금액을 입력하세요.")
    @DecimalMin(value = "10000",
            message = "신청 금액은 1만원 이상이어야 합니다.")
    @Digits(integer = 15, fraction = 0,
            message = "신청 금액은 원 단위 정수로 입력하세요.")
    private BigDecimal requestedAmount;

    @NotNull(message = "대출 기간을 선택하세요.")
    @Min(value = 1, message = "대출 기간은 1개월 이상이어야 합니다.")
    @Max(value = 120, message = "대출 기간은 120개월 이하여야 합니다.")
    private Integer requestedMonths;

    @AssertTrue(message = "필수 약관에 동의해야 합니다.")
    private boolean agreedToTerms;

    @NotNull(message = "대출금 수령 계좌를 선택하세요.")
    private Long disbursementAccountId;
    
    @NotBlank(message = "상환 방식을 선택하세요.")
    @Pattern(
        regexp = "EQUAL|PRINCIPAL|BULLET",
        message = "올바른 상환 방식을 선택하세요."
    )
    private String repaymentMethod;
}
