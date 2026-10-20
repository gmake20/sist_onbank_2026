package org.doit.onbank.admin.service;

import lombok.RequiredArgsConstructor;
import org.doit.onbank.admin.dto.AdminCreateForm;
import org.doit.onbank.admin.dto.AdminCreateResult;
import org.doit.onbank.admin.entity.Admin;
import org.doit.onbank.admin.entity.AdminRole;
import org.doit.onbank.admin.entity.AdminStatus;
import org.doit.onbank.admin.repository.AdminRepository;
import org.doit.onbank.admin.repository.AdminSearchRepository;
import org.doit.onbank.common.exception.BusinessException;
import org.doit.onbank.common.security.BackOfficeUser;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

// 계정 관리, 계정 생성도 추가
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminManageService {

    private static final String TEMP_PW_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
    private static final int TEMP_PW_LENGTH = 10;

    private final AdminSearchRepository adminSearchRepository;
    private final AdminRepository adminRepository;

    private final AdminLogService adminLogService;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom random = new SecureRandom();

    // 검색어가 비어있으면 null로 -> 쿼리에서 조건 빠짐
    public List<Admin> searchAdmins(String keyword, AdminRole role, AdminStatus status) {
        String kw = (keyword == null || keyword.isBlank()) ? null : keyword.trim();
        return adminSearchRepository.search(kw, role, status);
    }

    // S-20 관리자 계정 생성 : 계정 저장 + admin_log 기록을 한 트랜잭션으로 (로그가 실패하면 계정도 롤백)
    @Transactional
    public AdminCreateResult createAdmin(BackOfficeUser me, AdminCreateForm form, String requestIp) {
        // 최고 관리자는 1명(시드)뿐. 화면 선택지에 없지만 서버에서도 막는다 (D-14)
        if (form.getRole() == AdminRole.SUPER_ADMIN) {
            throw new IllegalArgumentException("최고 관리자 계정은 만들 수 없습니다.");
        }
        if (adminRepository.findByLoginId(form.getLoginId()).isPresent()) {
            throw new IllegalStateException("이미 사용 중인 아이디입니다.");
        }

        String tempPassword = createTempPassword();
        Admin admin = adminRepository.save(Admin.builder()
                                            .loginId(form.getLoginId())
                                            .password(passwordEncoder.encode(tempPassword))
                                            .name(form.getName().trim())
                                            .role(form.getRole())
                                            .tempPassword(true) // 첫 로그인 때 비밀번호 변경 (D-07)
                                            .build());

        // 임시 비밀번호는 로그에 남기지 않는다.
        adminLogService.record(me, "ADMIN_CREATE", "ADMIN", String.valueOf(admin.getAdminId()),
                  null, admin.getLoginId() + " " + admin.getRole().name(), form.getReason().trim(), requestIp);

        return new AdminCreateResult(admin.getLoginId(), admin.getName(), admin.getRole(), tempPassword);
    }

    // 비활성화 : ACTIVE, LOCKED -> INACTIVE
    @Transactional
    public void deactivate(BackOfficeUser me, Long adminId, String reason, String requestIp ) {
        changeStatus(me, adminId, "ADMIN_DEACTIVATE", reason, requestIp,
                EnumSet.of(AdminStatus.ACTIVE, AdminStatus.LOCKED), Admin::deactivate ) ;
    }

    // 활성화 : INACTIVE -> ACTIVE
    @Transactional
    public void activate(BackOfficeUser me, Long adminId, String reason, String requestIp ) {
        changeStatus(me, adminId, "ADMIN_ACTIVATE", reason, requestIp,
                EnumSet.of(AdminStatus.INACTIVE), Admin::activate ) ;
    }

    // 로그인 잠금 해제 : LOCKED -> ACTIVE
    @Transactional
    public void unlock(BackOfficeUser me, Long adminId, String reason, String requestIp ) {
        changeStatus(me, adminId, "ADMIN_UNLOCK", reason, requestIp,
                EnumSet.of(AdminStatus.LOCKED), Admin::unlock) ;
    }

    private String createTempPassword() {
        StringBuilder sb = new StringBuilder(TEMP_PW_LENGTH);
        for (int i = 0; i < TEMP_PW_LENGTH; i++) {
            sb.append(TEMP_PW_CHARS.charAt(random.nextInt(TEMP_PW_CHARS.length())));
        }
        return sb.toString();
    }

    // 상태 변경 + admin_log 기록을 한 트랜잭션으로. 최고 관리자는 화면에 버튼이 없지만 서버에서도 막는다
    private void changeStatus(BackOfficeUser me, Long adminId, String actionType, String reason, String requestIp,
                              Set<AdminStatus> allowed, Consumer<Admin> action) {
        Admin admin = adminRepository.findById(adminId)
                .orElseThrow(() -> new BusinessException("없는 계정입니다."));
        if (admin.getRole() == AdminRole.SUPER_ADMIN) {
            throw new BusinessException("최고 관리자 계정은 상태를 바꿀 수 없습니다.");
        }
        if (!allowed.contains(admin.getStatus())) {       // 다른 화면에서 먼저 바꾼 경우
            throw new BusinessException("계정 상태가 이미 바뀌었습니다. 목록을 다시 확인하세요.");
        }
        if (reason == null || reason.isBlank() || reason.trim().length() > 200) {   // admin_log.reason 200자
            throw new BusinessException("사유를 200자 이내로 입력하세요.");
        }
        AdminStatus before = admin.getStatus();
        action.accept(admin);
        adminLogService.record(me, actionType, "ADMIN", String.valueOf(adminId),
                before.name(), admin.getStatus().name(), reason.trim(), requestIp);
    }



    }



