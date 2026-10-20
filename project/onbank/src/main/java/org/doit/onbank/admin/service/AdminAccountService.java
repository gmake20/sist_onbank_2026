package org.doit.onbank.admin.service;

import lombok.RequiredArgsConstructor;
import org.doit.onbank.account.entity.Account;
import org.doit.onbank.account.entity.AccountStatus;
import org.doit.onbank.account.entity.AccountType;
import org.doit.onbank.account.service.AccountManageService;
import org.doit.onbank.admin.dto.AccountListRow;
import org.doit.onbank.admin.repository.AccountSearchRepository;
import org.doit.onbank.common.exception.BusinessException;
import org.doit.onbank.common.security.BackOfficeUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;
import java.util.function.Function;

// ACC-001 계좌 목록·검색 (S-07), ACC-003 거래정지·해제 (M-01)
// 상태·종류 한글 이름은 팀원2 enum 의 getLabel() 을 쓴다
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminAccountService {

    private static final int PAGE_SIZE = 20;

    private final AccountSearchRepository accountSearchRepository;
    private final AccountManageService accountManageService;
    private final AdminLogService adminLogService;

    // 빈 문자열 조건은 null 로 바꿔 "전체"로 처리. enum 은 컨트롤러에서 이미 null 로 들어온다
    public Page<AccountListRow> search(String accountNumber, String memberLoginId,
                                       AccountStatus status, AccountType type, int page) {
        return accountSearchRepository.search(withoutHyphen(accountNumber), blankToNull(memberLoginId),
                status, type, PageRequest.of(Math.max(page, 0), PAGE_SIZE));
    }

    // ---- ACC-003 거래정지·해제 ----
    // 상태 변경은 팀원2 AccountManageService 로만 한다 (account 를 직접 UPDATE 하지 않음, NFR-ADM-QLT-002)
    // 같은 트랜잭션에서 admin_log 를 남긴다 → 상태만 바뀌고 로그가 빠지는 일 없음 (명세서 2.5)

    // 거래정지 : ACTIVE → FROZEN
    @Transactional
    public void freeze(BackOfficeUser me, Long accountId, String reason, String requestIp) {
        changeStatus(me, accountId, "ACCOUNT_FREEZE", reason, requestIp,
                AccountStatus.ACTIVE, accountManageService::freeze);
    }

    // 거래정지 해제 : FROZEN → ACTIVE
    @Transactional
    public void unfreeze(BackOfficeUser me, Long accountId, String reason, String requestIp) {
        changeStatus(me, accountId, "ACCOUNT_UNFREEZE", reason, requestIp,
                AccountStatus.FROZEN, accountManageService::unfreeze);
    }

    // before : 팀원2 메서드가 받아 주는 유일한 상태. 호출이 성공했으면 변경 전 상태는 이 값이다
    private void changeStatus(BackOfficeUser me, Long accountId, String actionType, String reason, String requestIp,
                              AccountStatus before, Function<Long, Account> action) {
        if (reason == null || reason.isBlank() || reason.trim().length() > 200) {   // admin_log.reason 200자
            throw new BusinessException("사유를 200자 이내로 입력하세요.");
        }
        Account account;
        try {
            // 팀원2 메서드가 계좌 행을 잠그고(FOR UPDATE) 상태를 다시 검사한다 → 동시에 눌러도 한 번만 바뀜
            account = action.apply(accountId);
        } catch (NoSuchElementException e) {
            throw new BusinessException("없는 계좌입니다.");
        } catch (IllegalStateException e) {      // 다른 관리자가 먼저 바꾼 경우 포함
            throw new BusinessException("이미 처리된 요청입니다. 계좌 상태를 다시 확인하세요.");
        }
        adminLogService.record(me, actionType, "ACCOUNT", String.valueOf(accountId),
                before.name(), account.getAccountStatus().name(), reason.trim(), requestIp);
    }

    // 1002-482 / 1002482 둘 다 찾도록 하이픈만 뺀다. DB 쪽도 replace 로 하이픈을 빼고 비교
    // (숫자만 남기면 TEST-0001 같은 테스트 계좌를 'TEST'로 검색할 때 빈 값이 돼 전체가 나옴)
    private String withoutHyphen(String value) {
        String v = blankToNull(value);
        return (v == null) ? null : v.replace("-", "");
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
