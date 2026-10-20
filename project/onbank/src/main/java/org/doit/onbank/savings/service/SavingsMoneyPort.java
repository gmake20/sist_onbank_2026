package org.doit.onbank.savings.service;


public interface SavingsMoneyPort {
    
	void debit(Long sourceAccountId, Long memberId, long amount);
    
	void credit(Long destinationAccountId, Long memberId, long amount);
}
