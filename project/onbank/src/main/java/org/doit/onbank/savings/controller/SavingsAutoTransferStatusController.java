package org.doit.onbank.savings.controller;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.doit.onbank.member.security.LoginMember;
import org.doit.onbank.savings.entity.SavingsAutoTransfer;
import org.doit.onbank.savings.entity.SavingsStatus;
import org.doit.onbank.savings.repository.SavingsAutoTransferRepository;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequiredArgsConstructor
@RequestMapping("/savings")
public class SavingsAutoTransferStatusController {
    private final SavingsAutoTransferRepository repository;

    public record TransferRow(Long accountId, String productName, long monthlyAmount,
            int transferDay, boolean enabled, String statusCode, String statusLabel,
            LocalDate lastSuccessOn, String lastError, String nextAttemptLabel) {}

    @GetMapping("/auto-transfers")
    @Transactional(readOnly = true)
    public String status(@AuthenticationPrincipal LoginMember member, Model model) {
        if (member == null) return "redirect:/login";

        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        List<TransferRow> rows = repository.findByAccount_MemberIdOrderByIdDesc(member.getMemberId())
            .stream().map(setting -> toRow(setting, today)).toList();

        model.addAttribute("transfers", rows);
        model.addAttribute("totalCount", rows.size());
        model.addAttribute("activeCount", rows.stream().filter(r -> "ACTIVE".equals(r.statusCode())).count());
        model.addAttribute("failedCount", rows.stream().filter(r -> "FAILED".equals(r.statusCode())).count());
        model.addAttribute("disabledCount", rows.stream().filter(r -> "DISABLED".equals(r.statusCode())).count());
        return "savings/auto-transfers";
    }

    private TransferRow toRow(SavingsAutoTransfer setting, LocalDate today) {
        var account = setting.getAccount();
        boolean accountActive = account.getStatus() == SavingsStatus.ACTIVE;
        String code;
        String label;
        if (!setting.isEnabled() || !accountActive || !today.isBefore(account.getMaturityOn())) {
            code = "DISABLED"; label = "중지/종료";
        } else if (setting.getLastError() != null && !setting.getLastError().isBlank()) {
            code = "FAILED"; label = "최근 실패";
        } else {
            code = "ACTIVE"; label = "정상 설정";
        }

        String nextAttempt = "—";
        if ("ACTIVE".equals(code) || "FAILED".equals(code)) {
            YearMonth month = YearMonth.from(today);
            // 마지막 성공한 달은 다음 달로; 지정일이 지난 실패 건은 다음 실행일부터 재시도.
            if (month.toString().equals(setting.getLastSuccessMonth())) {
                nextAttempt = month.plusMonths(1).atDay(setting.getTransferDay()).toString();
            } else if (today.getDayOfMonth() > setting.getTransferDay()) {
                nextAttempt = today.plusDays(1).toString();
            } else {
                nextAttempt = month.atDay(setting.getTransferDay()).toString();
            }
            // 실제 스케줄러는 09:00 KST에 실행하며 누락 회차/잔액에 따라 결과 달라짐.
        }

        return new TransferRow(account.getId(), account.getProduct().getName(),
            account.getMonthlyAmount(), setting.getTransferDay(), setting.isEnabled(),
            code, label, setting.getLastSuccessOn(), setting.getLastError(), nextAttempt);
    }
}
