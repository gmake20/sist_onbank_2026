package org.doit.onbank.admin.service;

import lombok.RequiredArgsConstructor;
import org.doit.onbank.admin.repository.LoanSearchRepository;
import org.doit.onbank.loan.entity.LoanApplication;
import org.doit.onbank.loan.entity.LoanApplicationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.EnumMap;
import java.util.Map;

// LON-001 대출 목록 조회. 심사(접수·승인·거절)는 직원 화면 /employee/loans/review 에서 한다 (D-04)
@Service
@RequiredArgsConstructor
public class AdminLoanService {

    private static final int PAGE_SIZE = 20;

    public static final Map<LoanApplicationStatus, String> STATUS_LABELS = new EnumMap<>(Map.of(
            LoanApplicationStatus.PENDING, "심사대기",
            LoanApplicationStatus.REVIEWING, "심사중",
            LoanApplicationStatus.ADDITIONAL_INFO, "서류보완 요청",
            LoanApplicationStatus.APPROVED, "승인완료",
            LoanApplicationStatus.REJECTED, "심사거절",
            LoanApplicationStatus.CANCELLED, "신청취소",
            LoanApplicationStatus.EXPIRED, "승인기간 만료"));

    private final LoanSearchRepository loanSearchRepository;

    // 기간은 from 00:00 이상 ~ to 다음 날 00:00 미만
    @Transactional(readOnly = true)
    public Page<LoanApplication> search(LoanApplicationStatus status, String memberLoginId,
                                        LocalDate from, LocalDate to, int page) {
        return loanSearchRepository.search(status, blankToNull(memberLoginId),
                from.atStartOfDay(), to.plusDays(1).atStartOfDay(),
                PageRequest.of(Math.max(page, 0), PAGE_SIZE));
    }

    // 0건인 상태도 0으로 채워서 돌려준다
    @Transactional(readOnly = true)
    public Map<LoanApplicationStatus, Long> countByStatus(String memberLoginId, LocalDate from, LocalDate to) {
        Map<LoanApplicationStatus, Long> counts = new EnumMap<>(LoanApplicationStatus.class);
        for (LoanApplicationStatus s : LoanApplicationStatus.values()) {
            counts.put(s, 0L);
        }
        for (Object[] row : loanSearchRepository.countByStatus(blankToNull(memberLoginId),
                from.atStartOfDay(), to.plusDays(1).atStartOfDay())) {
            counts.put((LoanApplicationStatus) row[0], (Long) row[1]);
        }
        return counts;
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
