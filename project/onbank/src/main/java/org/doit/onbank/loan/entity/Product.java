package org.doit.onbank.loan.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "product")
@Getter
@Setter
@NoArgsConstructor
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "PRODUCT_ID")
    private Long productId;

    @Column(name = "PRODUCT_NAME", length = 100)
    private String productName;

    @Column(name = "PRODUCT_TYPE", length = 20)
    private String productType;

    @Column(name = "BASE_INTEREST_RATE", precision = 7, scale = 4)
    private BigDecimal baseInterestRate;

    @Column(name = "PREFERENTIAL_CONDITION", length = 500)
    private String preferentialCondition;

    @Column(name = "TERM_MONTHS")
    private Short termMonths;

    @Column(name = "MINIMUM_AMOUNT")
    private Long minimumAmount;

    @Column(name = "EARLY_TERMINATION_RATE", precision = 7, scale = 4)
    private BigDecimal earlyTerminationRate;

    @Column(name = "STATUS", length = 20)
    private String status;

    @Column(name = "CREATED_AT")
    private LocalDateTime createdAt;

    @Column(name = "UPDATED_AT")
    private LocalDateTime updatedAt;

}
