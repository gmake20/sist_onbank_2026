package org.doit.onbank.savings.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(
    name = "savings_payment",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_savings_installment",
        columnNames = {
            "savings_account_id",
            "installment_no"
        }
    )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SavingsPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "payment_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "savings_account_id")
    private SavingsAccount account;

    @Column(name = "installment_no", nullable = false)
    private int installmentNo;

    @Column(name = "amount", nullable = false)
    private long amount;

    @Column(name = "paid_on", nullable = false)
    private LocalDate paidOn;

    public SavingsPayment(
            SavingsAccount account,
            int installmentNo,
            long amount,
            LocalDate paidOn) {

        this.account = account;
        this.installmentNo = installmentNo;
        this.amount = amount;
        this.paidOn = paidOn;
    }
}