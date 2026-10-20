package org.doit.onbank.savings.entity;

import java.time.LocalDate;
import java.time.YearMonth;

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
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "savings_auto_transfer", uniqueConstraints = @UniqueConstraint(name = "uk_savings_auto_account", columnNames = "savings_account_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SavingsAutoTransfer {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "savings_account_id", nullable = false)
    private SavingsAccount account;
    
    @Column(name = "transfer_day", nullable = false)
    private int transferDay;
    
    @Column(name = "enabled", nullable = false)
    private boolean enabled;
    
    @Column(name = "last_success_month", length = 7)
    private String lastSuccessMonth;
    
    @Column(name = "last_success_on")
    private LocalDate lastSuccessOn;
    
    @Column(name = "last_error", length = 500)
    private String lastError;
    
    @Version @Column(name = "version")
    private Long version;

    public SavingsAutoTransfer(SavingsAccount account, int transferDay) {
        this.account = account;
        configure(transferDay);
    }
    
    public void configure(int day) {
        if (day < 1 || day > 28) throw new IllegalArgumentException("자동납입일은 1~28일 중 선택하세요.");
        this.transferDay = day;
        this.enabled = true;
        this.lastError = null;
    }
    
    public void disable() { this.enabled = false; this.lastError = null; }
    
    public void markSuccess(LocalDate date) {
        this.lastSuccessMonth = YearMonth.from(date).toString();
        this.lastSuccessOn = date;
        this.lastError = null;
    }
    
    public void markError(String error) {
        this.lastError = error == null ? "자동납입 실패" : error.substring(0, Math.min(error.length(), 500));
    }
}
