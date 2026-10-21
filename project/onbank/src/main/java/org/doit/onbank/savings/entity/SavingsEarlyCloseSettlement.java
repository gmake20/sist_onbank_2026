package org.doit.onbank.savings.entity;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "savings_early_close_settlement",
       uniqueConstraints = @UniqueConstraint(name = "uk_savings_early_close_account", columnNames = "savings_account_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SavingsEarlyCloseSettlement {
	
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "savings_account_id", nullable = false)
    private SavingsAccount account;
    
    @Column(nullable = false)
    private long principal;
    
    @Column(name = "gross_interest", nullable = false) 
    private long grossInterest;
    
    @Column(nullable = false)
    private long tax;
    
    @Column(nullable = false)
    private long payout;
    
    @Column(name = "annual_rate", nullable = false, precision = 7, scale = 4) 
    private BigDecimal annualRate;
    
    @Column(name = "closed_on", nullable = false)
    private LocalDate closedOn;

    public SavingsEarlyCloseSettlement(SavingsAccount account, long principal,
            long grossInterest, long tax, long payout,
            BigDecimal annualRate, LocalDate closedOn) {
    	
        this.account=account; this.principal=principal;
        this.grossInterest=grossInterest; this.tax=tax;
        this.payout=payout; this.annualRate=annualRate; this.closedOn=closedOn;
    }
}
