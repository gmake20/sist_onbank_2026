package org.doit.onbank.admin.service;

import lombok.RequiredArgsConstructor;
import org.doit.onbank.admin.dto.EmployeeCreateForm;
import org.doit.onbank.admin.dto.EmployeeCreateResult;
import org.doit.onbank.admin.repository.EmployeeSearchRepository;
import org.doit.onbank.branch.entity.Branch;
import org.doit.onbank.branch.entity.BranchType;
import org.doit.onbank.branch.repository.BranchRepository;
import org.doit.onbank.common.exception.BusinessException;
import org.doit.onbank.common.security.BackOfficeUser;
import org.doit.onbank.employee.entity.Employee;
import org.doit.onbank.employee.entity.EmployeeRole;
import org.doit.onbank.employee.entity.EmployeeStatus;
import org.doit.onbank.employee.repository.EmployeeRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

// 직원 계정 관리 (ACT-005). 목록 조회 + 생성
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EmployeeManageService {

    // 직급 선택지
    public static final List<String> POSITIONS = List.of("사원", "대리", "과장", "차장", "부장", "지점장");

    private static final String TEMP_PW_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
    private static final int TEMP_PW_LENGTH = 10;

    private final EmployeeSearchRepository employeeSearchRepository;
    private final EmployeeRepository employeeRepository;
    private final AdminLogService adminLogService;
    private final PasswordEncoder passwordEncoder;
    private final BranchRepository branchRepository;
    private final SecureRandom random = new SecureRandom();

    // 검색어가 비어 있으면 null
    public List<Employee> searchEmployees(String keyword, Long branchId, EmployeeRole role, EmployeeStatus status) {
        String kw = (keyword == null || keyword.isBlank()) ? null : keyword.trim();
        return employeeSearchRepository.search(kw, branchId, role, status);
    }

    // 목록의 "소속 지점"칸 Employee.branchId는 Long 이라 이름을 따로 찾아야 한다.
    public Map<Long, String> branchNames() {
        return branchRepository.findAll().stream().collect(Collectors.toMap(Branch::getBranchId, Branch::getName));
    }

    // 검색 조건 select용 (사용 중인 지점만)
    public List<Branch> selectableBranches() {
        return branchRepository.findByBranchTypeAndInUseTrueOrderByNameAsc(BranchType.BRANCH);
    }

    // 로그인 잠금 해제 : LOCKED → ACTIVE (실패 횟수 0)
    @Transactional
    public void unlockLogin(BackOfficeUser me, Long employeeId, String reason, String requestIp) {
        changeStatus(me, employeeId, "EMPLOYEE_UNLOCK", reason, requestIp,
                EnumSet.of(EmployeeStatus.LOCKED), Employee::unlockLogin);
    }

    // 휴직 : ACTIVE·LOCKED → ON_LEAVE
    @Transactional
    public void leave(BackOfficeUser me, Long employeeId, String reason, String requestIp) {
        changeStatus(me, employeeId, "EMPLOYEE_LEAVE", reason, requestIp,
                EnumSet.of(EmployeeStatus.ACTIVE, EmployeeStatus.LOCKED), Employee::leave);
    }

    // 복직 : ON_LEAVE → ACTIVE (실패 횟수 0)
    @Transactional
    public void returnFromLeave(BackOfficeUser me, Long employeeId, String reason, String requestIp) {
        changeStatus(me, employeeId, "EMPLOYEE_RETURN", reason, requestIp,
                EnumSet.of(EmployeeStatus.ON_LEAVE), Employee::returnFromLeave);
    }

    // 퇴사 : RETIRED가 아니면 → RETIRED. 되돌리지 않는다
    // 퇴사일은 처리한 날로 기록하고, 실제 퇴사일이 다르면 사유에 적는다 (2026-10-20 결정)
    @Transactional
    public void retire(BackOfficeUser me, Long employeeId, String reason, String requestIp) {
        changeStatus(me, employeeId, "EMPLOYEE_RETIRE", reason, requestIp,
                EnumSet.of(EmployeeStatus.ACTIVE, EmployeeStatus.LOCKED, EmployeeStatus.ON_LEAVE),
                e -> e.retire(LocalDate.now()));
    }

    // 상태 변경 + admin_log 기록을 한 트랜잭션으로 (AdminManageService.changeStatus와 같은 방식)
    private void changeStatus(BackOfficeUser me, Long employeeId, String actionType, String reason, String requestIp,
                              Set<EmployeeStatus> allowed, Consumer<Employee> action) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new BusinessException("없는 직원입니다."));
        if (!allowed.contains(employee.getStatus())) {       // 다른 화면에서 먼저 바꾼 경우
            throw new BusinessException("직원 상태가 이미 바뀌었습니다. 목록을 다시 확인하세요.");
        }
        if (reason == null || reason.isBlank() || reason.trim().length() > 200) {   // admin_log.reason 200자
            throw new BusinessException("사유를 200자 이내로 입력하세요.");
        }
        EmployeeStatus before = employee.getStatus();
        action.accept(employee);
        adminLogService.record(me, actionType, "EMPLOYEE", String.valueOf(employeeId),
                before.name(), employee.getStatus().name(), reason.trim(), requestIp);
    }

    // 직원 계정 생성 : 저장 + admin_log 한 트랜잭션으로 (로그 실패 시 직원도 롤백)
    @Transactional
    public EmployeeCreateResult createEmployee(BackOfficeUser me, EmployeeCreateForm form, String requestIp) {
        // 화면 select에 없는 값은 직접 보낸 요청(정상적인 요청 X)
        if (!POSITIONS.contains(form.getPosition())) {
            throw new BusinessException("선택할 수 없는 직급입니다");
        }


        // branch_type 이 BRANCH인 행만 소속 가능
        Branch branch = branchRepository.findById(form.getBranchId())
                .filter(b -> b.getBranchType() == BranchType.BRANCH && b.isInUse())
                .orElseThrow(() -> new BusinessException("선택 할 수 없는 지점입니다."));

        String email = form.getEmail().trim();
        if (employeeRepository.existsByEmail(email)) {
            throw new IllegalStateException("이미 등록된 이메일 입니다.");
        }
        String tempPassword = createTempPassword();
        Employee employee;

        try {
            //
            employee = employeeRepository.saveAndFlush(Employee.builder()
                    .employeeNo(nextEmployeeNo())
                    .password(passwordEncoder.encode(tempPassword))
                    .name(form.getName().trim())
                    .email(email)
                    .phone(form.getPhone() == null || form.getPhone().isBlank() ? null : form.getPhone().trim())
                    .branchId(branch.getBranchId())
                    .position(form.getPosition())
                    .role(form.getRole())
                    .hiredAt(form.getHiredAt())
                    .tempPassword(true) // 첫 로그인 시 비밀번호 변경
                    .build());

        } catch (DataIntegrityViolationException e) {
            throw new BusinessException("잠시 후 다시 시도해 주세요");
        }

        // 임시 비밀번호는 로그에 남기지 않는다.
        adminLogService.record(me, "EMPLOYEE_CREATE", "EMPLOYEE", String.valueOf(employee.getEmployeeId()),
                null, employee.getEmployeeNo() + " " + employee.getRole().name(), form.getReason().trim(), requestIp);

        return new EmployeeCreateResult(employee.getEmployeeNo(), employee.getName(), branch.getName(),
                employee.getRole(), tempPassword);
    }

    // 마지막 사번 숫자 + 1, "E" + 4자리. 없으면 E0001 (사번 재사용 하지 않는다)
    private String nextEmployeeNo() {
        return employeeRepository.findTopByEmployeeNoStartingWithOrderByEmployeeNoDesc("E")
                .map(e -> String.format("E%04d", Integer.parseInt(e.getEmployeeNo().substring(1)) + 1))
                .orElse("E0001");
    }

    // AdminManageService 와 같은 방식
    private String createTempPassword() {
        StringBuilder sb = new StringBuilder(TEMP_PW_LENGTH);
        for (int i = 0; i < TEMP_PW_LENGTH; i++) {
            sb.append(TEMP_PW_CHARS.charAt(random.nextInt(TEMP_PW_CHARS.length())));
        }
        return sb.toString();
    }


}
