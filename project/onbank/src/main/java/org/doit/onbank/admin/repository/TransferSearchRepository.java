package org.doit.onbank.admin.repository;

import org.doit.onbank.account.entity.AccountTransfer;
import org.doit.onbank.admin.dto.TransferListRow;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

// TRX-002 이체내역 (S-09 이체내역 탭). 조회만, 일반 SQL (TransactionSearchRepository 와 같은 방식)
// 출금 계좌는 account_id 로, 입금 계좌는 계좌번호로 잇는다 (팀원2가 to.getAccountNumber() 를 그대로 저장, AccountTransferService 180줄)
// 입금 쪽은 LEFT JOIN : 타행 이체(financial_institution_id 있음)는 우리 계좌가 아니라서 이름이 없다
public interface TransferSearchRepository extends Repository<AccountTransfer, Long> {

    // 계좌번호는 출금 계좌·입금 계좌 양쪽에서 찾는다
    // 기간은 from 이상 toExclusive 미만, 계좌번호는 하이픈을 뺀 값끼리 비교
    String FROM_WHERE = """
             FROM account_transfer tr
             JOIN account wa ON wa.account_id = tr.account_id
             JOIN members wm ON wm.member_id = wa.member_id
             LEFT JOIN account da ON da.account_number = tr.deposit_account_number
             LEFT JOIN members dm ON dm.member_id = da.member_id
             WHERE tr.transfer_at >= :from AND tr.transfer_at < :toExclusive
             AND (:accountNumber IS NULL
                  OR REPLACE(tr.withdrawal_account_number, '-', '') LIKE CONCAT('%', :accountNumber, '%')
                  OR REPLACE(tr.deposit_account_number, '-', '') LIKE CONCAT('%', :accountNumber, '%'))
             AND (:minAmount IS NULL OR tr.transfer_amount >= :minAmount)
             AND (:maxAmount IS NULL OR tr.transfer_amount <= :maxAmount)
             """;

    @Query(value = """
             SELECT tr.account_transfer_id AS transferId,
                    tr.transfer_at AS transferAt,
                    tr.transfer_type AS transferType,
                    tr.withdrawal_account_number AS withdrawalAccountNumber,
                    wm.name AS withdrawalMemberName,
                    wm.login_id AS withdrawalMemberLoginId,
                    tr.deposit_account_number AS depositAccountNumber,
                    dm.name AS depositMemberName,
                    dm.login_id AS depositMemberLoginId,
                    tr.transfer_amount AS amount
             """ + FROM_WHERE + " ORDER BY tr.transfer_at DESC, tr.account_transfer_id DESC",
            countQuery = "SELECT COUNT(*) " + FROM_WHERE,
            nativeQuery = true)
    Page<TransferListRow> search(@Param("from") LocalDateTime from,
                                 @Param("toExclusive") LocalDateTime toExclusive,
                                 @Param("accountNumber") String accountNumber,
                                 @Param("minAmount") Long minAmount,
                                 @Param("maxAmount") Long maxAmount,
                                 Pageable pageable);
}
