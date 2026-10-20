package org.doit.onbank.savings.dto;

import java.math.BigDecimal;
import org.doit.onbank.savings.entity.SavingsType;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SavingsProductCreateForm {
    private String name;
    private SavingsType type;
    private BigDecimal annualRate;
    private Integer termMonths;
    private Long minAmount;
    private Long maxAmount;
}
