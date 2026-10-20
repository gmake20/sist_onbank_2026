package org.doit.onbank.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;

import java.time.LocalDate;

import org.doit.onbank.admin.entity.Admin;
import org.doit.onbank.admin.entity.AdminRole;
import org.doit.onbank.admin.entity.AdminStatus;
import org.doit.onbank.admin.repository.AdminRepository;
import org.doit.onbank.employee.entity.Employee;
import org.doit.onbank.employee.entity.EmployeeRole;
import org.doit.onbank.employee.entity.EmployeeStatus;
import org.doit.onbank.employee.repository.EmployeeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;

// 직원(/employee/**)·관리자(/admin/**) 로그인과 영역 분리 확인
// @Transactional : 테스트 계정은 테스트가 끝나면 롤백 (공용 DB에 남지 않음)
// 직원 소속 지점이 필요하므로 공용 DB에 branch 시드 데이터(data-team1-mysql.sql)가 있어야 한다
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BackOfficeLoginTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private EmployeeRepository employeeRepository;

	@Autowired
	private AdminRepository adminRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private EntityManager entityManager;

	private final String suffix = String.valueOf(System.currentTimeMillis() % 100_000_000);
	private final String staffNo = "S" + suffix;	// 사번은 10자 이하
	private final String managerNo = "M" + suffix;
	private final String adminId = "testadmin" + suffix;
	private final String rawPassword = "Test1234!";

	@BeforeEach
	void saveAccounts() {
		Long branchId = jdbcTemplate.queryForObject(
				"SELECT MIN(branch_id) FROM branch WHERE branch_type = 'BRANCH'", Long.class);

		employeeRepository.saveAndFlush(newEmployee(staffNo, EmployeeRole.STAFF, branchId));
		employeeRepository.saveAndFlush(newEmployee(managerNo, EmployeeRole.MANAGER, branchId));
		adminRepository.saveAndFlush(Admin.builder()
				.loginId(adminId)
				.password(passwordEncoder.encode(rawPassword))
				.name("관리자테스트")
				.role(AdminRole.SUPER_ADMIN)
				.tempPassword(false)
				.build());
	}

	private Employee newEmployee(String employeeNo, EmployeeRole role, Long branchId) {
		return Employee.builder()
				.employeeNo(employeeNo)
				.password(passwordEncoder.encode(rawPassword))
				.name("직원테스트")
				.email(employeeNo + "@onbank.test")
				.branchId(branchId)
				.position("대리")
				.role(role)
				.hiredAt(LocalDate.of(2020, 1, 1))
				.tempPassword(false)
				.build();
	}

	private MockHttpSession loginEmployee(String employeeNo) throws Exception {
		return (MockHttpSession) mockMvc.perform(formLogin("/employee/login")
						.userParameter("employeeNo").user(employeeNo).password(rawPassword))
				.andExpect(authenticated())
				.andReturn().getRequest().getSession(false);
	}

	private MockHttpSession loginAdmin() throws Exception {
		return (MockHttpSession) mockMvc.perform(formLogin("/admin/login")
						.userParameter("loginId").user(adminId).password(rawPassword))
				.andExpect(authenticated())
				.andReturn().getRequest().getSession(false);
	}

	// ---------- 직원 로그인 ----------

	@Test
	void employeeLoginSuccess() throws Exception {
		mockMvc.perform(formLogin("/employee/login").userParameter("employeeNo").user(staffNo).password(rawPassword))
				.andExpect(authenticated().withUsername(staffNo).withRoles("STAFF"))
				.andExpect(redirectedUrl("/employee"));
	}

	@Test
	void employeeLoginWrongPassword() throws Exception {
		mockMvc.perform(formLogin("/employee/login").userParameter("employeeNo").user(staffNo).password("wrong"))
				.andExpect(unauthenticated())
				.andExpect(redirectedUrl("/employee/login?error"));
	}

	@Test
	void employeeHomeForStaffAndManager() throws Exception {
		mockMvc.perform(get("/employee").session(loginEmployee(staffNo))).andExpect(status().isOk());
		// MANAGER는 RoleHierarchy로 STAFF 화면도 사용
		mockMvc.perform(get("/employee").session(loginEmployee(managerNo))).andExpect(status().isOk());
	}

	@Test
	void employeeHomeRequiresLogin() throws Exception {
		mockMvc.perform(get("/employee"))
				.andExpect(redirectedUrl("/employee/login"));
	}

	// ---------- 관리자 로그인 ----------

	@Test
	void adminLoginSuccess() throws Exception {
		mockMvc.perform(formLogin("/admin/login").userParameter("loginId").user(adminId).password(rawPassword))
				.andExpect(authenticated().withUsername(adminId).withRoles("SUPER_ADMIN"))
				.andExpect(redirectedUrl("/admin"));
	}

	@Test
	void adminHomeAfterLogin() throws Exception {
		mockMvc.perform(get("/admin").session(loginAdmin())).andExpect(status().isOk());
	}

	// ---------- 영역 분리 ----------

	// 관리자 아이디로 직원 로그인 화면에서 로그인할 수 없다 (각 화면은 자기 테이블만 조회)
	@Test
	void adminAccountCannotLoginAsEmployee() throws Exception {
		mockMvc.perform(formLogin("/employee/login").userParameter("employeeNo").user(adminId).password(rawPassword))
				.andExpect(unauthenticated())
				.andExpect(redirectedUrl("/employee/login?error"));
	}

	// 직원으로 로그인한 세션으로 관리자 영역에 들어갈 수 없다
	@Test
	void employeeSessionCannotAccessAdmin() throws Exception {
		mockMvc.perform(get("/admin").session(loginEmployee(managerNo)))
				.andExpect(redirectedUrl("/admin/login"));
	}

	// 관리자로 로그인한 세션으로 직원 영역에 들어갈 수 없다
	@Test
	void adminSessionCannotAccessEmployee() throws Exception {
		mockMvc.perform(get("/employee").session(loginAdmin()))
				.andExpect(redirectedUrl("/employee/login"));
	}

	// ---------- 비밀번호 5회 오류 잠금 ----------

	private ResultActions employeeLogin(String employeeNo, String password) throws Exception {
		return mockMvc.perform(formLogin("/employee/login").userParameter("employeeNo").user(employeeNo).password(password));
	}

	private ResultActions adminLogin(String password) throws Exception {
		return mockMvc.perform(formLogin("/admin/login").userParameter("loginId").user(adminId).password(password));
	}

	@Test
	void employeeLockedAfterFiveFailures() throws Exception {
		for (int i = 1; i <= 4; i++) {
			employeeLogin(staffNo, "wrong").andExpect(redirectedUrl("/employee/login?error"));
		}
		// 5번째 실패에서 잠기고 잠금 안내
		employeeLogin(staffNo, "wrong").andExpect(redirectedUrl("/employee/login?locked"));

		Employee employee = employeeRepository.findByEmployeeNo(staffNo).orElseThrow();
		assertThat(employee.getLoginFailCount()).isEqualTo(5);
		assertThat(employee.getStatus()).isEqualTo(EmployeeStatus.LOCKED);
		assertThat(employee.getLoginLockedAt()).isNotNull();

		// 잠긴 뒤에는 비밀번호가 맞아도 로그인 불가 (실패 횟수도 더 늘지 않음)
		employeeLogin(staffNo, rawPassword)
				.andExpect(unauthenticated())
				.andExpect(redirectedUrl("/employee/login?locked"));
		assertThat(employeeRepository.findByEmployeeNo(staffNo).orElseThrow().getLoginFailCount()).isEqualTo(5);

		// 잠금 해제 후 다시 로그인 가능
		employee.unlockLogin();
		assertThat(employee.getStatus()).isEqualTo(EmployeeStatus.ACTIVE);
		employeeLogin(staffNo, rawPassword).andExpect(authenticated());
	}

	@Test
	void adminLockedAfterFiveFailures() throws Exception {
		for (int i = 1; i <= 4; i++) {
			adminLogin("wrong").andExpect(redirectedUrl("/admin/login?error"));
		}
		adminLogin("wrong").andExpect(redirectedUrl("/admin/login?locked"));

		Admin admin = adminRepository.findByLoginId(adminId).orElseThrow();
		assertThat(admin.getStatus()).isEqualTo(AdminStatus.LOCKED);
		assertThat(admin.getLoginFailCount()).isEqualTo(5);

		adminLogin(rawPassword).andExpect(unauthenticated()).andExpect(redirectedUrl("/admin/login?locked"));

		admin.unlock();
		adminLogin(rawPassword).andExpect(authenticated());
	}

	// 성공하면 실패 횟수가 0으로 돌아간다 (연속 5회일 때만 잠김). 관리자는 마지막 로그인 시간도 기록
	@Test
	void successResetsFailCount() throws Exception {
		employeeLogin(staffNo, "wrong");
		employeeLogin(staffNo, "wrong");
		assertThat(employeeRepository.findByEmployeeNo(staffNo).orElseThrow().getLoginFailCount()).isEqualTo(2);
		employeeLogin(staffNo, rawPassword).andExpect(authenticated());
		assertThat(employeeRepository.findByEmployeeNo(staffNo).orElseThrow().getLoginFailCount()).isZero();

		adminLogin("wrong");
		adminLogin(rawPassword).andExpect(authenticated());
		Admin admin = adminRepository.findByLoginId(adminId).orElseThrow();
		assertThat(admin.getLoginFailCount()).isZero();
		assertThat(admin.getLastLoginAt()).isNotNull();
	}

	// 휴직·퇴사 직원은 비밀번호가 맞아도 로그인 불가 → ?disabled
	@Test
	void disabledEmployeeCannotLogin() throws Exception {
		jdbcTemplate.update("UPDATE employee SET status = 'ON_LEAVE' WHERE employee_no = ?", staffNo);
		entityManager.clear();	// 위 UPDATE를 엔티티가 다시 읽도록

		employeeLogin(staffNo, rawPassword)
				.andExpect(unauthenticated())
				.andExpect(redirectedUrl("/employee/login?disabled"));
	}

	// 성공 핸들러를 바꾼 뒤에도 로그인 전에 보려던 페이지로 돌아간다 (영역별 requestCache 사용)
	@Test
	void loginReturnsToSavedRequest() throws Exception {
		MockHttpSession session = (MockHttpSession) mockMvc.perform(get("/employee"))
				.andExpect(redirectedUrl("/employee/login"))
				.andReturn().getRequest().getSession(false);

		mockMvc.perform(post("/employee/login").session(session).with(csrf())
						.param("employeeNo", staffNo).param("password", rawPassword))
				.andExpect(authenticated())
				.andExpect(header().string("Location", endsWith("/employee?continue")));
	}

	// ---------- API(fetch) 주소 ----------

	// 로그인하지 않고 API를 부르면 로그인 화면 HTML 대신 401 (화면 주소는 위 테스트처럼 로그인 화면으로)
	@Test
	void apiRequiresLoginReturns401() throws Exception {
		mockMvc.perform(get("/admin/api/test")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/employee/api/test")).andExpect(status().isUnauthorized());
	}

	// 로그인했으면 401이 아니라 컨트롤러까지 간다 (이 주소는 없으므로 404)
	@Test
	void apiAfterLoginPassesSecurity() throws Exception {
		mockMvc.perform(get("/admin/api/test").session(loginAdmin())).andExpect(status().isNotFound());
		mockMvc.perform(get("/employee/api/test").session(loginEmployee(staffNo))).andExpect(status().isNotFound());
	}

	// 직원 세션으로 관리자 API를 불러도 401 (영역 분리)
	@Test
	void employeeSessionCannotCallAdminApi() throws Exception {
		mockMvc.perform(get("/admin/api/test").session(loginEmployee(managerNo))).andExpect(status().isUnauthorized());
	}

	// ---------- CSRF 토큰 만료 ----------

	// 로그인 화면을 띄워 둔 채 서버가 재시작되면 토큰이 맞지 않는다 → 403 대신 로그인 화면으로 (?expired)
	@Test
	void expiredCsrfTokenRedirectsToLoginPage() throws Exception {
		mockMvc.perform(post("/employee/login").with(csrf().useInvalidToken())
						.param("employeeNo", staffNo).param("password", rawPassword))
				.andExpect(unauthenticated())
				.andExpect(redirectedUrl("/employee/login?expired"));
		mockMvc.perform(post("/admin/login").with(csrf().useInvalidToken())
						.param("loginId", adminId).param("password", rawPassword))
				.andExpect(redirectedUrl("/admin/login?expired"));
		mockMvc.perform(post("/login").with(csrf().useInvalidToken())
						.param("loginId", "user1").param("password", rawPassword))
				.andExpect(redirectedUrl("/login?expired"));
	}

	// 직원으로 로그인한 세션은 고객 영역에서 로그인 상태가 아니다
	@Test
	void employeeSessionIsNotCustomerLogin() throws Exception {
		mockMvc.perform(get("/mybank").session(loginEmployee(staffNo)))
				.andExpect(redirectedUrl("/login"));
	}

	// ---------- 임시 비밀번호 변경 강제 ----------

	private final String newPassword = "NewPass12!";

	private void makeTempPassword(String employeeNo) {
		jdbcTemplate.update("UPDATE employee SET temp_pw_yn = 'Y' WHERE employee_no = ?", employeeNo);
		entityManager.clear();	// 위 UPDATE를 엔티티가 다시 읽도록
	}

	private ResultActions changeEmployeePassword(MockHttpSession session, String current, String next, String confirm)
			throws Exception {
		return mockMvc.perform(post("/employee/password").session(session).with(csrf())
				.param("currentPassword", current)
				.param("newPassword", next)
				.param("newPasswordConfirm", confirm));
	}

	// 임시 비밀번호로 로그인하면 홈이 아니라 비밀번호 변경 화면으로
	@Test
	void tempPasswordLoginRedirectsToPasswordPage() throws Exception {
		makeTempPassword(staffNo);
		employeeLogin(staffNo, rawPassword)
				.andExpect(authenticated())
				.andExpect(redirectedUrl("/employee/password"));

		jdbcTemplate.update("UPDATE admin SET temp_pw_yn = 'Y' WHERE login_id = ?", adminId);
		entityManager.clear();
		adminLogin(rawPassword)
				.andExpect(authenticated())
				.andExpect(redirectedUrl("/admin/password"));
	}

	// 바꾸기 전에는 다른 화면·API를 쓸 수 없다. 변경 화면은 열린다
	@Test
	void tempPasswordBlocksOtherPages() throws Exception {
		makeTempPassword(staffNo);
		MockHttpSession session = loginEmployee(staffNo);

		mockMvc.perform(get("/employee").session(session)).andExpect(redirectedUrl("/employee/password"));
		mockMvc.perform(get("/employee/api/test").session(session)).andExpect(status().isForbidden());
		mockMvc.perform(get("/employee/password").session(session)).andExpect(status().isOk());
	}

	// 변경 성공 → 로그아웃되고 로그인 화면(?changed). 새 비밀번호로 로그인하면 홈으로
	@Test
	void changeTempPassword() throws Exception {
		makeTempPassword(staffNo);
		MockHttpSession session = loginEmployee(staffNo);

		changeEmployeePassword(session, rawPassword, newPassword, newPassword)
				.andExpect(redirectedUrl("/employee/login?changed"))
				.andExpect(unauthenticated());

		Employee employee = employeeRepository.findByEmployeeNo(staffNo).orElseThrow();
		assertThat(employee.isTempPassword()).isFalse();
		assertThat(passwordEncoder.matches(newPassword, employee.getPassword())).isTrue();

		employeeLogin(staffNo, rawPassword).andExpect(unauthenticated());
		employeeLogin(staffNo, newPassword)
				.andExpect(authenticated())
				.andExpect(redirectedUrl("/employee"));
	}

	// 현재 비밀번호 오류, 확인 불일치, 규칙 위반, 같은 비밀번호 → 화면에 오류, 비밀번호는 그대로
	@Test
	void changePasswordValidation() throws Exception {
		makeTempPassword(staffNo);
		MockHttpSession session = loginEmployee(staffNo);

		changeEmployeePassword(session, "wrong", newPassword, newPassword)
				.andExpect(status().isOk());
		changeEmployeePassword(session, rawPassword, newPassword, "Different1!")
				.andExpect(status().isOk());
		changeEmployeePassword(session, rawPassword, "short", "short")
				.andExpect(status().isOk());
		changeEmployeePassword(session, rawPassword, rawPassword, rawPassword)
				.andExpect(status().isOk());

		Employee employee = employeeRepository.findByEmployeeNo(staffNo).orElseThrow();
		assertThat(employee.isTempPassword()).isTrue();
		assertThat(passwordEncoder.matches(rawPassword, employee.getPassword())).isTrue();
	}

	// 관리자도 같은 흐름
	@Test
	void changeAdminTempPassword() throws Exception {
		jdbcTemplate.update("UPDATE admin SET temp_pw_yn = 'Y' WHERE login_id = ?", adminId);
		entityManager.clear();
		MockHttpSession session = loginAdmin();

		mockMvc.perform(get("/admin/system/employees").session(session)).andExpect(redirectedUrl("/admin/password"));
		mockMvc.perform(post("/admin/password").session(session).with(csrf())
						.param("currentPassword", rawPassword)
						.param("newPassword", newPassword)
						.param("newPasswordConfirm", newPassword))
				.andExpect(redirectedUrl("/admin/login?changed"));

		assertThat(adminRepository.findByLoginId(adminId).orElseThrow().isTempPassword()).isFalse();
		adminLogin(newPassword).andExpect(authenticated()).andExpect(redirectedUrl("/admin"));
	}

	// 임시 비밀번호가 아니어도 직접 들어가서 바꿀 수 있다
	@Test
	void normalAccountCanOpenPasswordPage() throws Exception {
		mockMvc.perform(get("/employee/password").session(loginEmployee(staffNo))).andExpect(status().isOk());
		mockMvc.perform(get("/admin/password").session(loginAdmin())).andExpect(status().isOk());
	}
}
