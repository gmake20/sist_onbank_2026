package org.doit.onbank.savings.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
    name = "savings_cash_flow",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_savings_flow_key",
        columnNames = "request_key"
    )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SavingsCashFlow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "flow_id")
    private Long id;

    @Column(name = "savings_account_id", nullable = false)
    private Long savingsAccountId;

    @Column(name = "request_key", nullable = false, length = 120)
    private String requestKey;

    @Column(name = "kind", nullable = false, length = 24)
    private String kind;

    @Column(name = "amount", nullable = false)
    private long amount;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public SavingsCashFlow(
            Long id,
            String requestKey,
            String kind,
            long amount) {

        this.savingsAccountId = id;
        this.requestKey = requestKey;
        this.kind = kind;
        this.amount = amount;
        this.createdAt = LocalDateTime.now();
    }
}