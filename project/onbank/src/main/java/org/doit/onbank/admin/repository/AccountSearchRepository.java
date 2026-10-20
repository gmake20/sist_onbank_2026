package org.doit.onbank.admin.repository;

import org.doit.onbank.account.entity.Account;
import org.doit.onbank.account.entity.AccountStatus;
import org.doit.onbank.account.entity.AccountType;
import org.doit.onbank.admin.dto.AccountListRow;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

// ACC-001 계좌 목록 (S-07)
// Account 에는 회원 연관관계가 없고 memberId 숫자만 있어서 ON 으로 직접 잇는다
public interface AccountSearchRepository extends Repository<Account, Long> {

    // 계좌번호는 하이픈을 뺀 값끼리 비교 (검색어도 서비스에서 하이픈을 뺀다)
    String FROM_WHERE = """
              FROM Account a JOIN Member m ON m.memberId = a.memberId
              WHERE (:accountNumber IS NULL OR replace(a.accountNumber, '-', '') LIKE concat('%', :accountNumber, '%'))
              AND (:memberLoginId IS NULL OR m.loginId LIKE concat('%', :memberLoginId, '%'))
              AND (:status IS NULL OR a.accountStatus = :status)
              AND (:type IS NULL OR a.accountType = :type)
              """;

    // 개설일 칸이 없어서(createdAt 없음) 계좌 번호(account_id) 최신순
    @Query(value = "SELECT new org.doit.onbank.admin.dto.AccountListRow(a.accountId, a.accountNumber,"
            + " m.loginId, m.name, a.accountType, a.accountStatus, a.balance, a.lastTransactionAt) "
            + FROM_WHERE + " ORDER BY a.accountId DESC",
            countQuery = "SELECT count(a) " + FROM_WHERE)
    Page<AccountListRow> search(@Param("accountNumber") String accountNumber,
                                @Param("memberLoginId") String memberLoginId,
                                @Param("status") AccountStatus status,
                                @Param("type") AccountType type,
                                Pageable pageable);
}
