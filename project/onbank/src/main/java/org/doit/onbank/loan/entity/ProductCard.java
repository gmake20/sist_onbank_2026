package org.doit.onbank.loan.entity;

import java.time.LocalDate;
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
@Table(name = "productcard")
@Getter
@Setter
@NoArgsConstructor
public class ProductCard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "CARD_ID")
    private Long cardId;

    @Column(name = "MEMBER_ID")
    private Long memberId;

    @Column(name = "ACCOUNT_ID")
    private Long accountId;

    @Column(name = "CARD_NUMBER", length = 19)
    private String cardNumber;

    @Column(name = "CARD_TYPE", length = 20)
    private String cardType;

    @Column(name = "CARD_NAME", length = 100)
    private String cardName;

    @Column(name = "CREDIT_LIMIT")
    private Long creditLimit;

    @Column(name = "STATUS", length = 20)
    private String status;

    @Column(name = "ISSUED_AT")
    private LocalDate issuedAt;

    @Column(name = "EXPIRES_AT")
    private LocalDate expiresAt;

    @Column(name = "CREATED_AT")
    private LocalDateTime createdAt;

    @Column(name = "UPDATED_AT")
    private LocalDateTime updatedAt;

}
