package org.doit.onbank.account.repository;

import org.doit.onbank.account.entity.AccountTransfer;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountTransferRepository extends JpaRepository<AccountTransfer, Long> {
}
