package org.doit.onbank.savings.service;


public interface SavingsMoneyPort {
    
	void debit(Long sourceAccountId, Long memberId, long amount);
    
	void credit(Long destinationAccountId, Long memberId, long amount);
	
	default void credit(
            Long id,
            Long memberId,
            long amount,
            String description) {

        credit(id, memberId, amount);
    }
}
