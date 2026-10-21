package org.doit.onbank.savings.service;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;

@Component
public class JpaSavingsAutoTransferStopAdapter implements SavingsAutoTransferStopPort {
    
	@PersistenceContext private EntityManager em;
	
    @Override
    public void stopBySavingsAccountId(Long accountId) {
    	
        em.createQuery("update SavingsAutoTransfer s set s.enabled = false " +
                "where s.account.id = :accountId")
          .setParameter("accountId", accountId).executeUpdate();
    }
}
