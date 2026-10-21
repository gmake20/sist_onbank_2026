package org.doit.onbank.admin.service;

import lombok.RequiredArgsConstructor;
import org.doit.onbank.admin.dto.AdminLogRow;
import org.doit.onbank.admin.dto.AdminLoginLogRow;
import org.doit.onbank.admin.entity.Admin;
import org.doit.onbank.admin.entity.AdminLog;
import org.doit.onbank.admin.entity.AdminLoginLog;
import org.doit.onbank.admin.repository.AdminLogRepository;
import org.doit.onbank.admin.repository.AdminLoginLogRepository;
import org.doit.onbank.admin.repository.AdminRepository;
import org.doit.onbank.common.security.ActorType;
import org.doit.onbank.common.security.BackOfficeUser;
import org.doit.onbank.common.security.LoginFailReason;
import org.doit.onbank.employee.entity.Employee;
import org.doit.onbank.employee.repository.EmployeeRepository;
import org.doit.onbank.member.entity.Member;
import org.doit.onbank.member.repository.MemberRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

// 직원·관리자 작업을 admin_log에 남긴다
// 계좌 동결 같은 작업의 @Transactional 안에서 부르면 같은 트랜잭션으로 묶인다
//  → 작업이 롤백되면 로그도 같이 롤백 (작업 없이 로그만 남거나, 로그 없이 작업만 되는 일이 없음)
// 사용 예) adminLogService.record(me, "ACCOUNT_FREEZE", "ACCOUNT", String.valueOf(accountId),
//                                 변경전상태, 변경후상태, reason, request.getRemoteAddr());

@Service
@RequiredArgsConstructor
public class AdminLogService {

    private static final int PAGE_SIZE = 20;

    private final AdminLogRepository adminLogRepository;
    private final AdminLoginLogRepository adminLoginLogRepository;
    private final AdminRepository adminRepository;
    private final EmployeeRepository employeeRepository;
    private final MemberRepository memberRepository;

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

    // ---- LOG-002 활동 로그 조회 (S-14) ----

    // 기간은 from 00:00 이상 ~ to 다음 날 00:00 미만. 빈 문자열 조건은 null로 바꿔 "전체"로 처리
    @Transactional(readOnly = true)
    public Page<AdminLogRow> search(ActorType actorType, String actorLoginId, String actionType,
                                    String targetType, String targetId, LocalDate from, LocalDate to, int page) {
        Page<AdminLog> logs = adminLogRepository.search(actorType, blankToNull(actorLoginId),
                blankToNull(actionType), blankToNull(targetType), blankToNull(targetId),
                from.atStartOfDay(), to.plusDays(1).atStartOfDay(),
                PageRequest.of(Math.max(page, 0), PAGE_SIZE));

        // 이 페이지에 나온 작업자 + 대상(ADMIN / EMPLOYEE / MEMBER)만 모아서 테이블마다 한 번씩 조회 (최대 40명)
        Set<Long> adminIds = new HashSet<>(actorIds(logs, ActorType.ADMIN));
        adminIds.addAll(targetIds(logs, ActorType.ADMIN.name()));
        Set<Long> employeeIds = new HashSet<>(actorIds(logs, ActorType.EMPLOYEE));
        employeeIds.addAll(targetIds(logs, ActorType.EMPLOYEE.name()));
        Set<Long> memberIds = targetIds(logs, "MEMBER");

        Map<Long, Admin> admins = adminRepository.findAllById(adminIds).stream()
                .collect(Collectors.toMap(Admin::getAdminId, Function.identity()));
        Map<Long, Employee> employees = employeeRepository.findAllById(employeeIds).stream()
                .collect(Collectors.toMap(Employee::getEmployeeId, Function.identity()));
        Map<Long, Member> members = memberRepository.findAllById(memberIds).stream()
                .collect(Collectors.toMap(Member::getMemberId, Function.identity()));

        return logs.map(l -> new AdminLogRow(l,
                person(l.getActorType().name(), l.getActorId(), admins, employees, members),
                person(l.getTargetType(), parseId(l.getTargetId()), admins, employees, members)));
    }

    // ---- S-14 로그인 이력 탭 (admin_login_log) ----

    // 기간·빈 문자열 처리는 활동 로그와 같다. success : true 성공만 / false 실패만 / null 전체
    @Transactional(readOnly = true)
    public Page<AdminLoginLogRow> searchLogins(String loginId, Boolean success, LoginFailReason failReason,
                                               String ipAddress, LocalDate from, LocalDate to, int page) {
        Page<AdminLoginLog> logs = adminLoginLogRepository.search(blankToNull(loginId), success,
                failReason == null ? null : failReason.name(), blankToNull(ipAddress),
                from.atStartOfDay(), to.plusDays(1).atStartOfDay(),
                PageRequest.of(Math.max(page, 0), PAGE_SIZE));

        // 이 페이지에 나온 관리자만 한 번에 조회 (최대 20명)
        Set<Long> adminIds = logs.stream()
                .map(AdminLoginLog::getAdminId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> names = adminRepository.findAllById(adminIds).stream()
                .collect(Collectors.toMap(Admin::getAdminId, Admin::getName));

        return logs.map(l -> new AdminLoginLogRow(l, l.getAdminId() == null ? null : names.get(l.getAdminId())));
    }

    @Transactional(readOnly = true)
    public List<String> actionTypes() {
        return adminLogRepository.findActionTypes();
    }

    @Transactional(readOnly = true)
    public List<String> targetTypes() {
        return adminLogRepository.findTargetTypes();
    }

    private Set<Long> actorIds(Page<AdminLog> logs, ActorType type) {
        return logs.stream()
                .filter(l -> l.getActorType() == type)
                .map(AdminLog::getActorId)
                .collect(Collectors.toSet());
    }

    // target_type 값이 ADMIN / EMPLOYEE / MEMBER 인 줄의 target_id
    private Set<Long> targetIds(Page<AdminLog> logs, String targetType) {
        return logs.stream()
                .filter(l -> targetType.equals(l.getTargetType()))
                .map(l -> parseId(l.getTargetId()))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    // type = ADMIN / EMPLOYEE / MEMBER 일 때만 사람을 찾는다. 다른 대상이거나 못 찾으면 null
    private AdminLogRow.Person person(String type, Long id, Map<Long, Admin> admins,
                                      Map<Long, Employee> employees, Map<Long, Member> members) {
        if (id == null) {
            return null;
        }
        if (ActorType.ADMIN.name().equals(type)) {
            Admin a = admins.get(id);
            return a == null ? null : new AdminLogRow.Person(a.getLoginId(), a.getName());
        }
        if (ActorType.EMPLOYEE.name().equals(type)) {
            Employee e = employees.get(id);
            return e == null ? null : new AdminLogRow.Person(e.getEmployeeNo(), e.getName());
        }
        if ("MEMBER".equals(type)) {
            Member m = members.get(id);
            return m == null ? null : new AdminLogRow.Person(m.getLoginId(), m.getName());
        }
        return null;
    }

    // target_id는 VARCHAR라 숫자가 아닐 수 있다 (나중에 계좌번호 등)
    private Long parseId(String value) {
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

}
