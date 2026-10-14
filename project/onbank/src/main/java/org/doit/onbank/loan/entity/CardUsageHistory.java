package org.doit.onbank.loan.entity;

import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "card_usage_history")
@Getter
@Setter
@NoArgsConstructor
public class CardUsageHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "CARD_USAGE_ID")
    private Long cardUsageId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "CARD_ID")
    private ProductCard card;

    @Column(name = "MERCHANT_NAME", length = 100)
    private String merchantName;

    @Column(name = "MERCHANT_CATEGORY", length = 50)
    private String merchantCategory;

    @Column(name = "AMOUNT")
    private Long amount;

    @Column(name = "USED_AT")
    private LocalDateTime usedAt;

    @Column(name = "APPROVAL_NUMBER", length = 30)
    private String approvalNumber;

    @Column(name = "STATUS", length = 20)
    private String status;

    @Column(name = "CREATED_AT")
    private LocalDateTime createdAt;
}
