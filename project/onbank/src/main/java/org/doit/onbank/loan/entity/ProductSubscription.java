package org.doit.onbank.loan.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "product_subscription")
@Getter
@Setter
@NoArgsConstructor
public class ProductSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "SUBSCRIPTION_ID")
    private Long subscriptionId;

    @Column(name = "MEMBER_ID")
    private Long memberId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "PRODUCT_ID")
    private Product product;

    @Column(name = "ACCOUNT_ID")
    private Long accountId;

    @Column(name = "PRINCIPAL_AMOUNT")
    private Long principalAmount;

    @Column(name = "MONTHLY_PAYMENT")
    private Long monthlyPayment;

    @Column(name = "SUBSCRIBED_AT")
    private LocalDate subscribedAt;

    @Column(name = "MATURITY_DATE")
    private LocalDate maturityDate;

    @Column(name = "EXPECTED_INTEREST")
    private Long expectedInterest;

    @Column(name = "STATUS", length = 20)
    private String status;

    @Column(name = "CANCELLED_AT")
    private LocalDateTime cancelledAt;

    @Column(name = "CREATED_AT")
    private LocalDateTime createdAt;

    @Column(name = "UPDATED_AT")
    private LocalDateTime updatedAt;

}
