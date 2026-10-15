package org.doit.onbank.loan.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "loan_product")
@Getter
@Setter
@NoArgsConstructor
public class LoanProduct {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "LOAN_PRODUCT_ID")
    private Long id;
    
    @Column(name = "CATEGORY", nullable = false, length = 30)
    private String category;

    @Column(name = "PRODUCT_NAME", nullable = false, length = 100)
    private String name;

    @Column(name = "SUBTITLE", length = 255)
    private String subtitle;

    @Column(name = "TARGET_CUSTOMER", length = 100)
    private String target;

    @Column(name = "LIMIT_DESCRIPTION", length = 100)
    private String limit;

    @Column(name = "REPAYMENT_METHOD", length = 100)
    private String method;

    @Column(name = "TAG", length = 50)
    private String tag;

    @Column(name = "ANNUAL_INTEREST_RATE", precision = 7, scale = 4)
    private BigDecimal annualInterestRate;

    @Column(name = "MAX_TERM_MONTHS")
    private Integer maxTermMonths;

    @Column(name = "CREATED_AT", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}