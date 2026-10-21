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

    // ---- 관리자 상품 관리용 (팀원4 추가, 2026-10-21) ----
    // 유형·가입기간은 바꾸지 않는다 : 계좌가 termMonths 를 계속 읽어서(SavingsService 납입 회차) 기존 가입자에게 영향
    // 검사는 생성자와 같은 규칙
    public void changeTerms(String name, BigDecimal annualRate, long minAmount, long maxAmount) {
        if (name == null
                || name.isBlank()
                || annualRate == null
                || annualRate.signum() < 0
                || annualRate.compareTo(new BigDecimal("100")) > 0
                || minAmount < 1
                || maxAmount < minAmount) {

            throw new IllegalArgumentException(
                "상품 정보가 올바르지 않습니다."
            );
        }

        this.name = name;
        this.annualRate = annualRate;
        this.minAmount = minAmount;
        this.maxAmount = maxAmount;
    }

    // 판매중지 = active false → 고객 상품 목록·상세·가입에서 빠진다. 이미 가입한 계좌는 그대로
    public void stopSale() {
        this.active = false;
    }

    public void resumeSale() {
        this.active = true;
    }
}