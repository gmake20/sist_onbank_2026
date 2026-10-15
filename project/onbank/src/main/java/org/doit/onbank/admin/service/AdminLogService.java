package org.doit.onbank.admin.service;

import lombok.RequiredArgsConstructor;
import org.doit.onbank.admin.entity.AdminLog;
import org.doit.onbank.admin.repository.AdminLogRepository;
import org.doit.onbank.common.security.BackOfficeUser;
import org.springframework.stereotype.Service;

// 직원·관리자 작업을 admin_log에 남긴다
// 계좌 동결 같은 작업의 @Transactional 안에서 부르면 같은 트랜잭션으로 묶인다
//  → 작업이 롤백되면 로그도 같이 롤백 (작업 없이 로그만 남거나, 로그 없이 작업만 되는 일이 없음)
// 사용 예) adminLogService.record(me, "ACCOUNT_FREEZE", "ACCOUNT", String.valueOf(accountId),
//                                 변경전상태, 변경후상태, reason, request.getRemoteAddr());

@Service
@RequiredArgsConstructor
public class AdminLogService {

    private final AdminLogRepository adminLogRepository;

    public void record(BackOfficeUser actor, String actionType, String targetType, String targetId,
                       String beforeValue, String afterValue, String reason, String requestIp) {
        adminLogRepository.save(AdminLog.builder()
                            .actorType(actor.getActorType())
                            .actorId(actor.getActorId())
                            .actionType(actionType)
                            .targetType(targetType)
                            .targetId(targetId)
                            .beforeValue(beforeValue)
                            .afterValue(afterValue)
                            .reason(reason)
                            .requestIp(requestIp)
                            .build());
    }

}
