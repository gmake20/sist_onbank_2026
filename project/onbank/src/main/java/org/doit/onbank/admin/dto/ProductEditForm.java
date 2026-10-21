package org.doit.onbank.admin.dto;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.doit.onbank.savings.entity.SavingsProduct;

import java.math.BigDecimal;

// S-11 상품 수정 폼. 바꿀 수 있는 칸만 (유형·가입기간은 화면에 보기만)
// 규칙은 ProductForm 과 같다
@Getter
@Setter
public class ProductEditForm {
    @NotBlank(message = "상품명을 입력하세요.")
    @Size(max = 120, message = "상품명은 120자까지 입력할 수 있습니다.")
    private String productName;

    @NotNull(message = "연이율을 입력하세요.")
    @DecimalMin(value = "0.0", message = "연이율은 0 이상이어야 합니다.")
    @DecimalMax(value = "100.0", message = "연이율은 100 이하여야 합니다.")
    @Digits(integer = 3, fraction = 4, message = "연이율은 소수점 4자리까지 입력할 수 있습니다.")
    private BigDecimal annualRate;

    @NotNull(message = "최소 금액을 입력하세요.")
    @Min(value = 1, message = "최소 금액은 1원 이상입니다.")
    private Long minAmount;

    // 최소 금액 이상인지는 두 칸을 같이 봐야 해서 서비스에서 검사한다
    @NotNull(message = "최대 금액을 입력하세요.")
    @Min(value = 1, message = "최대 금액은 1원 이상입니다.")
    private Long maxAmount;

    // 수정 화면을 처음 열 때 지금 값으로 채운다
    public static ProductEditForm from(SavingsProduct product) {
        ProductEditForm form = new ProductEditForm();
        form.setProductName(product.getName());
        form.setAnnualRate(product.getAnnualRate());
        form.setMinAmount(product.getMinAmount());
        form.setMaxAmount(product.getMaxAmount());
        return form;
    }
}
