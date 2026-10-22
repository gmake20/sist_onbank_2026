package org.doit.onbank.savings.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.*;

@Entity
@Table(
    name = "savings_account",
    indexes = @Index(
        name = "ix_savings_owner",
        columnList = "member_id"
    )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SavingsAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "savings_account_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id")
    private SavingsProduct product;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "source_account_id", nullable = false)
    private Long sourceAccountId;

    @Column(name = "principal", nullable = false)
    private long principal;

    @Column(name = "monthly_amount", nullable = false)
    private long monthlyAmount;

    @Column(
        name = "contract_rate",
        nullable = false,
        precision = 7,
        scale = 4
    )
    private BigDecimal contractRate;
    
    @Column(
    	    name = "account_number",
    	    nullable = false,
    	    unique = true,
    	    length = 30
    	)
    private String accountNumber;
    
    @Column(name = "last_transaction_at")
    private LocalDateTime lastTransactionAt;

    @Column(name = "opened_on", nullable = false)
    private LocalDate openedOn;

    @Column(name = "maturity_on", nullable = false)
    private LocalDate maturityOn;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private SavingsStatus status;

    @Column(name = "closed_on")
    private LocalDate closedOn;

    @Version
    @Column(name = "version")
    private Long version;

    public SavingsAccount(
            SavingsProduct product,
            Long memberId,
            Long sourceAccountId,
            long amount,
            String accountNumber,
            LocalDate date) {

        if (product == null
                || memberId == null
                || sourceAccountId == null
                || amount <= 0
                || date == null) {

            throw new IllegalArgumentException(
                "가입 정보가 올바르지 않습니다."
            );
        }

        this.product = product;
        this.memberId = memberId;
        this.sourceAccountId = sourceAccountId;
        this.principal = amount;

        this.monthlyAmount =
                product.getType() == SavingsType.INSTALLMENT_SAVINGS
                        ? amount
                        : 0;
        
        this.accountNumber = accountNumber;

        this.contractRate = product.getAnnualRate();
        this.openedOn = date;
        this.maturityOn = date.plusMonths(product.getTermMonths());
        this.status = SavingsStatus.ACTIVE;
        
        this.lastTransactionAt = date.atStartOfDay();
    }

    public void addPayment(long amount,
    						LocalDateTime now) {

        if (status != SavingsStatus.ACTIVE
                || product.getType() != SavingsType.INSTALLMENT_SAVINGS
                || amount != monthlyAmount) {

            throw new IllegalStateException(
                "적금 납입이 불가능합니다."
            );
        }

        principal = Math.addExact(principal, amount);
        lastTransactionAt = now;
    }

    public void close(LocalDate date) {

        if (status != SavingsStatus.ACTIVE
                || date.isBefore(maturityOn)) {

            throw new IllegalStateException(
                "만기 이후에만 해지할 수 있습니다."
            );
        }

        status = SavingsStatus.CLOSED;
        closedOn = date;
        lastTransactionAt = LocalDateTime.now();
    }
    
    public void closeEarly(LocalDate date) {
        if (status != SavingsStatus.ACTIVE
                || date == null
                || date.isBefore(openedOn)
                || !date.isBefore(maturityOn)) {
            throw new IllegalStateException("중도해지가 불가능합니다.");
        }

        status = SavingsStatus.CLOSED;
        closedOn = date;
        lastTransactionAt = LocalDateTime.now();
    }
}