
package org.doit.onbank.account.repository;

import java.util.List;

import org.doit.onbank.account.entity.AccountTransaction;
import org.doit.onbank.account.entity.AccountTransactionType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountTransactionRepository
        extends JpaRepository<AccountTransaction, Long> {

    List<AccountTransaction>
        findByAccount_AccountIdOrderByTransactedAtDesc(
            Long accountId
        );

    boolean existsByLoanAccountIdAndTransactionType(
            Long loanAccountId,
            AccountTransactionType transactionType
    );
}
