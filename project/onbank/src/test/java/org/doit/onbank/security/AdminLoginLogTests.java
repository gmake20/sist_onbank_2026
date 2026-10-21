package org.doit.onbank.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.time.LocalDate;
import java.util.List;

import org.doit.onbank.admin.entity.Admin;
import org.doit.onbank.admin.entity.AdminLoginLog;
import org.doit.onbank.admin.entity.AdminRole;
import org.doit.onbank.admin.repository.AdminLoginLogRepository;
import org.doit.onbank.admin.repository.AdminRepository;
import org.doit.onbank.admin.service.AdminLogService;
import org.doit.onbank.common.security.LoginFailReason;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

// 관리자 로그인 이력(admin_login_log) : 성공·실패마다 한 줄씩 남는지 확인
// @Transactional : 테스트 계정과 이력은 테스트가 끝나면 롤백 (공용 DB에 남지 않음)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminLoginLogTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private AdminRepository adminRepository;

	@Autowired
	private AdminLoginLogRepository adminLoginLogRepository;

	@Autowired
	private AdminLogService adminLogService;

	@Autowired
	private PasswordEncoder passwordEncoder;

	private final String suffix = String.valueOf(System.currentTimeMillis() % 100_000_000);
	private final String loginId = "loginlog" + suffix;
	private final String rawPassword = "Test1234!";

	private Admin admin;

	@BeforeEach
	void saveAdmin() {
		admin = adminRepository.saveAndFlush(Admin.builder()
				.loginId(loginId)
				.password(passwordEncoder.encode(rawPassword))
				.name("이력테스트")
				.role(AdminRole.SUPER_ADMIN)
				.tempPassword(false)
				.build());
	}

	@Test
	void successIsLogged() throws Exception {
		login(loginId, rawPassword);

		AdminLoginLog log = latest(loginId);
		assertThat(log.isSuccess()).isTrue();
		assertThat(log.getFailReason()).isNull();
		assertThat(log.getAdminId()).isEqualTo(admin.getAdminId());
		assertThat(log.getIpAddress()).isNotBlank();
		assertThat(log.getLoginAt()).isNotNull();
	}

	@Test
	void wrongPasswordIsLoggedAsBadCredentials() throws Exception {
		login(loginId, "wrong");

		AdminLoginLog log = latest(loginId);
		assertThat(log.isSuccess()).isFalse();
		assertThat(log.getFailReason()).isEqualTo("BAD_CREDENTIALS");
		assertThat(log.getAdminId()).isEqualTo(admin.getAdminId());
	}

	@Test
	void unknownIdIsLoggedWithoutAdminId() throws Exception {
		String unknown = "nobody" + suffix;
		login(unknown, rawPassword);

		AdminLoginLog log = latest(unknown);
		assertThat(log.isSuccess()).isFalse();
		assertThat(log.getFailReason()).isEqualTo("UNKNOWN_ID");
		assertThat(log.getAdminId()).isNull();
	}

	// 5회 오류로 잠긴 뒤에는 LOGIN_LOCKED (맞는 비밀번호여도)
	@Test
	void lockedAccountIsLoggedAsLoginLocked() throws Exception {
		for (int i = 0; i < 5; i++) {
			login(loginId, "wrong");
		}
		login(loginId, rawPassword);

		List<AdminLoginLog> logs = adminLoginLogRepository.findByLoginIdInputOrderByLoginLogIdDesc(loginId);
		assertThat(logs).hasSize(6);
		assertThat(logs.get(0).getFailReason()).isEqualTo("LOGIN_LOCKED");
		assertThat(logs.subList(1, 6)).allMatch(l -> "BAD_CREDENTIALS".equals(l.getFailReason()));
	}

	@Test
	void inactiveAccountIsLoggedAsInactive() throws Exception {
		admin.deactivate();
		adminRepository.saveAndFlush(admin);

		login(loginId, rawPassword);

		assertThat(latest(loginId).getFailReason()).isEqualTo("INACTIVE");
	}

	// 50자를 넘는 아이디는 잘라서 저장 (INSERT 실패 없음)
	@Test
	void longLoginIdIsCut() throws Exception {
		String longId = "x".repeat(80);
		login(longId, rawPassword);

		assertThat(latest(longId.substring(0, 50)).getFailReason()).isEqualTo("UNKNOWN_ID");
	}

	// ---------- 조회 화면 (S-14 로그인 이력 탭) ----------

	// 로그인한 SUPER_ADMIN이 이력 화면을 열면 방금 남은 성공·실패 기록이 보인다
	@Test
	void loginLogPageShowsLogs() throws Exception {
		login(loginId, "wrong");
		MockHttpSession session = (MockHttpSession) mockMvc.perform(formLogin("/admin/login")
						.userParameter("loginId").user(loginId).password(rawPassword))
				.andReturn().getRequest().getSession(false);

		mockMvc.perform(get("/admin/logs/logins").session(session).param("loginId", loginId))
				.andExpect(status().isOk())
				.andExpect(view().name("backoffice/admin/login-logs"))
				.andExpect(content().string(containsString("비밀번호 오류")))
				.andExpect(content().string(containsString("이력테스트")));
	}

	@Test
	void loginLogPageRequiresLogin() throws Exception {
		mockMvc.perform(get("/admin/logs/logins"))
				.andExpect(status().is3xxRedirection());
	}

	// 활동 로그·로그인 이력은 SUPER_ADMIN 전용 : OPERATOR, SERVICE 는 403
	@ParameterizedTest
	@EnumSource(value = AdminRole.class, names = { "OPERATOR", "SERVICE" })
	void logPagesAreForbiddenForOtherRoles(AdminRole role) throws Exception {
		String otherId = "logrole" + suffix;
		adminRepository.saveAndFlush(Admin.builder()
				.loginId(otherId)
				.password(passwordEncoder.encode(rawPassword))
				.name("권한테스트")
				.role(role)
				.tempPassword(false)
				.build());
		MockHttpSession session = (MockHttpSession) mockMvc.perform(formLogin("/admin/login")
						.userParameter("loginId").user(otherId).password(rawPassword))
				.andReturn().getRequest().getSession(false);

		mockMvc.perform(get("/admin/logs/logins").session(session)).andExpect(status().isForbidden());
		mockMvc.perform(get("/admin/logs").session(session)).andExpect(status().isForbidden());
		// 로그인은 정상 (관리자 홈은 열림)
		mockMvc.perform(get("/admin").session(session)).andExpect(status().isOk());
	}

	@Test
	void logPagesAreOpenForSuperAdmin() throws Exception {
		MockHttpSession session = (MockHttpSession) mockMvc.perform(formLogin("/admin/login")
						.userParameter("loginId").user(loginId).password(rawPassword))
				.andReturn().getRequest().getSession(false);

		mockMvc.perform(get("/admin/logs/logins").session(session)).andExpect(status().isOk());
		mockMvc.perform(get("/admin/logs").session(session)).andExpect(status().isOk());
	}

	// 결과·사유·아이디 조건으로 걸러진다
	@Test
	void searchFiltersByResultAndReason() throws Exception {
		login(loginId, "wrong");
		login(loginId, rawPassword);
		LocalDate today = LocalDate.now();

		assertThat(adminLogService.searchLogins(loginId, null, null, null, today, today, 0).getTotalElements())
				.isEqualTo(2);
		assertThat(adminLogService.searchLogins(loginId, true, null, null, today, today, 0).getContent())
				.singleElement().satisfies(r -> assertThat(r.adminName()).isEqualTo("이력테스트"));
		assertThat(adminLogService.searchLogins(loginId, false, LoginFailReason.BAD_CREDENTIALS, null, today, today, 0)
				.getContent())
				.singleElement().satisfies(r -> assertThat(r.failReasonLabel()).isEqualTo("비밀번호 오류"));
		assertThat(adminLogService.searchLogins(loginId, false, LoginFailReason.UNKNOWN_ID, null, today, today, 0)
				.getTotalElements()).isZero();
		// 어제까지로 조회하면 오늘 기록은 나오지 않는다
		assertThat(adminLogService.searchLogins(loginId, null, null, null, today.minusDays(7), today.minusDays(1), 0)
				.getTotalElements()).isZero();
	}

	private void login(String id, String password) throws Exception {
		mockMvc.perform(formLogin("/admin/login").userParameter("loginId").user(id).password(password));
	}

	private AdminLoginLog latest(String loginIdInput) {
		List<AdminLoginLog> logs = adminLoginLogRepository.findByLoginIdInputOrderByLoginLogIdDesc(loginIdInput);
		assertThat(logs).isNotEmpty();
		return logs.get(0);
	}
}
