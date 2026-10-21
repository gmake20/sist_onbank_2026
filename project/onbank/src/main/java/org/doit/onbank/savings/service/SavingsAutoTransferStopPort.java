package org.doit.onbank.savings.service;

public interface SavingsAutoTransferStopPort {
	
    void stopBySavingsAccountId(Long accountId);
}
