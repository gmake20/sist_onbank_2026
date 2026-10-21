package org.doit.onbank.admin.service;

import lombok.RequiredArgsConstructor;
import org.doit.onbank.admin.dto.TransactionListRow;
import org.doit.onbank.admin.dto.TransferListRow;
import org.doit.onbank.admin.repository.TransactionSearchRepository;
import org.doit.onbank.admin.repository.TransferSearchRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

// TRX-001 전체 거래 조회 (S-09 전체 거래 탭)
// 기간·금액 검사는 컨트롤러에서 (AdminAccountController.history 와 같음)
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminTransactionService {

    private static final int PAGE_SIZE = 20;

    // 유형 select 와 표의 한글 이름. 키는 팀원2 TransactionType 값, 넣은 순서대로 select 에 나온다
    // WITHDRAWAL 에는 대출 상환·적금 납입도 들어온다 (팀원3 LoanRepaymentService 191줄, OnbankAccountMoneyAdapter 82줄)
    public static final Map<String, String> TYPE_LABELS = typeLabels();

    // TRX-002 이체 유형 이름. 키는 팀원2 TransferType 값
    public static final Map<String, String> TRANSFER_TYPE_LABELS =
            Map.of("IMMEDIATE", "즉시", "SCHEDULED", "예약", "RECURRING", "정기");
    private final TransactionSearchRepository transactionSearchRepository;
    private final TransferSearchRepository transferSearchRepository;

    // 기간은 종료일 하루 전체를 넣으려고 종료일 + 1일 00:00 미만으로 찾는다 (AdminMemberService 와 같음)
    public Page<TransactionListRow> search(LocalDate from, LocalDate to, String type, boolean excludeIn,
                                           String accountNumber, Long min, Long max, int page) {
        return transactionSearchRepository.search(from.atStartOfDay(), to.plusDays(1).atStartOfDay(),
                knownType(type), excludeIn, withoutHyphen(accountNumber), min, max,
                PageRequest.of(Math.max(page, 0), PAGE_SIZE));
    }

    // 하단 합계. 조건은 search 와 같게
    public long sumAmount(LocalDate from, LocalDate to, String type, boolean excludeIn,
                          String accountNumber, Long min, Long max) {
        return transactionSearchRepository.sumAmount(from.atStartOfDay(), to.plusDays(1).atStartOfDay(),
                knownType(type), excludeIn, withoutHyphen(accountNumber), min, max);
    }

    // TRX-002 이체내역. 계좌번호는 출금·입금 계좌 양쪽에서 찾는다
    public Page<TransferListRow> searchTransfers(LocalDate from, LocalDate to,
                                                 String accountNumber, Long min, Long max, int page) {
        return transferSearchRepository.search(from.atStartOfDay(), to.plusDays(1).atStartOfDay(),
                withoutHyphen(accountNumber), min, max, PageRequest.of(Math.max(page, 0), PAGE_SIZE));
    }

    private static Map<String, String> typeLabels() {
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put("DEPOSIT", "입금");
        labels.put("WITHDRAWAL", "출금 (ATM·대출 상환·적금 납입)");
        labels.put("TRANSFER_OUT", "이체 보냄");
        labels.put("TRANSFER_IN", "이체 받음");
        return Collections.unmodifiableMap(labels);
    }
    // 빈 값이나 목록에 없는 값은 null → "전체"
    private String knownType(String type) {
        return TYPE_LABELS.containsKey(type) ? type : null;
    }

    // 1002-482 / 1002482 둘 다 찾도록 하이픈만 뺀다 (AdminAccountService 와 같음)
    private String withoutHyphen(String value) {
        return (value == null || value.isBlank()) ? null : value.trim().replace("-", "");
    }
}
