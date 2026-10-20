package org.doit.onbank.savings.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "savings_product")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SavingsProduct {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "product_id")
    private Long id;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 30)
    private SavingsType type;

    @Column(
        name = "annual_rate",
        nullable = false,
        precision = 7,
        scale = 4
    )
    private BigDecimal annualRate;

    @Column(name = "term_months", nullable = false)
    private int termMonths;

    @Column(name = "min_amount", nullable = false)
    private long minAmount;

    @Column(name = "max_amount", nullable = false)
    private long maxAmount;

    @Column(name = "active", nullable = false)
    private boolean active;

    public SavingsProduct(
            String name,
            SavingsType type,
            BigDecimal annualRate,
            int termMonths,
            long minAmount,
            long maxAmount) {

        if (name == null
                || name.isBlank()
                || type == null
                || annualRate == null
                || annualRate.signum() < 0
                || annualRate.compareTo(new BigDecimal("100")) > 0
                || termMonths < 1
                || termMonths > 120
                || minAmount < 1
                || maxAmount < minAmount) {

            throw new IllegalArgumentException(
                "상품 정보가 올바르지 않습니다."
            );
        }

        this.name = name;
        this.type = type;
        this.annualRate = annualRate;
        this.termMonths = termMonths;
        this.minAmount = minAmount;
        this.maxAmount = maxAmount;
        this.active = true;
    }
}